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
            .removePrefix("https://www.github.com/")
            .removePrefix("http://www.github.com/")
            .removePrefix("www.github.com/")
            .removePrefix("https://api.github.com/repos/")
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .trim('/')
        val parts = trimmed.split("/").filter { it.isNotBlank() }
        if (parts.size < 2) return ""
        val owner = parts[0].trim()
        val repo = parts[1].trim().removeSuffix(".git")
        return if (owner.isNotBlank() && repo.isNotBlank()) "$owner/$repo" else ""
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

    fun defaultConfiguredGithubRepo(): String {
        return normalizeGithubRepo(BuildConfig.GITHUB_UPDATE_REPO)
    }

    data class GitHubReleaseFetchResult(
        val release: AppReleaseInfo? = null,
        val httpCode: Int = 0,
        val errorMessage: String = ""
    )

    suspend fun fetchLatestFromGitHubDetailed(repoInput: String): GitHubReleaseFetchResult = withContext(Dispatchers.IO) {
        val repo = normalizeGithubRepo(repoInput)
        if (repo.isBlank()) {
            return@withContext GitHubReleaseFetchResult(errorMessage = "GitHub repository is not connected yet.")
        }

        fun request(urlStr: String): Pair<Int, String?> {
            var conn: HttpURLConnection? = null
            return try {
                val url = URL(urlStr)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "AabEBaraanROManager/${BuildConfig.VERSION_NAME}")
                }
                val code = conn.responseCode
                val body = if (code in 200..299) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else null
                code to body
            } catch (e: Exception) {
                Log.w(TAG, "GitHub release request error: ${e.message}")
                -1 to e.localizedMessage
            } finally {
                conn?.disconnect()
            }
        }

        // 1. Primary: /releases/latest
        val (latestCode, latestBody) = request("https://api.github.com/repos/$repo/releases/latest")
        if (latestCode in 200..299 && !latestBody.isNullOrBlank()) {
            val parsed = parseGithubReleaseJson(latestBody, repo)
            if (parsed != null) {
                return@withContext GitHubReleaseFetchResult(release = parsed, httpCode = latestCode)
            }
        }

        // 2. Fallback: /releases?per_page=1 (covers pre-releases or releases not marked latest)
        if (latestCode == 404) {
            val (listCode, listBody) = request("https://api.github.com/repos/$repo/releases?per_page=1")
            if (listCode in 200..299 && !listBody.isNullOrBlank()) {
                try {
                    val arr = org.json.JSONArray(listBody)
                    if (arr.length() > 0) {
                        val firstObj = arr.optJSONObject(0)
                        if (firstObj != null) {
                            val parsed = parseGithubReleaseJson(firstObj.toString(), repo)
                            if (parsed != null) {
                                return@withContext GitHubReleaseFetchResult(release = parsed, httpCode = listCode)
                            }
                        }
                    }
                    return@withContext GitHubReleaseFetchResult(
                        httpCode = 404,
                        errorMessage = "GitHub repo '$repo' found, but no APK Release is published yet. Run the GitHub Actions workflow first."
                    )
                } catch (_: Exception) {}
            }
            val (repoCode, _) = request("https://api.github.com/repos/$repo")
            val message = if (repoCode in 200..299) {
                "GitHub repo '$repo' is public, but no APK Release is published yet (404). Open GitHub -> Actions -> Build & Publish Release APK -> Run workflow."
            } else {
                "Could not access GitHub repo '$repo' (404). Either this GitHub repo is PRIVATE (change to Public in GitHub Settings -> Danger Zone -> Change visibility) or the owner/repo name is incorrect."
            }
            return@withContext GitHubReleaseFetchResult(
                httpCode = 404,
                errorMessage = message
            )
        }

        if (latestCode == 403) {
            return@withContext GitHubReleaseFetchResult(
                httpCode = 403,
                errorMessage = "GitHub API limit reached. Please try again in a few minutes."
            )
        }

        val errDetail = if (latestCode == -1) (latestBody ?: "No internet connection") else "HTTP $latestCode"
        return@withContext GitHubReleaseFetchResult(
            httpCode = latestCode,
            errorMessage = "Could not reach GitHub ($errDetail)."
        )
    }

    suspend fun fetchLatestFromGitHub(repoInput: String): AppReleaseInfo? {
        return fetchLatestFromGitHubDetailed(repoInput).release
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
