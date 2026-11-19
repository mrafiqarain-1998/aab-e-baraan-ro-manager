package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import com.example.model.AppReleaseInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Free, zero-billing OTA App Update Checker supporting:
 * 1) Direct GitHub Releases API (0 Firestore reads, 100% free forever with GitHub Actions auto-builds)
 * 2) Optional Firestore `appReleases/latest` broadcast (1 cached doc read per 6 hours)
 *
 * Uses standard browser/system Intent.ACTION_VIEW handoff (no REQUEST_INSTALL_PACKAGES or DCL).
 */
object AppUpdateChecker {
    private const val TAG = "AppUpdateChecker"
    const val AUTO_CHECK_INTERVAL_MS: Long = 6L * 60L * 60L * 1000L // 6 hours

    fun installedVersionCode(): Int = BuildConfig.VERSION_CODE
    fun installedVersionName(): String = BuildConfig.VERSION_NAME

    fun normalizeGithubRepo(input: String): String {
        val trimmed = input.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .trim('/')
        val parts = trimmed.split("/").filter { it.isNotBlank() }
        return if (parts.size >= 2) "${parts[0]}/${parts[1]}" else ""
    }

    fun isUpdateNewer(
        remote: AppReleaseInfo,
        currentCode: Int = installedVersionCode(),
        currentName: String = installedVersionName()
    ): Boolean {
        if (remote.downloadUrl.isBlank() || !remote.downloadUrl.startsWith("https://")) return false
        if (remote.versionCode > 0 && remote.versionCode > currentCode) return true
        if (remote.versionName.isNotBlank() && compareSemver(remote.versionName, currentName) > 0) return true
        return false
    }

    fun compareSemver(a: String, b: String): Int {
        val cleanA = a.trim().removePrefix("v").removePrefix("V").substringBefore("-").substringBefore("+")
        val cleanB = b.trim().removePrefix("v").removePrefix("V").substringBefore("-").substringBefore("+")
        val partsA = cleanA.split(".").map { it.toIntOrNull() ?: 0 }
        val partsB = cleanB.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(partsA.size, partsB.size)
        for (i in 0 until maxLen) {
            val va = partsA.getOrElse(i) { 0 }
            val vb = partsB.getOrElse(i) { 0 }
            if (va != vb) return va.compareTo(vb)
        }
        return 0
    }

    fun parseGithubReleaseJson(jsonBody: String, repo: String): AppReleaseInfo? {
        return try {
            val root = JSONObject(jsonBody)
            val tagName = root.optString("tag_name", "").trim()
            val releaseName = root.optString("name", "").trim()
            val bodyNotes = root.optString("body", "").trim().take(800)
            val htmlUrl = root.optString("html_url", "").trim()

            // Prefer direct .apk asset URL when attached by GitHub Actions
            var apkUrl = ""
            val assets = root.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "").lowercase()
                    val dl = asset.optString("browser_download_url", "").trim()
                    if (name.endsWith(".apk") && dl.startsWith("https://")) {
                        apkUrl = dl
                        break
                    }
                }
            }
            val finalUrl = apkUrl.ifBlank { htmlUrl }
            if (!finalUrl.startsWith("https://")) return null

            val cleanVersionName = tagName.removePrefix("v").removePrefix("V").ifBlank { releaseName }
            // Extract optional build code suffix like v1.0.2-102 or build-102
            val extractedCode = Regex("""(?:-|\+|build[._-]?)(\d+)$""", RegexOption.IGNORE_CASE)
                .find(tagName)
                ?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: 0

            AppReleaseInfo(
                versionCode = extractedCode,
                versionName = cleanVersionName.substringBefore("-"),
                downloadUrl = finalUrl,
                githubRepo = repo,
                releaseNotes = bodyNotes.ifBlank { "Latest Aab-e-Baraan RO Manager release ($tagName)" },
                mandatory = false,
                updatedAtMillis = System.currentTimeMillis(),
                source = "github"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse GitHub release JSON: ${e.message}")
            null
        }
    }

    suspend fun fetchLatestFromGitHub(repoInput: String): AppReleaseInfo? = withContext(Dispatchers.IO) {
        val repo = normalizeGithubRepo(repoInput)
        if (repo.isBlank()) return@withContext null
        var conn: HttpURLConnection? = null
        try {
            val url = URL("https://api.github.com/repos/$repo/releases/latest")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "AabEBaraanROManager/${BuildConfig.VERSION_NAME}")
            }
            if (conn.responseCode !in 200..299) return@withContext null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parseGithubReleaseJson(body, repo)
        } catch (e: Exception) {
            Log.w(TAG, "GitHub release check skipped: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    fun openUpdateUrl(context: Context, downloadUrl: String): Boolean {
        val clean = downloadUrl.trim()
        if (!clean.startsWith("https://")) return false
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(clean)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open update URL: ${e.message}")
            false
        }
    }
}
