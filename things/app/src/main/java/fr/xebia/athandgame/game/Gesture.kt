package fr.xebia.athandgame.game

enum class Gesture(val id: Int, val driverPin: Int) {
    ROCK(0, 0),
    PAPER(1, 2),
    SCISSORS(2, 4),
    SPOCK(3, 6),
    LIZARD(4, 7)
}

