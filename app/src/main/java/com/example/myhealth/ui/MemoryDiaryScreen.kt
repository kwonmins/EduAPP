package com.example.myhealth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myhealth.ai.OpenAiClient
import com.example.myhealth.session.LocalSessionRepository
import kotlinx.coroutines.launch

@Composable
fun MemoryDiaryScreen(onDone: () -> Unit) {
    val repo = remember { LocalSessionRepository() }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("오늘의 기억") }
    var content by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    fun saveAndNext() {
        scope.launch {
            saving = true
            val diaryJson = OpenAiClient.makeDiaryJson("{}", content)
            val finalContent = extractJsonField(diaryJson, "content") ?: content
            repo.insertDiary(
                title = title.ifBlank { "오늘의 기억" },
                content = finalContent.ifBlank { "오늘 떠오른 기억을 짧게 기록했습니다." },
                photoBase64 = null,
                analysisJson = null,
                sttText = content,
                recordedSec = 0
            )
            saving = false
            onDone()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snack) }) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Text("추억 일기", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("오늘 떠오른 장면을 편하게 적어 주세요.", color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("제목") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("기억 내용") },
                        minLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f)) {
                    Text("건너뛰기")
                }
                Button(
                    onClick = {
                        if (content.isBlank()) {
                            scope.launch { snack.showSnackbar("일기 내용을 입력해 주세요.") }
                        } else {
                            saveAndNext()
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (saving) "저장 중..." else "저장하고 다음")
                }
            }
        }
    }
}

private fun extractJsonField(json: String, key: String): String? =
    Regex(""""$key"\s*:\s*"([^"]*)"""").find(json)?.groupValues?.getOrNull(1)
