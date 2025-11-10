@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.myhealth.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.myhealth.ai.OpenAiClient
import com.example.myhealth.session.DirectDbRepository
import com.example.myhealth.session.DirectDbRepository.DailySummaryRow
import com.example.myhealth.session.SessionDataStore
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

@Composable
fun SummaryCalendarScreen() {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as Application
    val session = remember { SessionDataStore(app) }
    val loginId by session.userIdFlow.collectAsState(initial = null)
    val repo = remember { DirectDbRepository() }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // ===== 요약 점수 상태 =====
    var emotion by remember { mutableStateOf<Int?>(null) }
    var cognition by remember { mutableStateOf<Int?>(null) }
    var memory by remember { mutableStateOf<Int?>(null) }
    var total by remember { mutableStateOf<Int?>(null) }
    var wordScore by remember { mutableStateOf(0) }
    var diaryScore by remember { mutableStateOf(0) }
    var colorScore by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }

    // ===== 달력 상태 =====
    var ym by remember { mutableStateOf(YearMonth.now()) }
    var rows by remember { mutableStateOf(emptyList<DayCell>()) }
    var selected by remember { mutableStateOf<DayCell?>(null) }

    fun refreshMonth(selectToday: Boolean = false) {
        scope.launch {
            val start = ym.atDay(1)
            val end = ym.atEndOfMonth()
            val list = repo.getDailySummaries(loginId, start, end)
            val built = buildMonthCells(ym, list)
            rows = built
            if (selectToday) {
                val today = LocalDate.now()
                selected = built.firstOrNull { it.date == today }
            }
        }
    }

    // ===== 요약 계산 =====
    LaunchedEffect(loginId) {
        loading = true
        try {
            val word = repo.getLastWord(loginId)
            val diary = repo.getLastDiary(loginId)
            val color = repo.getLastColoring(loginId)

            // 끝말잇기 점수
            wordScore = word?.let { scoreWordChain(it.avgLatencyMs, it.validRatio, it.rounds) } ?: 0

            // Pair에 타입 먼저 지정 후 구조분해
            val result: Pair<Int, DiaryQual> =
                if (diary != null && diary.content.isNotBlank()) {
                    scoreDiary(diary.title, diary.content)
                } else {
                    Pair(0, DiaryQual(0f, 0f, 0f, 0f, null))
                }
            val (dScore, dq) = result
            diaryScore = dScore

            // 색칠 점수
            colorScore = color?.score ?: 0

            // 파생 지표
            val emotionPct = (0.6f * ((dq.warmth + dq.positivity) / 2f) + 0.4f * (colorScore / 100f))
                .coerceIn(0f, 1f)
            val cognitionPct = (0.7f * (wordScore / 100f) + 0.3f * dq.detail)
                .coerceIn(0f, 1f)
            val memoryPct = (0.5f * (word?.validRatio ?: 0f) + 0.5f * dq.detail)
                .coerceIn(0f, 1f)

            emotion = (emotionPct * 100f).roundToInt()
            cognition = (cognitionPct * 100f).roundToInt()
            memory = (memoryPct * 100f).roundToInt()

            val base = 0.35f * diaryScore + 0.35f * colorScore + 0.30f * wordScore
            val warmSynergy = dq.warmth.coerceAtMost(0.5f) * 6f
            val calmSynergy = dq.calmness.coerceAtMost(0.7f) * 4f
            total = (base + warmSynergy + calmSynergy).roundToInt().coerceIn(0, 100)
        } catch (e: Throwable) {
            snack.showSnackbar("요약 계산 실패: ${e.localizedMessage}")
        } finally {
            loading = false
        }
    }

    // 달력 초기 로드
    LaunchedEffect(ym, loginId) { refreshMonth() }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text("오늘의 요약") },
                actions = {
                    IconButton(onClick = { ym = ym.minusMonths(1) }) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = null)
                    }
                    Text("${ym.year}.${ym.monthValue}", modifier = Modifier.padding(horizontal = 6.dp))
                    IconButton(onClick = { ym = ym.plusMonths(1) }) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = null)
                    }
                }
            )
        }
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ===== 요약 카드 =====
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("총 점수", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Text((total ?: 0).toString(), style = MaterialTheme.typography.displaySmall)
                    Text(summaryBadge(total ?: 0))
                }
            }

            Spacer(Modifier.height(12.dp))
            ScoreRow("정서 상태", emotion ?: 0)
            Spacer(Modifier.height(8.dp))
            ScoreRow("인지 상태", cognition ?: 0)
            Spacer(Modifier.height(8.dp))
            ScoreRow("기억 상태", memory ?: 0)
            Spacer(Modifier.height(12.dp))

            Button(
                enabled = !loading,
                onClick = {
                    scope.launch {
                        loading = true
                        try {
                            val today = LocalDate.now()
                            val r = repo.upsertDailySummary(
                                loginId = loginId,
                                date = today,
                                total = total ?: 0,
                                word = wordScore,
                                diary = diaryScore,
                                color = colorScore,
                                emotion = emotion ?: 0,
                                cognition = cognition ?: 0,
                                memory = memory ?: 0,
                                detailJson = null
                            )
                            if (r.isFailure) {
                                snack.showSnackbar("저장 실패(달력은 갱신): ${r.exceptionOrNull()?.localizedMessage}")
                            }
                            if (YearMonth.from(today) == ym) {
                                refreshMonth(selectToday = true)
                            } else {
                                ym = YearMonth.from(today)
                                refreshMonth(selectToday = true)
                            }
                        } finally {
                            loading = false
                        }
                    }
                }
            ) { Text("적용으로") }

            Spacer(Modifier.height(18.dp))
            Divider()
            Spacer(Modifier.height(8.dp))

            // ===== 달력 =====
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("일","월","화","수","목","금","토").forEach {
                    Text(it, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(rows) { cell ->
                    DayBox(cell) {
                        selected = cell.takeIf { it.date != null && it.summary != null }
                    }
                }
            }

            // 선택한 날 상세
            if (selected?.summary != null) {
                val s = selected!!.summary!!
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("${selected!!.date}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        BreakdownRow("정서", s.emotion)
                        Spacer(Modifier.height(6.dp))
                        BreakdownRow("인지", s.cognition)
                        Spacer(Modifier.height(6.dp))
                        BreakdownRow("기억", s.memory)
                        Spacer(Modifier.height(8.dp))
                        Divider()
                        Spacer(Modifier.height(8.dp))
                        SmallRow("끝말잇기", s.word)
                        SmallRow("일기", s.diary)
                        SmallRow("색칠", s.color)
                        Spacer(Modifier.height(6.dp))
                        Text("총점: ${s.total}점", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/* ================== 공용 UI/로직 ================== */

@Composable
private fun ScoreRow(title: String, score: Int) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = (score.coerceIn(0, 100) / 100f),
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    color = when (score) {
                        in 0..59 -> Color(0xFFE53935)
                        in 60..79 -> Color(0xFFFFA000)
                        else -> Color(0xFF43A047)
                    }
                )
                Spacer(Modifier.width(12.dp))
                Text("${score}점", textAlign = TextAlign.End, modifier = Modifier.width(56.dp), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DayBox(cell: DayCell, onClick: () -> Unit) {
    val bg = if (cell.summary != null) totalColor(cell.summary.total) else Color.Transparent
    val alpha = if (cell.summary != null) 0.15f else 0f
    Column(
        modifier = Modifier
            .width(36.dp)
            .height(48.dp)
            .background(bg.copy(alpha = alpha), shape = MaterialTheme.shapes.small)
            .clickable(enabled = cell.summary != null) { onClick() }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(cell.label, style = MaterialTheme.typography.labelLarge)
        if (cell.summary != null) {
            Text(
                "${cell.summary.total}",
                style = MaterialTheme.typography.labelSmall,
                color = totalColor(cell.summary.total),
                fontWeight = FontWeight.Bold
            )
        } else {
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun BreakdownRow(title: String, score: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.width(56.dp))
        LinearProgressIndicator(
            progress = score.coerceIn(0, 100) / 100f,
            modifier = Modifier
                .weight(1f)
                .height(10.dp),
            color = totalColor(score),
            trackColor = MaterialTheme.colorScheme.surface
        )
        Spacer(Modifier.width(8.dp))
        Text("${score}점", fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SmallRow(title: String, score: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.width(64.dp))
        Spacer(Modifier.width(6.dp))
        Text("${score}점", fontWeight = FontWeight.Medium, color = totalColor(score))
    }
}

private fun totalColor(score: Int) = when (score) {
    in 0..59 -> Color(0xFFE53935)
    in 60..79 -> Color(0xFFFFA000)
    else -> Color(0xFF43A047)
}

private data class DayCell(val date: LocalDate?, val label: String, val summary: DaySummary?)
private data class DaySummary(
    val total: Int, val word: Int, val diary: Int, val color: Int,
    val emotion: Int, val cognition: Int, val memory: Int
)

private fun buildMonthCells(ym: YearMonth, rows: List<DailySummaryRow>): List<DayCell> {
    val map = rows.associateBy { it.date }
    val first = ym.atDay(1)
    val leading = (first.dayOfWeek.value) % 7 // Mon=1..Sun=7 → Sun=0
    val totalDays = ym.lengthOfMonth()
    val list = mutableListOf<DayCell>()
    repeat(leading) { list += DayCell(null, "", null) }
    for (d in 1..totalDays) {
        val date = ym.atDay(d)
        val row = map[date]
        val summary = row?.let {
            DaySummary(
                total = it.total, word = it.word, diary = it.diary, color = it.color,
                emotion = it.emotion, cognition = it.cognition, memory = it.memory
            )
        }
        list += DayCell(date, d.toString(), summary)
    }
    while (list.size % 7 != 0) list += DayCell(null, "", null)
    return list
}

/* ---- 점수 계산 로직 ---- */

private fun scoreWordChain(
    avgLatencyMs: Int,
    validRatio: Float,
    roundsCompleted: Int,
    targetRounds: Int = 5
): Int {
    val speed = ((3000f - avgLatencyMs) / (3000f - 500f)).coerceIn(0f, 1f)
    val accuracy = validRatio.coerceIn(0f, 1f)
    val completion = (roundsCompleted.toFloat() / targetRounds).coerceIn(0f, 1f)
    return (100f * (0.5f * speed + 0.4f * accuracy + 0.1f * completion)).toInt().coerceIn(0, 100)
}

private fun summaryBadge(total: Int) = when {
    total >= 90 -> "매우 훌륭해요 🌟"
    total >= 75 -> "아주 좋아요 🙂"
    total >= 60 -> "좋은 하루였어요 😊"
    else -> "차분히 한 걸음씩 🚶"
}

/* ---------- 일기 분석(공유 유틸) ---------- */

data class DiaryQual(
    val warmth: Float,
    val positivity: Float,
    val detail: Float,
    val calmness: Float,
    val mood: String?
)

private fun parseDiaryQual(json: String) = DiaryQual(
    Regex(""""warmth"\s*:\s*(\d(\.\d+)?)""").find(json)?.groupValues?.get(1)?.toFloat() ?: 0f,
    Regex(""""positivity"\s*:\s*(\d(\.\d+)?)""").find(json)?.groupValues?.get(1)?.toFloat() ?: 0f,
    Regex(""""detail"\s*:\s*(\d(\.\d+)?)""").find(json)?.groupValues?.get(1)?.toFloat() ?: 0f,
    Regex(""""calmness"\s*:\s*(\d(\.\d+)?)""").find(json)?.groupValues?.get(1)?.toFloat() ?: 0f,
    Regex(""""mood"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1)
)

private suspend fun scoreDiary(title: String, content: String): Pair<Int, DiaryQual> {
    val json = OpenAiClient.analyzeDiaryQualities(title, content)
    val q = parseDiaryQual(json)
    val base = 100f * (0.4f * q.warmth + 0.3f * q.positivity + 0.2f * q.detail + 0.1f * q.calmness)
    val len = content.length
    val penalty = when {
        len < 20 -> -20
        len < 40 -> -10
        else -> 0
    }
    val score = ((base + penalty).coerceIn(0f, 100f)).toInt()
    return Pair(score, q)
}
