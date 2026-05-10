package app.mpvnova.player.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdateVersioningTest {
    @Test
    fun chooseBestApkAssetNameForFireTvStickAbi() {
        val selected = chooseBestApkAssetName(
            assetNames = listOf(
                "mpvNova-2026-05-10-arm64-v8a.apk",
                "mpvNova-2026-05-10-armeabi-v7a.apk",
                "mpvNova-2026-05-10-universal.apk",
            ),
            supportedAbis = listOf("armeabi-v7a", "armeabi")
        )

        assertEquals("mpvNova-2026-05-10-armeabi-v7a.apk", selected)
    }

    @Test
    fun chooseBestApkAssetNameFallsBackToUniversal() {
        val selected = chooseBestApkAssetName(
            assetNames = listOf(
                "mpvNova-2026-05-10-x86_64.apk",
                "mpvNova-2026-05-10-universal.apk",
            ),
            supportedAbis = listOf("armeabi-v7a")
        )

        assertEquals("mpvNova-2026-05-10-universal.apk", selected)
    }

    @Test
    fun chooseBestApkAssetNameFallsBackToAbiNeutralName() {
        val selected = chooseBestApkAssetName(
            assetNames = listOf(
                "mpvNova-2026-05-10-x86.apk",
                "mpvNova-2026-05-10-release.apk",
            ),
            supportedAbis = listOf("armeabi-v7a")
        )

        assertEquals("mpvNova-2026-05-10-release.apk", selected)
    }

    @Test
    fun chooseBestApkAssetNameReturnsNullForEmptyAssets() {
        assertNull(chooseBestApkAssetName(emptyList(), listOf("armeabi-v7a")))
    }
}
