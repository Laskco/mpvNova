package app.mpvnova.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerBackPressTest {
    @Test
    fun optInHideThenDoubleBackUsesThreeDistinctPresses() {
        val first = decidePlayerBackPress(
            backHidesControlsFirst = true,
            controlsVisible = true,
            exitWithDoubleBack = true,
            playlistConfirmsExit = false,
            lastBackPressMs = 0L,
            nowMs = 10_000L,
        )
        assertEquals(PlayerBackAction.HIDE_CONTROLS, first.action)
        assertEquals(0L, first.nextLastBackPressMs)

        val second = decidePlayerBackPress(
            backHidesControlsFirst = true,
            controlsVisible = false,
            exitWithDoubleBack = true,
            playlistConfirmsExit = false,
            lastBackPressMs = first.nextLastBackPressMs,
            nowMs = 10_100L,
        )
        assertEquals(PlayerBackAction.SHOW_EXIT_HINT, second.action)

        val third = decidePlayerBackPress(
            backHidesControlsFirst = true,
            controlsVisible = false,
            exitWithDoubleBack = true,
            playlistConfirmsExit = false,
            lastBackPressMs = second.nextLastBackPressMs,
            nowMs = 10_500L,
        )
        assertEquals(PlayerBackAction.CONTINUE_EXIT, third.action)
    }

    @Test
    fun disabledHideSettingDoesNotConsumeBackForVisibleControls() {
        val decision = decidePlayerBackPress(
            backHidesControlsFirst = false,
            controlsVisible = true,
            exitWithDoubleBack = false,
            playlistConfirmsExit = false,
            lastBackPressMs = 0L,
            nowMs = 10_000L,
        )

        assertEquals(PlayerBackAction.CONTINUE_EXIT, decision.action)
    }

    @Test
    fun playlistConfirmationBypassesDoubleBackHint() {
        val decision = decidePlayerBackPress(
            backHidesControlsFirst = false,
            controlsVisible = false,
            exitWithDoubleBack = true,
            playlistConfirmsExit = true,
            lastBackPressMs = 0L,
            nowMs = 10_000L,
        )

        assertEquals(PlayerBackAction.CONTINUE_EXIT, decision.action)
    }

    @Test
    fun firstDoubleBackPressAlwaysShowsHint() {
        val decision = decidePlayerBackPress(
            backHidesControlsFirst = false,
            controlsVisible = false,
            exitWithDoubleBack = true,
            playlistConfirmsExit = false,
            lastBackPressMs = 0L,
            nowMs = 500L,
        )

        assertEquals(PlayerBackAction.SHOW_EXIT_HINT, decision.action)
    }
}
