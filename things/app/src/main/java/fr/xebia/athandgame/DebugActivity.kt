package fr.xebia.athandgame

import android.app.Activity
import android.os.Bundle
import android.util.Log
import fr.xebia.athandgame.driver.AdafruitPwm
import fr.xebia.athandgame.game.Gesture
import kotlinx.android.synthetic.main.activity_debug.*

class DebugActivity : Activity() {

    private lateinit var pwm: AdafruitPwm

    private var isRockUp = false
    private var isPaperUp = false
    private var isScissorsUp = false
    private var isLizardUp = false
    private var isSpockUp = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug)

        initServoMotors()

        testRock.setOnClickListener {
            isRockUp = !isRockUp
            fireGesture(isRockUp, Gesture.ROCK)
        }

        testPaper.setOnClickListener {
            isPaperUp = !isPaperUp
            fireGesture(isPaperUp, Gesture.PAPER)
        }

        testScissors.setOnClickListener {
            isScissorsUp = !isScissorsUp
            fireGesture(isScissorsUp, Gesture.SCISSORS)
        }

        testSpock.setOnClickListener {
            isSpockUp = !isSpockUp
            fireGesture(isSpockUp, Gesture.SPOCK)
        }

        testLizard.setOnClickListener {
            isLizardUp = !isLizardUp
            fireGesture(isLizardUp, Gesture.LIZARD)
        }

        exitDebug.setOnClickListener {
            finish()
        }
    }

    private fun fireGesture(isUp: Boolean, gesture: Gesture) {
        if (isUp) {
            pwm.setPwm(gesture.driverPin, 0, SERVO_DOWN)
        } else {
            pwm.setPwm(gesture.driverPin, 0, SERVO_HAND)
        }
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

    companion object {
        private const val TAG = "DebugActivity"
    }

}
