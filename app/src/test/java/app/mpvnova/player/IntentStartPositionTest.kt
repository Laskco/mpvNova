package app.mpvnova.player

import org.junit.Assert.assertEquals
import org.junit.Test

class IntentStartPositionTest {
    @Test
    fun ordinaryLaunchWithoutResumeTimestampLeavesStartUnspecified() {
        assertEquals(
            ExternalStartPositionRequest(positionMs = 0L, isExplicit = false),
            externalStartPositionRequest(
                ExternalStartPositionExtras()
            )
        )
    }

    @Test
    fun explicitZeroPositionRequestsBeginning() {
        assertEquals(
            ExternalStartPositionRequest(positionMs = 0L, isExplicit = true),
            externalStartPositionRequest(
                ExternalStartPositionExtras(positionMs = 0L)
            )
        )
    }

    @Test
    fun fromStartOverridesPositiveResumeExtras() {
        assertEquals(
            ExternalStartPositionRequest(positionMs = 0L, isExplicit = true),
            externalStartPositionRequest(
                ExternalStartPositionExtras(
                    forceFromStart = true,
                    positionMs = 180_000L,
                )
            )
        )
    }

    @Test
    fun explicitBeginningDoesNotUseLocalResumePosition() {
        assertEquals(
            0L,
            effectiveIntentStartPosition(
                request = ExternalStartPositionRequest(positionMs = 0L, isExplicit = true),
                savedPositionMs = 420_000L,
                durationMs = 1_440_000L,
            )
        )
    }

    @Test
    fun launchWithoutProgressOwnerStillUsesLocalResumePosition() {
        assertEquals(
            420_000L,
            effectiveIntentStartPosition(
                request = ExternalStartPositionRequest(positionMs = 0L, isExplicit = false),
                savedPositionMs = 420_000L,
                durationMs = 1_440_000L,
            )
        )
    }

    @Test
    fun explicitResumePositionWinsOverLocalResumePosition() {
        assertEquals(
            180_000L,
            effectiveIntentStartPosition(
                request = ExternalStartPositionRequest(positionMs = 180_000L, isExplicit = true),
                savedPositionMs = 420_000L,
                durationMs = 1_440_000L,
            )
        )
    }

    @Test
    fun explicitNearEndPositionRestartsFromBeginning() {
        assertEquals(
            0L,
            effectiveIntentStartPosition(
                request = ExternalStartPositionRequest(positionMs = 1_420_000L, isExplicit = true),
                savedPositionMs = 420_000L,
                durationMs = 1_440_000L,
            )
        )
    }
}
