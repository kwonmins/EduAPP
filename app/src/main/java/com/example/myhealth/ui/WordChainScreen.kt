package com.example.myhealth.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.myhealth.session.DailySessionViewModel
import com.example.myhealth.session.DirectDbRepository
import com.example.myhealth.session.SessionDataStore
import com.google.gson.Gson
import kotlinx.coroutines.launch
import kotlin.random.Random

data class Turn(val prompt: String, val answer: String, val ms: Long, val valid: Boolean)

@Composable
fun WordChainScreen(vm: DailySessionViewModel, onDone: () -> Unit) {
    // 사전 (중복 방지 위해 간단 리스트)
    val dict = remember {
        listOf(
            "기억","검사","사과","학교","오리","리본","바다","다리","이불","물고기","가방",
            "빙수","수박","강아지","자동차","문어","어항","한글","라면","노트","토끼","기사",
            "사자","자전거","거울","라디오","오징어","얼음","문화","하늘","라마","마차",
            "사진","지도","도로","로봇","트럭","크레용","연필","일기","기차","차표","표정"
        )
    }

    fun pickRandomStart(): String = dict[Random.nextInt(dict.size)]
    fun cpuPick(start: Char?, used: Set<String>): String? =
        if (start == null) null
        else dict.firstOrNull { it.first() == start && it !in used }

    // --- 상태들 ---
    val used = remember { mutableStateListOf<String>() }          // 사용된 단어(중복 방지)
    var prompt by remember { mutableStateOf(pickRandomStart()) }   // CPU 제시어
    var input by remember { mutableStateOf("") }                   // 사용자 답
    val turns = remember { mutableStateListOf<Turn>() }            // 진행 로그(사용자 1회마다 1턴)
    var t0 by remember { mutableStateOf(System.currentTimeMillis()) }
    val startedAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var saving by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 세션/DB
    val app = LocalContext.current.applicationContext as Application
    val sessionStore = remember { SessionDataStore(app) }
    val loginId by sessionStore.userIdFlow.collectAsState(initial = null)
    val dbRepo = remember { DirectDbRepository() }
    val gson = remember { Gson() }

    // 시작 시 현재 제시어 사용 처리
    LaunchedEffect(Unit) {
        used += prompt
        t0 = System.currentTimeMillis()
    }

    fun finishAndSave() {
        // 평균/정확 비율
        val avg = turns.map { it.ms }.ifEmpty { listOf(0L) }.average().toLong()
        val ratio = if (turns.isEmpty()) 0f else turns.count { it.valid }.toFloat() / turns.size.toFloat()
        vm.setWord(avgLatencyMs = avg, validRatio = ratio)

        val finishedAt = System.currentTimeMillis()
        val details = turns.mapIndexed { idx, t ->
            mapOf("turn_index" to idx, "prompt" to t.prompt, "answer" to t.answer, "ms" to t.ms, "valid" to t.valid)
        }

        saving = true
        scope.launch {
            val res = dbRepo.insertAppWord(
                loginId = loginId,
                rounds = turns.size,
                avgLatencyMs = avg,
                validRatio = ratio,
                totalMs = finishedAt - startedAt,
                startedMs = startedAt,
                finishedMs = finishedAt,
                detailsJson = gson.toJson(details)
            )
            saving = false
            snack.showSnackbar(res.fold(
                onSuccess = { "DB 저장 완료 (id=$it)" },
                onFailure = { "DB 저장 실패: ${it.localizedMessage ?: it.javaClass.simpleName}" }
            ))
            onDone()
        }
    }

    fun submit() {
        if (input.isBlank()) {
            scope.launch { snack.showSnackbar("단어를 입력하세요.") }
            return
        }

        val now = System.currentTimeMillis()
        val ok = input.first() == prompt.last()
        if (!ok) {
            scope.launch { snack.showSnackbar("❌ 제시어의 마지막 글자로 시작해야 해요!") }
            return
        }
        if (input in used) {
            scope.launch { snack.showSnackbar("이미 사용된 단어예요.") }
            return
        }

        // 턴 기록 (사용자 1회)
        turns += Turn(prompt = prompt, answer = input, ms = now - t0, valid = true)
        used += input

        // 5턴 끝나면 저장
        if (turns.size >= 5) {
            finishAndSave()
            return
        }

        // CPU의 다음 제시어 선택 (사용자 답의 마지막 글자)
        val next = cpuPick(input.lastOrNull(), used.toSet())
        if (next == null) {
            // 사용자가 이김 → 조기 종료
            scope.launch { snack.showSnackbar("🎉 CPU가 낼 단어가 없어요. 당신의 승리!") }
            finishAndSave()
            return
        }

        // 다음 라운드 준비
        prompt = next
        used += next
        input = ""
        t0 = System.currentTimeMillis()
    }

    // -------------------- UI --------------------
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snack) }
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("끝말잇기 (사용자 5턴)", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(14.dp))

            // 제시어 카드
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
                    Text("→ '${prompt.last()}' 로 시작하는 단어를 입력하세요", color = MaterialTheme.colorScheme.secondary)
                }
            }

            Spacer(Modifier.height(16.dp))

            // 입력 카드
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.trim() },
                        singleLine = true,
                        label = { Text("당신의 단어") },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = ::submit, enabled = !saving) {
                        Text(if (saving) "저장 중…" else "제출")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 진행/사용 단어
            LinearProgressIndicator(
                progress = (turns.size / 5f).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.surface,
            )
            Spacer(Modifier.height(8.dp))
            Text("진행: ${turns.size} / 5")

            if (used.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("사용된 단어", color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(used) { w ->
                        AssistChip(onClick = {}, label = { Text(w) })
                    }
                }
            }
        }
    }
}
