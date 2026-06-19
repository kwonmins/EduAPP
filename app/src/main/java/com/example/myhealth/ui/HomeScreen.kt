package com.example.myhealth.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onStart: () -> Unit
) {
    Scaffold { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .padding(24.dp)
        ) {
            Text(
                text = "EduAPP",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "오늘의 인지·정서 활동을 시작해 보세요.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors()
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("진행 순서", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    Text("끝말잇기 → 추억 일기 → 만다라 색칠 → 오늘의 요약")
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onStart) {
                        Text("시작하기")
                    }
                }
            }
        }
    }
}
