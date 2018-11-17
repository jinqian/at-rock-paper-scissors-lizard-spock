package fr.xebia.athandgame.game

import android.support.annotation.StringRes
import fr.xebia.athandgame.R

class Rules {

    companion object {
        private val tieResult = GameResult(null, R.string.tie_result)

        // TODO this could use some refacto & unit tests, but later later...
        fun getGameResult(selfGesture: Gesture, opponentGesture: Gesture): GameResult {
            return when (selfGesture) {
                Gesture.ROCK -> {
                    when (opponentGesture) {
                        Gesture.SCISSORS -> GameResult(true, R.string.rock_crushed_scissors)
                        Gesture.LIZARD -> GameResult(true, R.string.rock_crushes_lizard)
                        Gesture.PAPER -> GameResult(false, R.string.paper_covers_rock)
                        Gesture.SPOCK -> GameResult(false, R.string.spock_vaporizes_rock)
                        else -> tieResult
                    }
                }
                Gesture.PAPER -> {
                    when (opponentGesture) {
                        Gesture.ROCK -> GameResult(true, R.string.paper_covers_rock)
                        Gesture.PAPER -> tieResult
                        Gesture.SCISSORS -> GameResult(false, R.string.scissors_cuts_paper)
                        Gesture.SPOCK -> GameResult(true, R.string.paper_disproves_spock)
                        Gesture.LIZARD -> GameResult(false, R.string.lizard_eats_paper)
                    }
                }
                Gesture.SCISSORS -> {
                    when (opponentGesture) {
                        Gesture.ROCK -> GameResult(false, R.string.rock_crushed_scissors)
                        Gesture.PAPER -> GameResult(true, R.string.scissors_cuts_paper)
                        Gesture.SCISSORS -> tieResult
                        Gesture.SPOCK -> GameResult(false, R.string.spock_smashed_scissors)
                        Gesture.LIZARD -> GameResult(true, R.string.scissors_decapitates_lizard)
                    }
                }
                Gesture.SPOCK -> {
                    when (opponentGesture) {
                        Gesture.ROCK -> GameResult(true, R.string.spock_vaporizes_rock)
                        Gesture.PAPER -> GameResult(false, R.string.paper_disproves_spock)
                        Gesture.SCISSORS -> GameResult(true, R.string.spock_smashed_scissors)
                        Gesture.SPOCK -> tieResult
                        Gesture.LIZARD -> GameResult(false, R.string.lizard_poisons_spock)
                    }
                }
                Gesture.LIZARD -> {
                    when (opponentGesture) {
                        Gesture.ROCK -> GameResult(false, R.string.rock_crushes_lizard)
                        Gesture.PAPER -> GameResult(true, R.string.lizard_eats_paper)
                        Gesture.SCISSORS -> GameResult(false, R.string.scissors_decapitates_lizard)
                        Gesture.SPOCK -> GameResult(true, R.string.lizard_poisons_spock)
                        Gesture.LIZARD -> tieResult
                    }
                }
            }
        }
    }
}

data class GameResult(val doIWin: Boolean?, @StringRes val reason: Int)