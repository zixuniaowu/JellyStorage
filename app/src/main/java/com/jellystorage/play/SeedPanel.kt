package com.jellystorage.play

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * 种子远征面板：输入好友的种子码打同一张地图；展示本档种子码供分享。
 * 纯本地，无服务器。
 */
@Composable
fun SeedPanel(
    input: String,
    message: String,
    currentCode: String?,
    language: GameLanguage,
    onInputChange: (String) -> Unit,
    onLaunch: () -> Unit,
    onCancel: () -> Unit
) {
    val t: (String) -> String = { GameI18n.tr(it) }
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    var copied by remember(currentCode, language) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(340.dp)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(t("种子远征"), color = Color(0xFFF5EBD4), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(t("输入好友种子码 · 打同一张图（职业用你自己的）"), color = Color(0xFFD6BB8F), fontSize = 10.sp)
        if (currentCode != null) {
            Text("${t("本档种子")} $currentCode", color = Color(0xFFFDBA74), fontSize = 11.sp)
            TextButton(
                onClick = {
                    clipboardScope.launch {
                        clipboard.setClipEntry(
                            ClipEntry(ClipData.newPlainText("JellyStorage seed", currentCode))
                        )
                    }
                    copied = true
                }
            ) {
                Text(t(if (copied) "已复制" else "复制种子码"), color = Color(0xFF67E8F9), fontSize = 11.sp)
            }
        }
        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color(0xFFF5EBD4),
            unfocusedTextColor = Color(0xFFE7E0D4),
            focusedBorderColor = Color(0xFFFBBF24),
            unfocusedBorderColor = Color(0xFF7C2D12),
            cursorColor = Color(0xFFFBBF24),
            focusedLabelColor = Color(0xFFFBBF24),
            unfocusedLabelColor = Color(0xFFD6BB8F)
        )
        OutlinedTextField(
            value = input,
            onValueChange = { s -> onInputChange(s.filter { it.isLetterOrDigit() || it == '_' }.take(24)) },
            label = { Text("${t("种子码")} (3vk7d2_2)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors
        )
        if (message.isNotEmpty()) {
            Text(t(message), color = Color(0xFFF87171), fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF78716C)),
                modifier = Modifier.weight(1f)
            ) { Text(t("取消"), color = Color(0xFFF5EBD4), fontSize = 14.sp) }
            Button(
                onClick = onLaunch,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309)),
                modifier = Modifier.weight(1f)
            ) { Text(t("出发"), color = Color(0xFFF5EBD4), fontSize = 14.sp) }
        }
        TextButton(onClick = onCancel) {
            Text(t("有存档时出发会覆盖当前进度"), color = Color(0xFF9C8469), fontSize = 10.sp)
        }
    }
}
