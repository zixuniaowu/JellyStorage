package com.jellystorage.play

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginPanel(
    username: String,
    password: String,
    message: String,
    isRegister: Boolean,
    hasAccount: Boolean,
    guestHint: String,
    onUserChange: (String) -> Unit,
    onPassChange: (String) -> Unit,
    onToggleMode: () -> Unit,
    onSubmit: () -> Unit,
    onGuestEnter: () -> Unit,
    onAutoFill: () -> Unit
) {
    val paper = Brush.verticalGradient(
        listOf(Color(0xFFF3E9D2), Color(0xFFE8D9B8), Color(0xFFD4C4A0))
    )
    val cardShape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .fillMaxSize()
            .background(paper),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth(0.58f)
                .background(Color(0xF5F5EBD4), cardShape)
                .border(1.5.dp, Color(0xAA5C4033), cardShape)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("果冻勇者", color = Color(0xFF2C1810), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("水墨远征 · 本地账号", color = Color(0xFF5C4033), fontSize = 12.sp)
            Button(
                onClick = onGuestEnter,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4D7C0F)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("一键生成账号并进入", color = Color(0xFFF5EBD4), fontSize = 16.sp)
            }
            if (guestHint.isNotEmpty()) {
                Text(guestHint, color = Color(0xFF3F6212), fontSize = 12.sp)
            }
            Text("—— 或手动 ——", color = Color(0xFF78716C), fontSize = 11.sp)
            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color(0xFF2C1810),
                unfocusedTextColor = Color(0xFF3F3F46),
                focusedBorderColor = Color(0xFFB91C1C),
                unfocusedBorderColor = Color(0xFFA16207),
                cursorColor = Color(0xFFB45309),
                focusedLabelColor = Color(0xFFB45309),
                unfocusedLabelColor = Color(0xFF78716C)
            )
            OutlinedTextField(
                value = username,
                onValueChange = onUserChange,
                label = { Text("用户名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors
            )
            OutlinedTextField(
                value = password,
                onValueChange = onPassChange,
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors
            )
            if (message.isNotEmpty()) {
                Text(message, color = Color(0xFFB91C1C), fontSize = 13.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onAutoFill,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C4033)),
                    modifier = Modifier.weight(1f)
                ) { Text("填入随机账号", color = Color(0xFFF5EBD4), fontSize = 13.sp) }
                Button(
                    onClick = onSubmit,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isRegister) "注册进入" else "登录", color = Color(0xFFF5EBD4), fontSize = 14.sp)
                }
            }
            TextButton(onClick = onToggleMode) {
                Text(
                    text = if (isRegister) {
                        if (hasAccount) "已有账号？去登录" else "改为登录模式"
                    } else {
                        "没有账号？去注册"
                    },
                    color = Color(0xFF0F766E),
                    fontSize = 12.sp
                )
            }
            Text("账号仅本机保存 · 广告支持免费运营", color = Color(0xFF78716C), fontSize = 10.sp)
        }
    }
}
