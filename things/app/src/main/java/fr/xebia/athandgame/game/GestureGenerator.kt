package fr.xebia.athandgame.game

class GestureGenerator(private val totalGesture: Int) {

    fun fire(): Gesture {
        return Gesture.values()[(0 until totalGesture).random()]
    }
}