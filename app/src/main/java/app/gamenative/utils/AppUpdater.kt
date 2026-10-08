package app.gamenative.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import app.gamenative.BuildConfig
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber

/**
 * In-app updates from this project's GitHub releases ([BuildConfig.UPDATE_REPO], set in app/build.gradle.kts).
 *
 * The newest non-draft, non-prerelease release is offered when its tag ("v1.2.3") is newer than the
 * installed versionName, and its first .apk asset is downloaded and handed to the Android installer.
 */
object AppUpdater {
    private val RELEASES_URL = "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest"

    data class Release(
        val version: String,
        val name: String,
        val notes: String,
        val downloadUrl: String,
        val sizeBytes: Long,
    )

    fun isNewer(latest: String, current: String = BuildConfig.VERSION_NAME): Boolean {
        val parse = { v: String ->
            v.trimStart('v', 'V')
                .split('-', '_', '+')[0]
                .split('.')
                .mapNotNull { it.toIntOrNull() }
        }
        val l = parse(latest)
        val c = parse(current)
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return false
    }

    fun parseRelease(jsonString: String, currentVersion: String = BuildConfig.VERSION_NAME): Release? = runCatching {
        val json = JSONObject(jsonString)
        val tag = json.optString("tag_name")
        if (!isNewer(tag, currentVersion)) return null

        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                return Release(
                    version = tag.trimStart('v', 'V'),
                    name = json.optString("name", tag),
                    notes = json.optString("body"),
                    downloadUrl = url,
                    sizeBytes = asset.optLong("size", 0L),
                )
            }
        }
        null
    }.getOrNull()

    suspend fun check(currentVersion: String = BuildConfig.VERSION_NAME): Release? = withContext(Dispatchers.IO) {
        // Dev builds (package app.wowds.dev) are a separate app; releases can't update them.
        if (BuildConfig.DEBUG) return@withContext null
        runCatching {
            val request = Request.Builder()
                .url(RELEASES_URL)
                .header("User-Agent", "WoW-DS")
                .header("Accept", "application/vnd.github+json")
                .build()
            Net.http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                parseRelease(body, currentVersion)
            }
        }.getOrElse {
            Timber.w(it, "AppUpdater check failed")
            null
        }
    }

    suspend fun download(
        context: Context,
        release: Release,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val dest = File(dir, "WoW-DS-${release.version}.apk")
        if (!(dest.exists() && dest.length() == release.sizeBytes && release.sizeBytes > 0)) {
            Net.fetchFile(release.downloadUrl, dest, onProgress)
        }
        onProgress(1f)
        runCatching { verify(context, dest) }.onFailure {
            dest.delete()
            throw it
        }
        dest
    }

    /**
     * Android refuses an update signed with a different key, with only a vague "App not installed".
     * Checking first gives a message that says what's wrong.
     */
    private fun verify(context: Context, apk: File) {
        val pm = context.packageManager
        val update = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw IOException("the downloaded file isn't a valid app")
        if (update.packageName != context.packageName) {
            throw IOException("the download is ${update.packageName}, not ${context.packageName}")
        }
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val ours = installed.signingInfo?.apkContentsSigners?.toSet()
        val theirs = update.signingInfo?.apkContentsSigners?.toSet()
        if (ours != null && theirs != null && ours != theirs) {
            throw IOException(
                "the update is signed with a different key than this install. " +
                    "Uninstall WoW-DS and install the new version from the GitHub releases page.",
            )
        }
    }

    fun canInstall(context: Context): Boolean = runCatching {
        context.packageManager.canRequestPackageInstalls()
    }.getOrElse {
        Timber.w(it, "canRequestPackageInstalls check failed")
        false
    }

    fun openInstallPermissionSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            }
        }
    }

    fun install(context: Context, apkFile: File) {
        if (!canInstall(context)) {
            openInstallPermissionSettings(context)
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
