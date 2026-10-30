package com.borealis.planner.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Updates Borealis from its GitHub releases: finds the newest build, downloads the APK and
 * hands it to Android's installer, which installs it over this copy and keeps all tasks.
 */
object Updater {
    private const val REPO = "NoorJ29/Plan"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases/latest"
    private const val PREFS = "updater"

    data class Release(
        val versionCode: Int,
        val versionName: String,
        val notes: String,
        val apkUrl: String,
        val sizeBytes: Long,
        val publishedAt: String,
    )

    sealed interface Check {
        data class Available(val release: Release) : Check
        data class UpToDate(val versionName: String) : Check
        data class Failed(val message: String) : Check
    }

    fun installedVersionCode(context: Context): Int = try {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0)).toInt()
    } catch (e: Exception) { 0 }

    fun installedVersionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
    } catch (e: Exception) { "1.0" }

    suspend fun check(context: Context): Check = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Borealis-Android")
            }
            val code = conn.responseCode
            if (code == 403 || code == 429) return@withContext Check.Failed("GitHub is busy. Try again in a few minutes.")
            if (code != 200) return@withContext Check.Failed("Couldn't reach the update server (error $code).")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val o = JSONObject(body)
            val tag = o.optString("tag_name")
            val vc = Regex("(\\d+)$").find(tag)?.groupValues?.get(1)?.toIntOrNull()
                ?: return@withContext Check.Failed("The latest release has no version number.")
            val assets = o.optJSONArray("assets")
            var url: String? = null
            var size = 0L
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                if (a.optString("name").endsWith(".apk")) { url = a.optString("browser_download_url"); size = a.optLong("size"); break }
            }
            if (url.isNullOrEmpty()) return@withContext Check.Failed("The latest release has no app file yet. Try again soon.")
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("lastCheck", System.currentTimeMillis()).apply()
            val release = Release(
                versionCode = vc,
                versionName = tag.removePrefix("v"),
                notes = cleanNotes(o.optString("body")),
                apkUrl = url,
                sizeBytes = size,
                publishedAt = o.optString("published_at"),
            )
            if (vc > installedVersionCode(context)) Check.Available(release) else Check.UpToDate(installedVersionName(context))
        } catch (e: java.net.UnknownHostException) {
            Check.Failed("You're offline. Connect to the internet and try again.")
        } catch (e: java.net.SocketTimeoutException) {
            Check.Failed("The connection timed out. Try again.")
        } catch (e: Exception) {
            Check.Failed("Couldn't check for updates. Try again.")
        }
    }

    private fun cleanNotes(raw: String): String =
        raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("Co-Authored-By", true) && !it.startsWith("Claude-Session", true) && !it.startsWith("Open this page", true) }
            .take(12)
            .joinToString("\n")

    /** True when an automatic check is due (at most every 6 hours). */
    fun shouldAutoCheck(context: Context): Boolean {
        val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("lastCheck", 0L)
        return System.currentTimeMillis() - last > 6 * 60 * 60 * 1000L
    }

    fun dismissedVersion(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("dismissed", 0)

    fun dismiss(context: Context, versionCode: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("dismissed", versionCode).apply()
    }

    /** Downloads the APK into the app's cache, reporting progress from 0 to 1. */
    suspend fun download(context: Context, release: Release, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "Borealis-${release.versionName}.apk")
        val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "Borealis-Android")
        }
        if (conn.responseCode !in 200..299) throw IllegalStateException("Download failed (error ${conn.responseCode}).")
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
        conn.inputStream.use { input ->
            out.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var read = 0L
                var last = 0L
                while (true) {
                    ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    read += n
                    if (total > 0 && read - last > 96 * 1024) { last = read; onProgress((read.toFloat() / total).coerceIn(0f, 1f)) }
                }
            }
        }
        onProgress(1f)
        out
    }

    enum class Signature { SAME, DIFFERENT, UNKNOWN }

    /** Compares the downloaded APK's signing key with the installed app's. A different key can't install over this copy. */
    @Suppress("DEPRECATION")
    fun compareSignature(context: Context, apk: File): Signature = try {
        val pm = context.packageManager
        fun digests(info: PackageInfo?): Set<String>? {
            info ?: return null
            val sigs = if (Build.VERSION.SDK_INT >= 28) {
                val si = info.signingInfo ?: return null
                if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
            } else {
                info.signatures
            } ?: return null
            return sigs.map { sig -> MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
        }
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = digests(pm.getPackageInfo(context.packageName, flags))
        val archive = digests(pm.getPackageArchiveInfo(apk.path, flags))
        when {
            installed == null || archive == null -> Signature.UNKNOWN
            installed.intersect(archive).isNotEmpty() -> Signature.SAME
            else -> Signature.DIFFERENT
        }
    } catch (e: Exception) { Signature.UNKNOWN }

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /** Opens the system page where the user allows Borealis to install updates. */
    fun openInstallPermission(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(intent) } catch (e: Exception) {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Hands the APK to Android's package installer. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".updates", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun formatSize(bytes: Long): String = when {
        bytes <= 0 -> ""
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}
