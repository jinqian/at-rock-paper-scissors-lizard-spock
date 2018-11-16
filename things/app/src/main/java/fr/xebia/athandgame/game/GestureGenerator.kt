package fr.xebia.athandgame.game

class GestureGenerator {

    fun fire(): Gesture {
        return Gesture.values()[(0 until Gesture.values().size).random()]
    }
}