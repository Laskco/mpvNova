package app.mpvnova.player.preferences

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import app.mpvnova.player.R
import java.io.File
import java.io.IOException

internal const val PROFILED_UPDATE_TAG = "ProfiledUpdate"

internal fun AppUpdateManager.prepareUpdateInstall(tagName: String?, apk: File) {
    val context = activity.applicationContext
    val installer = context.packageManager.packageInstaller
    retainUpdateInstaller(apk)
    val started = runUpdateOperation(
        message = activity.getString(R.string.update_preparing_install),
        operation = {
            runCatching { validateAndStageUpdate(context, apk) }
                .onFailure { context.finishUpdateInstaller(apk) }
                .getOrThrow()
        },
        onDiscard = { session ->
            session?.let { runCatching { installer.abandonSession(it) } }
            context.finishUpdateInstaller(apk)
        },
    ) { result ->
        result.onSuccess { session ->
            if (session != null) rememberPendingUpdate(tagName, apk)
            if (session == null || !commitProfiledUpdate(context, session, tagName, apk)) {
                queueProfiledUpdateUi { it.installApkWithSystemUi(tagName, apk) }
            } else {
                beginProfiledInstallProgress(session)
            }
        }.onFailure {
            showError(activity.getString(R.string.update_invalid_apk))
        }
    }
    if (!started) context.finishUpdateInstaller(apk)
}

@Suppress("DEPRECATION")
private fun validateAndStageUpdate(context: Context, apk: File): Int? {
    val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
    if (archive?.packageName != context.packageName) throw IOException("Not an mpvNova update APK")
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) stageProfiledUpdate(context, apk) else null
}

@RequiresApi(Build.VERSION_CODES.P)
private fun stageProfiledUpdate(context: Context, apk: File): Int? = runCatching {
    val metadata = readUpdateDexMetadata(apk, Build.VERSION.SDK_INT) ?: return null
    val installer = context.packageManager.packageInstaller
    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
        setAppPackageName(context.packageName)
        setInstallLocation(PackageInfo.INSTALL_LOCATION_AUTO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
    }
    val sessionId = installer.createSession(params)
    var staged = false
    try {
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { output ->
                apk.inputStream().use { it.copyTo(output) }
                session.fsync(output)
            }
            session.openWrite("base.dm", 0, metadata.size.toLong()).use { output ->
                output.write(metadata)
                session.fsync(output)
            }
        }
        staged = true
        sessionId
    } finally {
        if (!staged) runCatching { installer.abandonSession(sessionId) }
    }
}.onFailure { Log.w(PROFILED_UPDATE_TAG, "Could not stage baseline profile; using APK-only install", it) }.getOrNull()

private fun commitProfiledUpdate(context: Context, sessionId: Int, tagName: String?, apk: File): Boolean {
    val installer = context.packageManager.packageInstaller
    return runCatching {
        installer.openSession(sessionId).use { session ->
            val callback = ProfiledUpdateReceiver.callback(context, sessionId, apk, tagName)
            session.commit(callback.intentSender)
        }
        Log.i(PROFILED_UPDATE_TAG, "Submitted update session $sessionId with dex metadata (release $tagName)")
    }.onFailure {
        Log.w(PROFILED_UPDATE_TAG, "Could not submit update session; using APK-only install", it)
        ProfiledUpdateReceiver.cancelCallback(context, sessionId)
        runCatching { installer.abandonSession(sessionId) }
    }.isSuccess
}
