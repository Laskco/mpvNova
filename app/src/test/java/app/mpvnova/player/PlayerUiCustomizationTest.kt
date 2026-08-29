package app.mpvnova.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerUiCustomizationTest {
    @Test
    fun protectedControlsCannotBeHidden() {
        val style = PlayerUiCustomization(
            hiddenControls = PlayerBarControl.entries.toSet(),
        ).normalized()

        assertTrue(style.isControlVisible(PlayerBarControl.PLAY))
        assertTrue(style.isControlVisible(PlayerBarControl.CHAPTERS))
        assertTrue(style.isControlVisible(PlayerBarControl.AUDIO))
        assertTrue(style.isControlVisible(PlayerBarControl.SUBTITLES))
        assertFalse(style.isControlVisible(PlayerBarControl.STATS))
    }

    @Test
    fun malformedOrderIsCompletedWithoutDuplicates() {
        val style = PlayerUiCustomization(
            controlOrder = listOf(
                PlayerBarControl.SPEED,
                PlayerBarControl.SPEED,
                PlayerBarControl.PLAY,
            ),
        ).normalized()

        assertEquals(PlayerBarControl.entries.size, style.controlOrder.size)
        assertEquals(PlayerBarControl.SPEED, style.controlOrder.first())
        assertEquals(PlayerBarControl.PLAY, style.controlOrder[1])
    }

    @Test
    fun invalidValuesAreClampedToEditorRanges() {
        val style = PlayerUiCustomization(
            widthPercent = 120,
            timeTextSizeSp = 4,
            controlSpacingDp = 30,
            panelOutlineWidthDp = 20,
            horizontalPaddingDp = 80,
            panelElevationDp = -5,
        ).normalized()

        assertEquals(MAX_WIDTH_PERCENT, style.widthPercent)
        assertEquals(MIN_TIME_TEXT_SIZE_SP, style.timeTextSizeSp)
        assertEquals(MAX_CONTROL_SPACING_DP, style.controlSpacingDp)
        assertEquals(MAX_OUTLINE_WIDTH_DP, style.panelOutlineWidthDp)
        assertEquals(MAX_PANEL_PADDING_DP, style.horizontalPaddingDp)
        assertEquals(MIN_ELEVATION_DP, style.panelElevationDp)
    }

    @Test
    fun steppedValuesSnapTowardTheRequestedGridPoint() {
        assertEquals(70, stepPlayerUiValue(68, 1, 5))
        assertEquals(65, stepPlayerUiValue(68, -1, 5))
        assertEquals(75, stepPlayerUiValue(70, 1, 5))
        assertEquals(65, stepPlayerUiValue(70, -1, 5))
    }

    @Test
    fun presetsRoundTripToTheirLabels() {
        PlayerUiPreset.entries.filter { it != PlayerUiPreset.CUSTOM }.forEach { preset ->
            assertEquals(preset, playerUiPresetFor(playerUiPresetStyle(preset)))
        }
        assertTrue(playerUiPresetStyle(PlayerUiPreset.FLOATING).isControlVisible(PlayerBarControl.DECODER))
        assertEquals(
            PlayerUiPreset.CUSTOM,
            playerUiPresetFor(PlayerUiCustomization.DEFAULT.copy(widthPercent = 91)),
        )
    }
}
