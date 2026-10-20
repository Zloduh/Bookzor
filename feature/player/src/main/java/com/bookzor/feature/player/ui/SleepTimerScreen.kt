package com.bookzor.feature.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SleepTimerScreen(
    activeRemainingSec: Long?,
    onSetTimerMinutes: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var alwaysReactivate by remember { mutableStateOf(false) }
    var notifyBeforeStop by remember { mutableStateOf(false) }
    var shakeToRestart by remember { mutableStateOf(true) }
    var playbackActionsRestart by remember { mutableStateOf(false) }
    var vibrationOnRestart by remember { mutableStateOf(true) }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "SLEEP TIMER",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Set The Sleep Timer",
                color = Color.LightGray,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )

            // 1. "off" button (White box if active, dark gray if not)
            val isOff = activeRemainingSec == null || activeRemainingSec <= 0
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isOff) Color.White else Color(0xFF2C2C2C))
                    .clickable { onCancelTimer() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "off",
                    color = if (isOff) Color.Black else Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Preset Rows (5 min / 15 min), (10 min / 30 min)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SleepPresetButton("5 min", activeRemainingSec == 5 * 60L, Modifier.weight(1f)) { onSetTimerMinutes(5) }
                SleepPresetButton("15 min", activeRemainingSec == 15 * 60L, Modifier.weight(1f)) { onSetTimerMinutes(15) }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SleepPresetButton("10 min", activeRemainingSec == 10 * 60L, Modifier.weight(1f)) { onSetTimerMinutes(10) }
                SleepPresetButton("30 min", activeRemainingSec == 30 * 60L, Modifier.weight(1f)) { onSetTimerMinutes(30) }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Custom Button with settings gear
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2C2C2C))
                        .clickable { onSetTimerMinutes(45) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = Color.LightGray)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2C2C2C))
                        .clickable { onSetTimerMinutes(45) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "custom ( 45 m )", color = Color.White, fontSize = 15.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4. End Of Chapter button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2C2C2C))
                    .clickable { onSetTimerMinutes(20) },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "End Of Chapter", color = Color.White, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 5. Options Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E1E1E))
                    .padding(vertical = 4.dp)
            ) {
                OptionSwitchRow("Always reactivate timer", alwaysReactivate) { alwaysReactivate = it }
                Divider(color = Color(0xFF2A2A2A))
                OptionSwitchRow("Notify 10 sec before stop", notifyBeforeStop) { notifyBeforeStop = it }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Restart timer on:",
                color = Color.LightGray,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            // 6. Restart Options Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E1E1E))
                    .padding(vertical = 4.dp)
            ) {
                OptionSwitchRow("Shake to restart timer", shakeToRestart) { shakeToRestart = it }
                Divider(color = Color(0xFF2A2A2A))
                OptionSwitchRow("Playback actions ( ⏸ , ▶... )", playbackActionsRestart) { playbackActionsRestart = it }
                Divider(color = Color(0xFF2A2A2A))
                OptionSwitchRow("Vibration on restart", vibrationOnRestart) { vibrationOnRestart = it }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun SleepPresetButton(text: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0xFFFF5722) else Color(0xFF2C2C2C))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 15.sp
        )
    }
}

@Composable
fun OptionSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color.White, fontSize = 14.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFFFF5722),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color(0xFF333333)
            )
        )
    }
}
