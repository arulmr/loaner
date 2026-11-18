package com.arulm.loaner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LoanerTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val vm: LoanViewModel = viewModel()
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
                    Box {
                        LoanerApp(
                            vm = vm,
                            onExport = { exportLauncher.launch("loaner-backup.json") },
                            onImport = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                            onCheckUpdates = {
                                scope.launch {
                                    vm.message = "Checking for updates…"
                                    val result = checkLatestRelease()
                                    vm.message = result.first
                                    result.second?.let { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    private suspend fun checkLatestRelease(): Pair<String, String?> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL("https://api.github.com/repos/arulmr/loaner/releases/latest").openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000; readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Loaner-Android/1.0")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val release = JSONObject(body)
            val latest = release.getString("tag_name").removePrefix("v")
            if (latest != BuildConfig.VERSION_NAME) "Version $latest is available. Opening download page." to release.getString("html_url")
            else "Loaner is up to date (version ${BuildConfig.VERSION_NAME})" to null
        }.getOrElse { "Could not check for updates: ${it.message}" to null }
    }
}

