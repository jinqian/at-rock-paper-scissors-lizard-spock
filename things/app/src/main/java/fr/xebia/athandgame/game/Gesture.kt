package fr.xebia.athandgame.game

enum class Gesture(val id: Int, val driverPin: Int) {
    ROCK(0, 1),
    PAPER(1, 2),
    SCISSORS(2, 3),
    SPOCK(3, 5),
    LIZARD(4, 7)
}

