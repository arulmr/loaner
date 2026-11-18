package com.arulm.loaner

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : FragmentActivity() {
    private var pendingUpdateFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: LoanViewModel = viewModel()
            LoanerTheme(darkTheme = vm.darkMode) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val scope = rememberCoroutineScope()
                    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                        uri?.let { contentResolver.openOutputStream(it)?.bufferedWriter()?.use { writer -> writer.write(vm.exportJson()) } }
                    }
                    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                        uri?.let {
                            runCatching { contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> vm.importJson(reader.readText()) } }
                                .onFailure { error -> vm.message = "Import failed: ${error.message}" }
                        }
                    }
                    val installPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                        val update = pendingUpdateFile
                        if (update != null && packageManager.canRequestPackageInstalls()) {
                            installApk(update) { vm.message = it }
                        } else if (update != null) {
                            vm.message = "Install permission was not enabled"
                        }
                    }
                    Box {
                        LoanerApp(
                            vm = vm,
                            onExport = { exportLauncher.launch("loaner-backup.json") },
                            onImport = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                            onCheckUpdates = {
                                scope.launch {
                                    vm.message = "Checking for and downloading updates…"
                                    when (val result = downloadLatestUpdate()) {
                                        is UpdateResult.NoUpdate -> vm.message = result.message
                                        is UpdateResult.Failed -> vm.message = result.message
                                        is UpdateResult.Ready -> {
                                            pendingUpdateFile = result.file
                                            vm.message = "Version ${result.version} downloaded"
                                            if (packageManager.canRequestPackageInstalls()) {
                                                installApk(result.file) { vm.message = it }
                                            } else {
                                                vm.message = "Allow Loaner to install updates, then return to continue"
                                                installPermissionLauncher.launch(
                                                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")),
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            onExit = { finish() },
                            biometricAvailable = biometricAvailable(),
                            onBiometricAuth = { onSuccess -> showBiometricPrompt(onSuccess) { vm.message = it } },
                        )
                    }
                }
            }
        }
    }

    private fun biometricAvailable(): Boolean = BiometricManager.from(this)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    private fun showBiometricPrompt(onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (!biometricAvailable()) {
            onError("No enrolled biometric is available on this device")
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_USER_CANCELED) onError(errString.toString())
            }
            override fun onAuthenticationFailed() = onError("Biometric not recognized")
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Loaner")
                .setSubtitle("Confirm your identity")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .setNegativeButtonText("Use PIN")
                .build(),
        )
    }

    private suspend fun downloadLatestUpdate(): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val connection = (URL("https://api.github.com/repos/arulmr/loaner/releases/latest").openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000; readTimeout = 15000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Loaner-Android/${BuildConfig.VERSION_NAME}")
            }
            require(connection.responseCode in 200..299) { "GitHub returned ${connection.responseCode}" }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val release = JSONObject(body)
            val latest = release.getString("tag_name").removePrefix("v")
            if (!VersionComparator.isNewer(latest, BuildConfig.VERSION_NAME)) {
                return@withContext UpdateResult.NoUpdate("Loaner is up to date (version ${BuildConfig.VERSION_NAME})")
            }

            val assets = release.getJSONArray("assets")
            val asset = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk", ignoreCase = true) }
                ?: error("Release $latest does not contain an APK")
            val expectedSize = asset.getLong("size")
            require(expectedSize in 1..100_000_000) { "Invalid APK size" }

            val updateDir = File(cacheDir, "updates").apply { mkdirs() }
            val temp = File(updateDir, "Loaner-$latest.download")
            val apk = File(updateDir, "Loaner-$latest.apk")
            temp.delete()
            apk.delete()
            val download = (URL(asset.getString("browser_download_url")).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15000; readTimeout = 30000
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("User-Agent", "Loaner-Android/${BuildConfig.VERSION_NAME}")
            }
            require(download.responseCode in 200..299) { "Download returned ${download.responseCode}" }
            val downloadedSize = download.inputStream.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
            require(downloadedSize == expectedSize) { "Downloaded APK is incomplete" }
            require(temp.renameTo(apk)) { "Could not prepare downloaded APK" }
            validateDownloadedApk(apk, latest)?.let { validationError ->
                apk.delete()
                error(validationError)
            }
            UpdateResult.Ready(latest, apk)
        } catch (error: Exception) {
            UpdateResult.Failed("Update failed: ${error.message ?: "unknown error"}")
        }
    }

    private fun installApk(file: File, onError: (String) -> Unit) {
        runCatching {
            require(file.exists()) { "Downloaded update is missing" }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }.onFailure { onError("Could not open Android installer: ${it.message}") }
    }

    @Suppress("DEPRECATION")
    private fun validateDownloadedApk(file: File, expectedVersion: String): String? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = packageManager.getPackageArchiveInfo(file.absolutePath, flags) ?: return "Downloaded file is not a valid APK"
        if (archive.packageName != packageName) return "Downloaded APK has the wrong package name"
        if (archive.versionName != expectedVersion) return "Downloaded APK version does not match the release"
        if (packageVersionCode(archive) <= packageVersionCode(packageManager.getPackageInfo(packageName, flags))) return "Downloaded APK is not newer than this version"

        val installed = packageManager.getPackageInfo(packageName, flags)
        val installedSignatures = signatures(installed)
        val archiveSignatures = signatures(archive)
        if (installedSignatures.isEmpty() || archiveSignatures.none { candidate -> installedSignatures.any { it.contentEquals(candidate) } }) {
            return "Downloaded APK is not signed by Loaner"
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun signatures(info: PackageInfo): List<ByteArray> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.signingInfo?.apkContentsSigners?.map { it.toByteArray() }.orEmpty()
    } else info.signatures?.map { it.toByteArray() }.orEmpty()

    @Suppress("DEPRECATION")
    private fun packageVersionCode(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()

}

private sealed interface UpdateResult {
    data class Ready(val version: String, val file: File) : UpdateResult
    data class NoUpdate(val message: String) : UpdateResult
    data class Failed(val message: String) : UpdateResult
}

