package app.mpvnova.player

import app.mpvnova.player.databinding.PlayerBinding
import app.mpvnova.player.MpvEvent
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import androidx.appcompat.app.AlertDialog
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.DisplayMetrics
import android.util.Rational
import androidx.core.content.ContextCompat
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import java.io.FileNotFoundException
import java.lang.IllegalArgumentException
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal fun MPVActivity.availableSecondarySubTracks(): List<MPVView.Track> {
    val subs = player.tracks["sub"] ?: return emptyList()
    val primarySid = player.sid
    return subs.filter { it.mpvId >= 1 && it.mpvId != primarySid }
}

internal fun MPVActivity.listTrackMeta(type: String): List<TrackMeta> {
    val count = mpvGetPropertyInt("track-list/count") ?: return emptyList()
    return (0 until count).mapNotNull { index ->
        if (mpvGetPropertyString("track-list/$index/type") == type) {
            val id = mpvGetPropertyInt("track-list/$index/id")
            val title = mpvGetPropertyString("track-list/$index/title") ?: ""
            val lang = mpvGetPropertyString("track-list/$index/lang") ?: ""
            id?.let { TrackMeta(it, title, lang) }
        } else {
            null
        }
    }
}

internal fun MPVActivity.normalizeTitleTokens(title: String): Set<String> {
    if (title.isEmpty()) return emptySet()
    return title
        .lowercase()
        .replace(TITLE_NON_ALNUM_REGEX, " ")
        .split(WHITESPACE_REGEX)
        .filter { it.length > 2 && it !in trackTitleStopwords }
        .toSet()
}

internal fun MPVActivity.titleSimilarity(saved: String, candidate: String): Double {
    val savedTokens = normalizeTitleTokens(saved)
    val candidateTokens = normalizeTitleTokens(candidate)
    if (savedTokens.isEmpty() || candidateTokens.isEmpty()) return 0.0
    val common = savedTokens.intersect(candidateTokens).size
    return common.toDouble() / savedTokens.size.toDouble()
}

internal fun MPVActivity.langPrefixMatch(a: String, b: String): Boolean {
    if (a.isEmpty() || b.isEmpty()) return false
    return a.lowercase().take(2) == b.lowercase().take(2)
}

internal fun MPVActivity.saveUserTrackPick(type: String, mpvId: Int) {
    if (mpvId == -1) return
    val meta = listTrackMeta(type).firstOrNull { it.mpvId == mpvId } ?: return
    val prefs = getDefaultSharedPreferences(applicationContext)
    val (titleKey, langKey) = trackMemoryKeys(type)
    prefs.edit().apply {
        putString(titleKey, meta.title)
        putString(langKey, meta.lang)
        apply()
    }
}

internal fun MPVActivity.applyRememberedTrack(type: String) {
    val prefs = getDefaultSharedPreferences(applicationContext)
    val (titleKey, langKey) = trackMemoryKeys(type)
    val savedTitle = prefs.getString(titleKey, null)
    val savedLang = prefs.getString(langKey, "") ?: ""

    if (savedTitle != null) {
        val compatible = listTrackMeta(type).filter {
            savedLang.isEmpty() || langPrefixMatch(it.lang, savedLang)
        }

        val exactMatch = compatible.firstOrNull { it.title.equals(savedTitle, ignoreCase = true) }
        if (exactMatch != null) {
            setTrackForMemory(type, exactMatch.mpvId, exactMatch.title, score = 1.0, exact = true)
        } else {
            val (bestMatch, bestScore) = bestTrackTitleMatch(compatible, savedTitle)
            if (bestMatch != null && bestScore >= TRACK_MEMORY_MIN_SCORE) {
                setTrackForMemory(type, bestMatch.mpvId, bestMatch.title, bestScore, exact = false)
            }
        }
    }
}

internal fun MPVActivity.bestTrackTitleMatch(tracks: List<TrackMeta>, savedTitle: String): Pair<TrackMeta?, Double> {
    var bestMatch: TrackMeta? = null
    var bestScore = 0.0
    tracks.forEach { track ->
        val score = titleSimilarity(savedTitle, track.title)
        if (score > bestScore) {
            bestScore = score
            bestMatch = track
        }
    }
    return bestMatch to bestScore
}
