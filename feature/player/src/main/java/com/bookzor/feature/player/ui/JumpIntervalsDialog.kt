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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun JumpIntervalsDialog(
    currentBackSec: Int,
    currentForwardSec: Int,
    onBackSecChange: (Int) -> Unit,
    onForwardSecChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val backOptions = listOf(5, 10, 15, 30, 45)
    val forwardOptions = listOf(15, 30, 45, 60, 90)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        title = {
            Text(
                text = "Custom Jump Intervals",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Jump Back Interval (Seconds)",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    backOptions.forEach { sec ->
                        val isSelected = currentBackSec == sec
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFFFF5722) else Color(0xFF2C2C2C))
                                .clickable { onBackSecChange(sec) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "-${sec}s",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Jump Forward Interval (Seconds)",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    forwardOptions.forEach { sec ->
                        val isSelected = currentForwardSec == sec
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFFFF5722) else Color(0xFF2C2C2C))
                                .clickable { onForwardSecChange(sec) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "+${sec}s",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Done", color = Color.LightGray)
            }
        }
    )
}
