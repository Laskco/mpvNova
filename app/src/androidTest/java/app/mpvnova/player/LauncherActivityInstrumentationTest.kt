package app.mpvnova.player

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherActivityInstrumentationTest {
    @Test
    fun homeActivityReachesResumedState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var resumed = false
            scenario.onActivity { activity ->
                resumed = !activity.isFinishing && !activity.isDestroyed
            }
            assertTrue("Launcher activity did not remain active", resumed)
        }
    }
}
