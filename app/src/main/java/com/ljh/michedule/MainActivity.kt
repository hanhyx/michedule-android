package com.ljh.michedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ljh.michedule.ui.navigation.MicheduleNavHost
import com.ljh.michedule.ui.theme.*
import com.ljh.michedule.update.UpdateChecker
import com.ljh.michedule.update.UpdateInfo
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MicheduleApp

        setContent {
            MicheduleTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
                var showUpdateDialog by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    val currentVersion = try {
                        context.packageManager
                            .getPackageInfo(context.packageName, 0)
                            .versionName ?: "1.0.0"
                    } catch (_: Exception) { "1.0.0" }

                    val info = UpdateChecker.checkForUpdate(currentVersion)
                    if (info.hasUpdate) {
                        updateInfo = info
                        showUpdateDialog = true
                    }
                }

                MicheduleNavHost(prefsManager = app.prefsManager)

                if (showUpdateDialog && updateInfo != null) {
                    val info = updateInfo!!
                    AlertDialog(
                        onDismissRequest = { showUpdateDialog = false },
                        title = {
                            Text(
                                "업데이트 알림",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        },
                        text = {
                            Column {
                                Text(
                                    "새 버전이 있습니다: v${info.latestVersion}",
                                    color = TextPrimary
                                )
                                Text(
                                    "현재 버전: v${info.currentVersion}",
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (info.releaseNotes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        info.releaseNotes,
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                if (info.downloadUrl.isNotBlank()) {
                                    UpdateChecker.downloadAndInstall(
                                        context,
                                        info.downloadUrl,
                                        info.latestVersion
                                    )
                                }
                                showUpdateDialog = false
                            }) {
                                Text("업데이트", color = Purple80, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showUpdateDialog = false }) {
                                Text("나중에", color = TextMuted)
                            }
                        },
                        containerColor = DarkCard,
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
        }
    }
}
