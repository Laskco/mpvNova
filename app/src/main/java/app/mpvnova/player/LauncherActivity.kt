package app.mpvnova.player

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchHome()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        launchHome()
    }

    @Suppress("DEPRECATION")
    private fun launchHome() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        )
        finish()
        overridePendingTransition(0, 0)
    }
}
