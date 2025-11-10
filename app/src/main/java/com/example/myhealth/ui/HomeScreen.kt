package com.example.myhealth.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onStart: () -> Unit,
    onLogout: () -> Unit     // ← 추가
) {
    Scaffold { inner ->
        Column(
            Modifier
                .padding(inner)
                .padding(24.dp)
        ) {
            // 상단바 역할의 행
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("끝말잇기", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onLogout) { Text("로그아웃") }
            }

            Spacer(Modifier.height(12.dp))

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
                Column(Modifier.padding(20.dp)) {
                    Text("간단 규칙", color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(8.dp))
                    Text("CPU가 제시어를 내면 마지막 글자로 이어지는 단어를 5번 입력해 보세요.")
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onStart) { Text("시작하기") }
                }
            }
        }
    }
}
