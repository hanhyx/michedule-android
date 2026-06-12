package com.ljh.michedule

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ljh.michedule.ui.navigation.MicheduleNavHost
import com.ljh.michedule.ui.onboarding.OnboardingScreen
import com.ljh.michedule.ui.theme.*
import com.ljh.michedule.update.UpdateChecker
import com.ljh.michedule.update.UpdateInfo
import kotlinx.coroutines.launch

data class InviteData(val url: String, val key: String, val partnerCode: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MicheduleApp
        val invite = parseInviteIntent()

        setContent {
            val themeMode by app.prefsManager.themeMode.collectAsState(initial = "dark")
            val isDarkTheme = themeMode != "light"

            LaunchedEffect(isDarkTheme) {
                if (isDarkTheme) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    )
                } else {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    )
                }
            }

            MicheduleTheme(darkTheme = isDarkTheme) {
                val colors = LocalAppColors.current
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
                var showUpdateDialog by remember { mutableStateOf(false) }
                var showOnboarding by remember { mutableStateOf(invite != null) }
                val currentPartner by app.prefsManager.partnerCode.collectAsState(initial = "")

                val notificationLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { _ -> }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                LaunchedEffect(currentPartner, invite) {
                    if (invite != null && currentPartner == invite.partnerCode) {
                        showOnboarding = false
                    }
                }

                LaunchedEffect(Unit) {
                    if (!BuildConfig.DEBUG) {
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
                }

                if (showOnboarding && invite != null) {
                    OnboardingScreen(
                        partnerCode = invite.partnerCode,
                        prefsManager = app.prefsManager,
                        onComplete = {
                            showOnboarding = false
                            app.startSync()
                        }
                    )
                } else {
                    MicheduleNavHost(prefsManager = app.prefsManager)
                }

                if (showUpdateDialog && updateInfo != null) {
                    val info = updateInfo!!
                    AlertDialog(
                        onDismissRequest = { showUpdateDialog = false },
                        title = {
                            Text(
                                "업데이트 알림",
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        },
                        text = {
                            Column {
                                Text(
                                    "새 버전이 있습니다: v${info.latestVersion}",
                                    color = colors.textPrimary
                                )
                                Text(
                                    "현재 버전: v${info.currentVersion}",
                                    color = colors.textSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (info.releaseNotes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        info.releaseNotes,
                                        color = colors.textSecondary,
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
                                Text("업데이트", color = colors.accent, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showUpdateDialog = false }) {
                                Text("나중에", color = colors.textMuted)
                            }
                        },
                        containerColor = colors.card,
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
        }
    }

    private fun parseInviteIntent(): InviteData? {
        val uri = intent?.data ?: return null
        if (uri.scheme != "michedule" || uri.host != "join") return null

        val url = uri.getQueryParameter("url") ?: return null
        val key = uri.getQueryParameter("key") ?: return null
        val code = uri.getQueryParameter("code") ?: uri.getQueryParameter("room") ?: return null

        return InviteData(url, key, code)
    }
}
