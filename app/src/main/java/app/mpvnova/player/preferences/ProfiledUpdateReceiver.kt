package app.mpvnova.player.preferences

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.core.content.IntentCompat
import app.mpvnova.player.R
import java.io.File

class ProfiledUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val apk = intent.getStringExtra(EXTRA_UPDATE_APK)?.let(::File) ?: return
        val tagName = intent.getStringExtra(EXTRA_UPDATE_TAG)
        if (sessionId < 0 || !context.isUpdateCacheApk(apk)) {
            return
        }
        Log.i(PROFILED_UPDATE_TAG, "Update session $sessionId returned status $status")
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            confirm(context, intent, sessionId, apk, tagName)
        } else {
            finishProfiledInstallProgress(sessionId)
            cancelCallback(context, sessionId)
            finish(context, intent, status, apk, tagName)
        }
    }

    private fun confirm(context: Context, intent: Intent, sessionId: Int, apk: File, tagName: String?) {
        val confirmation = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
        queueProfiledUpdateUi { manager ->
            val installer = context.packageManager.packageInstaller
            if (installer.getSessionInfo(sessionId) != null) {
                val launched = confirmation != null &&
                    runCatching { manager.activity.startActivity(confirmation) }.isSuccess
                if (!launched) {
                    finishProfiledInstallProgress(sessionId)
                    cancelCallback(context, sessionId)
                    runCatching { installer.abandonSession(sessionId) }
                    manager.installApkWithSystemUi(tagName, apk)
                }
            }
        }
    }

    private fun finish(context: Context, intent: Intent, status: Int, apk: File, tagName: String?) {
        val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val rejectedProfile = status == PackageInstaller.STATUS_FAILURE_INVALID &&
            detail?.startsWith("INSTALL_FAILED_BAD_DEX_METADATA") == true
        if (rejectedProfile && apk.isFile) {
            queueProfiledUpdateUi { it.installApkWithSystemUi(tagName, apk) }
        } else {
            context.finishUpdateInstaller(apk)
            val canceled = status == PackageInstaller.STATUS_FAILURE_ABORTED &&
                (detail == null || detail.startsWith("INSTALL_FAILED_ABORTED"))
            if (status != PackageInstaller.STATUS_SUCCESS && !canceled) {
                queueProfiledUpdateUi { it.showError(context.getString(R.string.update_install_retry)) }
            }
        }
    }

    companion object {
        private const val EXTRA_UPDATE_APK = "app.mpvnova.player.extra.PROFILED_UPDATE_APK"
        private const val EXTRA_UPDATE_TAG = "app.mpvnova.player.extra.PROFILED_UPDATE_TAG"

        private fun callbackFlags(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }

        internal fun callback(context: Context, sessionId: Int, apk: File, tagName: String?): PendingIntent =
            PendingIntent.getBroadcast(
                context, sessionId,
                Intent(context, ProfiledUpdateReceiver::class.java)
                    .putExtra(EXTRA_UPDATE_APK, apk.absolutePath)
                    .putExtra(EXTRA_UPDATE_TAG, tagName),
                callbackFlags() or PendingIntent.FLAG_CANCEL_CURRENT,
            )

        internal fun cancelCallback(context: Context, sessionId: Int) {
            PendingIntent.getBroadcast(
                context, sessionId, Intent(context, ProfiledUpdateReceiver::class.java),
                callbackFlags() or PendingIntent.FLAG_NO_CREATE,
            )?.cancel()
        }
    }
}
