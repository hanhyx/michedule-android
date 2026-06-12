package com.ljh.michedule.ui.onboarding

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ljh.michedule.data.PrefsManager
import com.ljh.michedule.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    partnerCode: String,
    prefsManager: PrefsManager,
    onComplete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    val colors = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "📅",
            fontSize = 64.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Michedule",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = colors.accent
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "초대를 받았습니다!\n이름만 입력하면 바로 연결됩니다.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("내 이름", color = colors.textMuted) },
            placeholder = { Text("이름 입력", color = colors.textMuted) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.border,
                cursorColor = colors.accent,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedLabelColor = colors.accent
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                scope.launch {
                    prefsManager.setMyName(name.trim())
                    prefsManager.setPartnerCode(partnerCode)
                    Toast.makeText(context, "연결 완료!", Toast.LENGTH_SHORT).show()
                    onComplete()
                }
            },
            enabled = name.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accentDark,
                disabledContainerColor = colors.surface
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = "시작하기",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
