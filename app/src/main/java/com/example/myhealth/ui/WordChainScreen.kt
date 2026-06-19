package com.example.myhealth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.myhealth.session.DailySessionViewModel
import com.example.myhealth.session.LocalSessionRepository
import com.google.gson.Gson
import kotlinx.coroutines.launch
import kotlin.random.Random

data class Turn(val prompt: String, val answer: String, val ms: Long, val valid: Boolean)

@Composable
fun WordChainScreen(vm: DailySessionViewModel, onDone: () -> Unit) {
    val dict = remember {
        listOf(
            "사과", "과자", "자동차", "차표", "표정", "정원", "원숭이", "이불", "불꽃", "꽃병",
            "병원", "원두", "두부", "부채", "채소", "소나무", "무지개", "개나리", "리본", "본능",
            "능력", "역사", "사진", "진주", "주전자", "자전거", "거울", "울타리", "리듬", "음악",
            "학교", "교실", "실내", "내일", "일기", "기차", "차고", "고양이", "이야기", "기억"
        )
    }

    fun pickRandomStart(): String = dict[Random.nextInt(dict.size)]
    fun cpuPick(start: Char?, used: Set<String>): String? =
        start?.let { ch -> dict.firstOrNull { it.first() == ch && it !in used } }

    val used = remember { mutableStateListOf<String>() }
    var prompt by remember { mutableStateOf(pickRandomStart()) }
    var input by remember { mutableStateOf("") }
    val turns = remember { mutableStateListOf<Turn>() }
    var turnStartedAt by remember { mutableStateOf(System.currentTimeMillis()) }
    val gameStartedAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var saving by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val repo = remember { LocalSessionRepository() }
    val gson = remember { Gson() }

    LaunchedEffect(Unit) {
        used += prompt
        turnStartedAt = System.currentTimeMillis()
    }

    fun finishAndSave() {
        val avg = turns.map { it.ms }.ifEmpty { listOf(0L) }.average().toLong()
        val ratio = if (turns.isEmpty()) 0f else turns.count { it.valid }.toFloat() / turns.size
        val finishedAt = System.currentTimeMillis()
        val details = turns.mapIndexed { index, turn ->
            mapOf(
                "turn_index" to index,
                "prompt" to turn.prompt,
                "answer" to turn.answer,
                "ms" to turn.ms,
                "valid" to turn.valid
            )
        }

        vm.setWord(avgLatencyMs = avg, validRatio = ratio)
        saving = true
        scope.launch {
            repo.insertAppWord(
                rounds = turns.size,
                avgLatencyMs = avg,
                validRatio = ratio,
                totalMs = finishedAt - gameStartedAt,
                startedMs = gameStartedAt,
                finishedMs = finishedAt,
                detailsJson = gson.toJson(details)
            )
            saving = false
            onDone()
        }
    }

    fun submit() {
        val answer = input.trim()
        if (answer.isBlank()) {
            scope.launch { snack.showSnackbar("단어를 입력해 주세요.") }
            return
        }
        if (answer.first() != prompt.last()) {
            scope.launch { snack.showSnackbar("'${prompt.last()}'로 시작하는 단어를 입력해야 해요.") }
            return
        }
        if (answer in used) {
            scope.launch { snack.showSnackbar("이미 사용한 단어예요.") }
            return
        }

        val now = System.currentTimeMillis()
        turns += Turn(prompt = prompt, answer = answer, ms = now - turnStartedAt, valid = true)
        used += answer

        if (turns.size >= 5) {
            finishAndSave()
            return
        }

        val next = cpuPick(answer.lastOrNull(), used.toSet())
        if (next == null) {
            scope.launch { snack.showSnackbar("이어갈 단어가 없어 여기서 마무리할게요.") }
            finishAndSave()
            return
        }

        prompt = next
        used += next
        input = ""
        turnStartedAt = System.currentTimeMillis()
    }

    Scaffold(snackbarHost = { SnackbarHost(hostState = snack) }) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("끝말잇기", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(14.dp))

            Card(
                colors = CardDefaults.cardColors(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("제시어", color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        prompt,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("'${prompt.last()}'로 시작하는 단어를 입력해 주세요.")
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        label = { Text("내 단어") },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = ::submit, enabled = !saving) {
                        Text(if (saving) "저장 중..." else "제출")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { (turns.size / 5f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text("진행: ${turns.size} / 5")

            if (used.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("사용한 단어", color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(used) { word ->
                        AssistChip(onClick = {}, label = { Text(word) })
                    }
                }
            }
        }
    }
}
