/*
 * Copyright 2017 The Android Things Samples Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.xebia.athandgame

import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import fr.xebia.athandgame.classifier.Recognition
import fr.xebia.athandgame.classifier.TensorFlowHelper
import org.tensorflow.lite.Interpreter

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ImageClassifierActivity : Activity() {

    //    private ButtonInputDriver mButtonDriver;
    private var mProcessing: Boolean = false

    private var mImage: ImageView? = null
    private var mResultText: TextView? = null

    private var mTensorFlowLite: Interpreter? = null
    private var mLabels: List<String>? = null
    private var mCameraHandler: CameraHandler? = null
    private var mImagePreprocessor: ImagePreprocessor? = null

    /**
     * Initialize the classifier that will be used to process images.
     */
    private fun initClassifier() {
        try {
            mTensorFlowLite = Interpreter(TensorFlowHelper.loadModelFile(this, MODEL_FILE))
            mLabels = TensorFlowHelper.readLabels(this, LABELS_FILE)
        } catch (e: IOException) {
            Log.w(TAG, "Unable to initialize TensorFlow Lite.", e)
        }

    }

    /**
     * Clean up the resources used by the classifier.
     */
    private fun destroyClassifier() {
        mTensorFlowLite!!.close()
    }

    /**
     * Process an image and identify what is in it. When done, the method
     * [.onPhotoRecognitionReady] must be called with the results of
     * the image recognition process.
     *
     * @param image Bitmap containing the image to be classified. The image can be
     * of any size, but preprocessing might occur to resize it to the
     * format expected by the classification process, which can be time
     * and power consuming.
     */
    private fun doRecognize(image: Bitmap?) {
        // Allocate space for the inference results
        val confidencePerLabel = Array(1) { ByteArray(mLabels!!.size) }
        // Allocate buffer for image pixels.
        val intValues = IntArray(DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y)
        val imgData = ByteBuffer.allocateDirect(
            4 * DIM_BATCH_SIZE * DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y * DIM_PIXEL_SIZE
        )
        imgData.order(ByteOrder.nativeOrder())

        // Read image data into buffer formatted for the TensorFlow model
        TensorFlowHelper.convertBitmapToByteBuffer(image, intValues, imgData)

        // Run inference on the network with the image bytes in imgData as input,
        // storing results on the confidencePerLabel array.
        mTensorFlowLite!!.run(imgData, confidencePerLabel)

        // Get the results with the highest confidence and map them to their labels
        val results = TensorFlowHelper.getBestResults(confidencePerLabel, mLabels!!)
        // Report the results with the highest confidence
        onPhotoRecognitionReady(results)
    }

    /**
     * Initialize the camera that will be used to capture images.
     */
    private fun initCamera() {
        mImagePreprocessor = ImagePreprocessor(
            PREVIEW_IMAGE_WIDTH, PREVIEW_IMAGE_HEIGHT,
            DIM_IMG_SIZE_X, DIM_IMG_SIZE_Y
        )
        mCameraHandler = CameraHandler.getInstance()
        mCameraHandler!!.initializeCamera(
            this,
            PREVIEW_IMAGE_WIDTH, PREVIEW_IMAGE_HEIGHT, null
        ) { imageReader ->
            val bitmap = mImagePreprocessor!!.preprocessImage(imageReader.acquireNextImage())
            onPhotoReady(bitmap)
        }
    }

    /**
     * Clean up resources used by the camera.
     */
    private fun closeCamera() {
        mCameraHandler!!.shutDown()
    }

    /**
     * Load the image that will be used in the classification process.
     * When done, the method [.onPhotoReady] must be called with the image.
     */
    private fun loadPhoto() {
        mCameraHandler!!.takePicture()
    }


    // --------------------------------------------------------------------------------------
    // NOTE: The normal codelab flow won't require you to change anything below this line,
    // although you are encouraged to read and understand it.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_camera)
        mImage = findViewById(R.id.imageView)
        mResultText = findViewById(R.id.resultText)

        updateStatus(getString(R.string.initializing))
        initCamera()
        initClassifier()
        initCountDown()
        updateStatus(getString(R.string.help_message))
    }

    /**
     * Register a GPIO button that, when clicked, will generate the [KeyEvent.KEYCODE_ENTER]
     * key, to be handled by [.onKeyUp] just like any regular keyboard
     * event.
     *
     *
     * If there's no button connected to the board, the doRecognize can still be triggered by
     * sending key events using a USB keyboard or `adb shell input keyevent 66`.
     */
    private fun initCountDown() {
        //        try {
        //            mButtonDriver = RainbowHat.createButtonCInputDriver(KeyEvent.KEYCODE_ENTER);
        //            mButtonDriver.register();
        //        } catch (IOException e) {
        //            Log.w(TAG, "Cannot find button. Ignoring push button. Use a keyboard instead.", e);
        //        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            if (mProcessing) {
                updateStatus("Still processing, please wait")
                return true
            }
            updateStatus("Running photo recognition")
            mProcessing = true
            loadPhoto()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Image capture process complete
     */
    private fun onPhotoReady(bitmap: Bitmap?) {
        mImage!!.setImageBitmap(bitmap)
        doRecognize(bitmap)
    }

    /**
     * Image classification process complete
     */
    private fun onPhotoRecognitionReady(results: Collection<Recognition>) {
        updateStatus(formatResults(results))
        mProcessing = false
    }

    /**
     * Format results list for display
     */
    private fun formatResults(results: Collection<Recognition>?): String {
        if (results == null || results.isEmpty()) {
            return getString(R.string.empty_result)
        } else {
            val sb = StringBuilder()
            val it = results.iterator()
            var counter = 0
            while (it.hasNext()) {
                val r = it.next()
                sb.append(r.title)
                counter++
                if (counter < results.size - 1) {
                    sb.append(", ")
                } else if (counter == results.size - 1) {
                    sb.append(" or ")
                }
            }

            return sb.toString()
        }
    }

    /**
     * Report updates to the display and log output
     */
    private fun updateStatus(status: String) {
        Log.d(TAG, status)
        mResultText!!.text = status
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            destroyClassifier()
        } catch (t: Throwable) {
            // close quietly
        }

        try {
            closeCamera()
        } catch (t: Throwable) {
            // close quietly
        }
    }

    companion object {
        private const val TAG = "ImageClassifierActivity"

        /**
         * Camera image capture size
         */
        private const val PREVIEW_IMAGE_WIDTH = 640
        private const val PREVIEW_IMAGE_HEIGHT = 480
        /**
         * Image dimensions required by TF model
         */
        private const val DIM_IMG_SIZE_X = 224
        private const val DIM_IMG_SIZE_Y = 224
        /**
         * Dimensions of model inputs.
         */
        private const val DIM_BATCH_SIZE = 1
        private const val DIM_PIXEL_SIZE = 3
        /**
         * TF model asset files
         */
        private const val LABELS_FILE = "handgame_labels.txt"
        private const val MODEL_FILE = "handgame_graph.tflite"
    }
}
