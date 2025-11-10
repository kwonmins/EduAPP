package com.example.myhealth.ui

import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.myhealth.R
import com.example.myhealth.session.SessionDataStore
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginDone: () -> Unit
) {
    val app = LocalContext.current.applicationContext as Application
    val session = remember { SessionDataStore(app) }
    val scope = rememberCoroutineScope()

    var userId by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var pwVisible by rememberSaveable { mutableStateOf(false) }

    val canLogin = userId.isNotBlank() && password.isNotBlank()

    // 아이보리 배경
    val ivory = Color(0xFFFBF8F1)   // 필요하면 살짝 조절 가능
    val textPrimary = Color(0xFF1F2937)
    val textSecondary = Color(0xFF6B7280)

    Scaffold(containerColor = ivory) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ivory)
                .padding(inner)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))

            // 상단 중앙 히어로 이미지
            Image(
                painter = painterResource(R.drawable.login_hero),
                contentDescription = null,
                modifier = Modifier
                    .size(250.dp)
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "로그인",
                color = textPrimary,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "계정으로 로그인해 일일 미션과 만다라를 관리하세요.",
                color = textSecondary,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = userId,
                onValueChange = { userId = it.trim() },
                label = { Text("아이디") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("비밀번호") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                visualTransformation = if (pwVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { pwVisible = !pwVisible }) {
                        Icon(
                            imageVector = if (pwVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = "보기"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    scope.launch {
                        session.setUserId(userId)
                        onLoginDone()
                    }
                },
                enabled = canLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(18.dp)
            ) { Text("로그인", style = MaterialTheme.typography.titleMedium) }

            Spacer(Modifier.height(14.dp))

            // 안내 문구 교체
            Text(
                text = "웹에서 가입한 ID와 비밀번호를 입력해주세요",
                color = textSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
