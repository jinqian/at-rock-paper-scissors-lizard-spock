package fr.xebia.athandgame

import android.app.Activity
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.KeyEvent
import com.google.android.things.contrib.driver.button.Button
import com.google.android.things.contrib.driver.button.ButtonInputDriver
import com.google.android.things.pio.Gpio
import com.google.android.things.pio.PeripheralManager
import fr.xebia.athandgame.driver.AdafruitPwm
import fr.xebia.athandgame.game.Gesture
import fr.xebia.athandgame.game.GestureGenerator
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber

class MainActivity : Activity() {

    private lateinit var ledGpio: Gpio
    private lateinit var buttonInputDriver: ButtonInputDriver
    private lateinit var countDownTimer: CountDownTimer

    private lateinit var pwm: AdafruitPwm

    private var isCountingDown = false
    private val gestureGenerator = GestureGenerator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(TAG, "Starting MainActivity")

        // I2C setup
        Log.d(TAG, "Setup I2C devices")
        val peripheralManager = PeripheralManager.getInstance()
        pwm = AdafruitPwm(I2C_DEVICE_NAME, I2C_SERVO_ADDRESS, true)
        pwm.setPwmFreq(60)

        Log.d(TAG, "Configuring GPIO pins")
        ledGpio = peripheralManager.openGpio(BoardDefaults.gpioForLED)
        ledGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)

        Timber.d(TAG, "Registering button driver")
        buttonInputDriver = ButtonInputDriver(
            BoardDefaults.gpioForButton,
            Button.LogicState.PRESSED_WHEN_LOW,
            KeyEvent.KEYCODE_SPACE
        )
        buttonInputDriver.register()
        countDownTimer = object : CountDownTimer(2000, 1000) {

            override fun onTick(millisUntilFinished: Long) {
                isCountingDown = true
                countDownPrompt.text = (millisUntilFinished / 1000 + 1).toString()
            }

            override fun onFinish() {
                isCountingDown = false
                countDownPrompt.text = "Play!"

                // TODO game start sound
                val mediaPlayer = MediaPlayer.create(applicationContext, R.raw.test_sound)
                mediaPlayer.start()

                // TODO test servo motors should not block current thread
                moveServo(gestureGenerator.fire())
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            countDown()
            setLedValue(true)
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    private fun moveServo(gesture: Gesture) {
        pwm.setPwm(gesture.driverPin, 0, SERVO_MIN)
        Thread.sleep(200)
        pwm.setPwm(gesture.driverPin, SERVO_MIN, 0)
    }

    private fun countDown() {
        if (!isCountingDown) {
            countDownTimer.start()
        }
    }

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
        Timber.d(TAG, "Setting LED value to $value")
        ledGpio.value = value
    }

    override fun onStop() {
        buttonInputDriver.unregister()
        buttonInputDriver.close()

        ledGpio.close()
        pwm.close()

        super.onStop()
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName

        // The PWM/Servo driver is hooked on I2C2
        private const val I2C_DEVICE_NAME: String = "I2C2"

        private const val I2C_SERVO_ADDRESS: Int = 0x40

        private const val SERVO_MIN = 150
        private const val SERVO_MAX = 600
    }
}
