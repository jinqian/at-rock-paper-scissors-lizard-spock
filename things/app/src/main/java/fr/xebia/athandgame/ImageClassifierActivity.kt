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
import android.os.Environment
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import com.google.android.things.contrib.driver.button.Button
import com.google.android.things.contrib.driver.button.ButtonInputDriver
import com.google.android.things.pio.Gpio
import com.google.android.things.pio.PeripheralManager
import fr.xebia.athandgame.classifier.TensorFlowHelper
import fr.xebia.athandgame.driver.AdafruitPwm
import fr.xebia.athandgame.game.Gesture
import fr.xebia.athandgame.game.GestureGenerator
import fr.xebia.athandgame.game.Rules
import kotlinx.android.synthetic.main.activity_camera.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*


class ImageClassifierActivity : Activity() {

    private lateinit var ledGpio: Gpio
    private lateinit var buttonInputDriver: ButtonInputDriver
    private lateinit var countDownTimer: CountDownTimer
    private lateinit var pwm: AdafruitPwm
    private var isCountingDown = false
    private lateinit var gestureGenerator: GestureGenerator
    private var handUp = false
    private var currentGesture: Gesture? = null

    private var mProcessing: Boolean = false

    private lateinit var mTensorFlowLite: Interpreter
    private lateinit var mLabels: List<String>
    private lateinit var mCameraHandler: CameraHandler
    private lateinit var mImagePreprocessor: ImagePreprocessor

    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var tts: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        mediaPlayer = MediaPlayer.create(applicationContext, R.raw.game_start)
        tts = TextToSpeech(this, TextToSpeech.OnInitListener { tts.language = Locale.US })

        setContentView(R.layout.activity_camera)

        updateStatus(getString(R.string.initializing))

        initGameSet()
        initCamera()
        initButton()
        initServoMotors()
        initCountDown()

        initGameInstruction()

        updateStatus(getString(R.string.welcome_prompt))
    }

    private fun initGameSet() {
        try {
            intent?.extras?.let {
                if (it.getBoolean(EXTRA_FULL_MODE)) {
                    mTensorFlowLite = Interpreter(TensorFlowHelper.loadModelFile(this, FULL_MODEL_FILE))
                    mLabels = TensorFlowHelper.readLabels(this, FULL_LABELS_FILE)
                    gestureGenerator = GestureGenerator(FULL_GAME_MODE)
                } else {
                    mTensorFlowLite = Interpreter(TensorFlowHelper.loadModelFile(this, PARTIAL_MODEL_FILE))
                    mLabels = TensorFlowHelper.readLabels(this, PARTIAL_LABELS_FILE)
                    gestureGenerator = GestureGenerator(PARTIAL_GAME_MODE)
                }
            }

        } catch (e: IOException) {
            Log.w(TAG, "Unable to initialize TensorFlow Lite.", e)
        }
    }

    private fun initGameInstruction() {
        val welcomePrompt = getString(R.string.welcome_prompt)
        mediaPlayer.setOnCompletionListener {
            tts.speak(welcomePrompt, TextToSpeech.QUEUE_FLUSH, null, System.currentTimeMillis().toString())
        }
        mediaPlayer.start()
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
    private fun doRecognize(image: Bitmap) {
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

        val topResult = TensorFlowHelper.getTopLabel(confidencePerLabel, mLabels)
        currentGesture?.let { selfGesture ->
            val opponentGesture = Gesture.valueOf(topResult.first.toUpperCase())
            val gameResult = Rules.getGameResult(selfGesture, opponentGesture)
            val reason = getString(gameResult.reason)

            gameResult.doIWin?.let {
                if (it) {
                    val fullResult = getString(R.string.i_won_you_lost, reason)
                    resultText.text = fullResult
                    tts.speak(fullResult, TextToSpeech.QUEUE_FLUSH, null, System.currentTimeMillis().toString())
                } else {
                    val fullResult = getString(R.string.i_lost_you_won, reason)
                    resultText.text = fullResult
                    tts.speak(fullResult, TextToSpeech.QUEUE_FLUSH, null, System.currentTimeMillis().toString())
                }
            } ?: run {
                // it's a tie!
                resultText.text = reason
                tts.speak(reason, TextToSpeech.QUEUE_FLUSH, null, System.currentTimeMillis().toString())
            }
        }

        mProcessing = false
        saveTempBitmap(image)
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
            val bitmap = mImagePreprocessor.preprocessImage(imageReader.acquireNextImage())
            previewImage.setImageBitmap(bitmap)
            doRecognize(bitmap)
        }
    }

    private fun saveTempBitmap(bitmap: Bitmap) {
        if (isExternalStorageWritable()) {
            Log.d(TAG, "Save bitmap")
            GlobalScope.launch {
                saveImage(bitmap)
            }
        } else {
            Log.d(TAG, "External storage not writable")
        }
    }

    private fun saveImage(finalBitmap: Bitmap) {
        val root = Environment.getExternalStorageDirectory().toString()
        val myDir = File("$root/chifumi")
        myDir.mkdirs()

        val timeStamp = System.currentTimeMillis().toString()
        val fileName = "image_$timeStamp.jpg"

        val file = File(myDir, fileName)
        if (file.exists()) file.delete()
        try {
            Log.d(TAG, "Save to $myDir/$fileName")
            val out = FileOutputStream(file)
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            out.flush()
            out.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isExternalStorageWritable(): Boolean {
        val state = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED == state
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
                val currentSecond = (millisUntilFinished / 1000 + 1).toString()
                resultText.text = currentSecond
                tts.speak(currentSecond, TextToSpeech.QUEUE_FLUSH, null, System.currentTimeMillis().toString())
            }

            override fun onFinish() {
                isCountingDown = false
                val play = getString(R.string.play)
                resultText.text = play
                tts.speak(play, TextToSpeech.QUEUE_FLUSH, null, System.currentTimeMillis().toString())

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
        Log.d(TAG, "Fire ${gesture.name}")
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

    private fun updateStatus(text: String) {
        resultText.text = text
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

        const val EXTRA_FULL_MODE = "EXTRA_FULL_MODE"

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
        private const val FULL_GAME_MODE = 5
        private const val PARTIAL_GAME_MODE = 3

        private const val FULL_LABELS_FILE = "handgame_5_labels.txt"
        private const val FULL_MODEL_FILE = "handgame_5_graph.lite"

        private const val PARTIAL_LABELS_FILE = "handgame_3_labels.txt"
        private const val PARTIAL_MODEL_FILE = "handgame_3_graph.lite"

        // The PWM/Servo driver is hooked on I2C2
        private const val I2C_DEVICE_NAME: String = "I2C2"

        private const val I2C_SERVO_ADDRESS: Int = 0x40

        private const val PWM_FREQUENCE = 60
        private const val SERVO_DOWN = 100
        private const val SERVO_HAND = 350
    }
}
