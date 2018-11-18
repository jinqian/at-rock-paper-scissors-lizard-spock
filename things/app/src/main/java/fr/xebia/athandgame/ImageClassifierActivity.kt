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
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import com.google.android.things.contrib.driver.button.Button
import com.google.android.things.contrib.driver.button.ButtonInputDriver
import com.google.android.things.pio.Gpio
import com.google.android.things.pio.PeripheralManager
import fr.xebia.athandgame.classifier.Recognition
import fr.xebia.athandgame.classifier.TensorFlowHelper
import fr.xebia.athandgame.driver.AdafruitPwm
import fr.xebia.athandgame.game.Gesture
import fr.xebia.athandgame.game.GestureGenerator
import kotlinx.android.synthetic.main.activity_camera.*
import org.tensorflow.lite.Interpreter
import timber.log.Timber
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ImageClassifierActivity : Activity() {

    private lateinit var ledGpio: Gpio
    private lateinit var buttonInputDriver: ButtonInputDriver
    private lateinit var countDownTimer: CountDownTimer
    private lateinit var pwm: AdafruitPwm
    private var isCountingDown = false
    private val gestureGenerator = GestureGenerator()
    private var handUp = false
    private var currentGesture: Gesture? = null

    private var mProcessing: Boolean = false

    private lateinit var mTensorFlowLite: Interpreter
    private lateinit var mLabels: List<String>
    private lateinit var mCameraHandler: CameraHandler
    private lateinit var mImagePreprocessor: ImagePreprocessor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_camera)

        updateStatus(getString(R.string.initializing))

        initButton()
        initServoMotors()
        initCamera()
        initClassifier()
        initCountDown()

        updateStatus(getString(R.string.help_message))
    }

    private fun initButton() {
        Log.d(TAG, "Configuring GPIO pins")
        val peripheralManager = PeripheralManager.getInstance()
        ledGpio = peripheralManager.openGpio(BoardDefaults.gpioForLED)
        ledGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)

        Log.d(TAG, "Registering button driver")
        buttonInputDriver = ButtonInputDriver(
            BoardDefaults.gpioForButton,
            Button.LogicState.PRESSED_WHEN_LOW,
            KeyEvent.KEYCODE_SPACE
        )
        buttonInputDriver.register()
    }

    override fun onStop() {
        buttonInputDriver.unregister()
        buttonInputDriver.close()

        ledGpio.close()
        pwm.close()

        super.onStop()
    }

    private fun initServoMotors() {
        // I2C setup
        Log.d(TAG, "Setup I2C devices")
        pwm = AdafruitPwm(I2C_DEVICE_NAME, I2C_SERVO_ADDRESS, true)
        pwm.setPwmFreq(PWM_FREQUENCE)

        // calibrate servos to down angel
        pwm.setPwm(Gesture.ROCK.driverPin, 0, SERVO_DOWN)
        pwm.setPwm(Gesture.PAPER.driverPin, 0, SERVO_DOWN)
        pwm.setPwm(Gesture.SCISSORS.driverPin, 0, SERVO_DOWN)
        pwm.setPwm(Gesture.SPOCK.driverPin, 0, SERVO_DOWN)
        pwm.setPwm(Gesture.LIZARD.driverPin, 0, SERVO_DOWN)
    }

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
        mTensorFlowLite.close()
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
        val confidencePerLabel = Array(1) { FloatArray(mLabels.size) }
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
        mTensorFlowLite.run(imgData, confidencePerLabel)

        // Get the results with the highest confidence and map them to their labels
        val results = TensorFlowHelper.getBestResults(confidencePerLabel, mLabels)
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
        mCameraHandler.initializeCamera(
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
        mCameraHandler.shutDown()
    }

    /**
     * Load the image that will be used in the classification process.
     * When done, the method [.onPhotoReady] must be called with the image.
     */
    private fun loadPhoto() {
        mCameraHandler.takePicture()
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
        countDownTimer = object : CountDownTimer(3000, 1000) {

            override fun onTick(millisUntilFinished: Long) {
                isCountingDown = true
                resultText.text = (millisUntilFinished / 1000 + 1).toString()
            }

            override fun onFinish() {
                isCountingDown = false
                resultText.text = "Play!"

                // TODO game start sound
                val mediaPlayer = MediaPlayer.create(applicationContext, R.raw.test_sound)
                mediaPlayer.start()

                // TODO test servo motors should not block current thread
                if (mProcessing) {
                    updateStatus("Still processing, please wait")
                } else {
                    fireGesture(gestureGenerator.fire())
                    updateStatus("Running photo recognition on your gesture")
                    mProcessing = true
                    loadPhoto()
                }
            }
        }
    }

    // hardware

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            if (handUp) {
                reset()
            } else {
                countDown()
            }
            setLedValue(true)
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    private fun reset() {
        handUp = false
        currentGesture?.let {
            pwm.setPwm(it.driverPin, 0, SERVO_DOWN)
        }
        finish()
    }

    private fun fireGesture(gesture: Gesture) {
        Timber.d("Fire ${gesture.name}")
        handUp = true
        currentGesture = gesture
        pwm.setPwm(gesture.driverPin, 0, SERVO_HAND)
    }

    private fun countDown() {
        if (!isCountingDown) {
            countDownTimer.start()
        }
    }

    // hardware

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            // Turn off the LED
            setLedValue(false)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Update the value of the LED output.
     */
    private fun setLedValue(value: Boolean) {
        Log.d(TAG, "Setting LED value to $value")
        ledGpio.value = value
    }

    /**
     * Image capture process complete
     */
    private fun onPhotoReady(bitmap: Bitmap?) {
        imageView.setImageBitmap(bitmap)
        doRecognize(bitmap)
    }

    /**
     * Image classification process complete
     */
    private fun onPhotoRecognitionReady(results: Collection<Recognition>) {
        updateStatus(formatResults(results))
        mProcessing = false
    }

    private fun updateStatus(text: String) {
        resultText.text = text
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
        private const val MODEL_FILE = "handgame_graph.lite"

        // The PWM/Servo driver is hooked on I2C2
        private const val I2C_DEVICE_NAME: String = "I2C2"

        private const val I2C_SERVO_ADDRESS: Int = 0x40

        private const val PWM_FREQUENCE = 60
        private const val SERVO_DOWN = 100
        private const val SERVO_HAND = 350
    }
}