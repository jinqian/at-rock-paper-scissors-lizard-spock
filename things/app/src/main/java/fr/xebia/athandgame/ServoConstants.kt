package fr.xebia.athandgame

// The PWM/Servo driver is hooked on I2C2

const val I2C_DEVICE_NAME: String = "I2C2"
const val I2C_SERVO_ADDRESS: Int = 0x40

const val PWM_FREQUENCE = 60
const val SERVO_DOWN = 100
const val SERVO_HAND = 350