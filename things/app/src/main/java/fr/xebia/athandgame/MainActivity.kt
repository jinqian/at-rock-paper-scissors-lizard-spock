package fr.xebia.athandgame

import android.app.Activity
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.KeyEvent
import com.google.android.things.contrib.driver.button.Button
import com.google.android.things.contrib.driver.button.ButtonInputDriver
import com.google.android.things.pio.Gpio
import com.google.android.things.pio.PeripheralManager
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : Activity() {

    private lateinit var ledGpio: Gpio
    private lateinit var buttonInputDriver: ButtonInputDriver
    private lateinit var countDownTimer: CountDownTimer
    private var isCountingDown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.i(TAG, "Starting ButtonActivity")

        val pioService = PeripheralManager.getInstance()

        Log.i(TAG, "Configuring GPIO pins")
        ledGpio = pioService.openGpio(BoardDefaults.gpioForLED)
        ledGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)

        Log.i(TAG, "Registering button driver")
        // Initialize and register the InputDriver that will emit SPACE key events
        // on GPIO state changes.
        buttonInputDriver = ButtonInputDriver(
                BoardDefaults.gpioForButton,
                Button.LogicState.PRESSED_WHEN_LOW,
                KeyEvent.KEYCODE_SPACE
        )
        buttonInputDriver.register()
        countDownTimer = object : CountDownTimer(5000, 1000) {

            override fun onTick(millisUntilFinished: Long) {
                isCountingDown = true
                countDownPrompt.text = (millisUntilFinished / 1000 + 1).toString()
            }

            override fun onFinish() {
                isCountingDown = false
                countDownPrompt.text = "Play!"
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
        Log.d(TAG, "Setting LED value to $value")
        ledGpio.value = value
    }

    override fun onStop() {
        buttonInputDriver.unregister()
        buttonInputDriver.close()

        ledGpio.close()

        super.onStop()
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }
}
