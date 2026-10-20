package com.bookzor.feature.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerBottomSheet(
    remainingSeconds: Long?,
    isShakeToResetEnabled: Boolean,
    onSetTimerMinutes: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onToggleShakeToReset: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A1A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sleep Timer",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                if (remainingSeconds != null && remainingSeconds > 0) {
                    val mins = remainingSeconds / 60
                    val secs = remainingSeconds % 60
                    Text(
                        text = "Active: ${mins}m ${secs}s",
                        color = Color(0xFFFF5722),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Timer Presets
            listOf(15, 30, 45, 60).forEach { minutes ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF262626))
                        .clickable {
                            onSetTimerMinutes(minutes)
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = "$minutes Minutes",
                        color = Color.White,
                        fontSize = 15.sp
                    )
                }
            }

            if (remainingSeconds != null && remainingSeconds > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF3E1E1E))
                        .clickable {
                            onCancelTimer()
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = "Turn Off Sleep Timer",
                        color = Color(0xFFFF6D6D),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Shake to Reset Option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF262626), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Shake to Reset",
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Shake phone before timer ends to extend",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = isShakeToResetEnabled,
                    onCheckedChange = onToggleShakeToReset,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFFF5722)
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
