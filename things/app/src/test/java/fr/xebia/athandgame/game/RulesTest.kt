package fr.xebia.athandgame.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class RulesTest {

    @DisplayName("Gesture a should win over Gesture b")
    @ParameterizedTest(name = "{index} => a={0}, b={1}")
    @MethodSource("gestures")
    fun gestureAWinsGestureB(a: Gesture, b: Gesture) {
        val gameResult = Rules.getGameResult(a, b)
        assertEquals(gameResult.doIWin, true)
    }

    companion object {
        @JvmStatic
        fun gestures() = listOf(
            Arguments.of(Gesture.ROCK, Gesture.SCISSORS),
            Arguments.of(Gesture.ROCK, Gesture.LIZARD),
            Arguments.of(Gesture.PAPER, Gesture.ROCK),
            Arguments.of(Gesture.PAPER, Gesture.SPOCK),
            Arguments.of(Gesture.SCISSORS, Gesture.PAPER),
            Arguments.of(Gesture.SCISSORS, Gesture.LIZARD),
            Arguments.of(Gesture.SPOCK, Gesture.SCISSORS),
            Arguments.of(Gesture.SPOCK, Gesture.ROCK),
            Arguments.of(Gesture.LIZARD, Gesture.SPOCK),
            Arguments.of(Gesture.LIZARD, Gesture.PAPER)
        )
    }
}