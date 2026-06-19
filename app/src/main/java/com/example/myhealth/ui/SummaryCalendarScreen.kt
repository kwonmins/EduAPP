@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.myhealth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.myhealth.session.LocalSessionRepository
import com.example.myhealth.session.LocalSessionRepository.DailySummaryRow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

@Composable
fun SummaryCalendarScreen() {
    val repo = remember { LocalSessionRepository() }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var ym by remember { mutableStateOf(YearMonth.now()) }
    var rows by remember { mutableStateOf(emptyList<DayCell>()) }
    var selected by remember { mutableStateOf<DayCell?>(null) }
    var score by remember { mutableStateOf(DaySummary()) }
    var loading by remember { mutableStateOf(false) }

    suspend fun recalculate(): DaySummary {
        val word = repo.getLastWord()
        val diary = repo.getLastDiary()
        val color = repo.getLastColoring()
        val wordScore = word?.let { scoreWordChain(it.avgLatencyMs, it.validRatio, it.rounds) } ?: 0
        val diaryScore = diary?.content?.let { scoreDiary(it) } ?: 0
        val colorScore = color?.score ?: 0
        val emotion = ((diaryScore * 0.55f) + (colorScore * 0.45f)).roundToInt().coerceIn(0, 100)
        val cognition = wordScore
        val memory = ((wordScore * 0.45f) + (diaryScore * 0.55f)).roundToInt().coerceIn(0, 100)
        val total = ((wordScore + diaryScore + colorScore) / 3f).roundToInt().coerceIn(0, 100)
        return DaySummary(total, wordScore, diaryScore, colorScore, emotion, cognition, memory)
    }

    suspend fun refreshMonth(selectToday: Boolean = false) {
        val monthRows = repo.getDailySummaries(ym.atDay(1), ym.atEndOfMonth())
        val built = buildMonthCells(ym, monthRows)
        rows = built
        if (selectToday) selected = built.firstOrNull { it.date == LocalDate.now() }
    }

    LaunchedEffect(Unit) {
        score = recalculate()
    }

    LaunchedEffect(ym) {
        refreshMonth()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text("오늘의 요약") },
                actions = {
                    IconButton(onClick = { ym = ym.minusMonths(1) }) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "이전 달")
                    }
                    Text("${ym.year}.${ym.monthValue}", modifier = Modifier.padding(horizontal = 6.dp))
                    IconButton(onClick = { ym = ym.plusMonths(1) }) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "다음 달")
                    }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("총점", style = MaterialTheme.typography.labelLarge)
                    Text(score.total.toString(), style = MaterialTheme.typography.displaySmall)
                    Text(summaryBadge(score.total), color = MaterialTheme.colorScheme.secondary)
                }
            }

            Spacer(Modifier.height(12.dp))
            ScoreRow("정서 상태", score.emotion)
            Spacer(Modifier.height(8.dp))
            ScoreRow("인지 상태", score.cognition)
            Spacer(Modifier.height(8.dp))
            ScoreRow("기억 상태", score.memory)
            Spacer(Modifier.height(12.dp))

            Button(
                enabled = !loading,
                onClick = {
                    scope.launch {
                        loading = true
                        score = recalculate()
                        val today = LocalDate.now()
                        repo.upsertDailySummary(
                            date = today,
                            total = score.total,
                            word = score.word,
                            diary = score.diary,
                            color = score.color,
                            emotion = score.emotion,
                            cognition = score.cognition,
                            memory = score.memory,
                            detailJson = null
                        )
                        ym = YearMonth.from(today)
                        refreshMonth(selectToday = true)
                        loading = false
                        snack.showSnackbar("오늘 요약을 저장했습니다.")
                    }
                }
            ) {
                Text(if (loading) "저장 중..." else "오늘 요약 저장")
            }

            Spacer(Modifier.height(18.dp))
            Divider()
            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("일", "월", "화", "수", "목", "금", "토").forEach {
                    Text(it, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(rows) { cell ->
                    DayBox(cell) { selected = cell.takeIf { it.summary != null } }
                }
            }

            selected?.summary?.let { summary ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(selected?.date.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text("총점 ${summary.total}점 · 끝말잇기 ${summary.word}점 · 일기 ${summary.diary}점 · 색칠 ${summary.color}점")
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreRow(title: String, score: Int) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { score.coerceIn(0, 100) / 100f },
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp),
                    color = totalColor(score)
                )
                Spacer(Modifier.width(12.dp))
                Text("${score}점", modifier = Modifier.width(56.dp), textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DayBox(cell: DayCell, onClick: () -> Unit) {
    val summary = cell.summary
    Column(
        modifier = Modifier
            .width(36.dp)
            .height(48.dp)
            .background(
                color = summary?.let { totalColor(it.total).copy(alpha = 0.15f) } ?: Color.Transparent,
                shape = MaterialTheme.shapes.small
            )
            .clickable(enabled = summary != null) { onClick() }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(cell.label, style = MaterialTheme.typography.labelLarge)
        if (summary != null) {
            Text(summary.total.toString(), color = totalColor(summary.total), fontWeight = FontWeight.Bold)
        } else {
            Spacer(Modifier.height(6.dp))
        }
    }
}

private data class DayCell(val date: LocalDate?, val label: String, val summary: DaySummary?)
private data class DaySummary(
    val total: Int = 0,
    val word: Int = 0,
    val diary: Int = 0,
    val color: Int = 0,
    val emotion: Int = 0,
    val cognition: Int = 0,
    val memory: Int = 0
)

private fun buildMonthCells(ym: YearMonth, summaries: List<DailySummaryRow>): List<DayCell> {
    val byDate = summaries.associateBy { it.date }
    val first = ym.atDay(1)
    val leading = first.dayOfWeek.value % 7
    val cells = mutableListOf<DayCell>()
    repeat(leading) { cells += DayCell(null, "", null) }
    for (day in 1..ym.lengthOfMonth()) {
        val date = ym.atDay(day)
        val row = byDate[date]
        cells += DayCell(
            date = date,
            label = day.toString(),
            summary = row?.let { DaySummary(it.total, it.word, it.diary, it.color, it.emotion, it.cognition, it.memory) }
        )
    }
    while (cells.size % 7 != 0) cells += DayCell(null, "", null)
    return cells
}

private fun scoreWordChain(avgLatencyMs: Int, validRatio: Float, roundsCompleted: Int): Int {
    val speed = ((3000f - avgLatencyMs) / 2500f).coerceIn(0f, 1f)
    val accuracy = validRatio.coerceIn(0f, 1f)
    val completion = (roundsCompleted / 5f).coerceIn(0f, 1f)
    return (100f * (0.5f * speed + 0.4f * accuracy + 0.1f * completion)).roundToInt().coerceIn(0, 100)
}

private fun scoreDiary(content: String): Int = when {
    content.length >= 80 -> 90
    content.length >= 40 -> 78
    content.length >= 15 -> 64
    else -> 45
}

private fun totalColor(score: Int) = when (score) {
    in 0..59 -> Color(0xFFE53935)
    in 60..79 -> Color(0xFFFFA000)
    else -> Color(0xFF43A047)
}

private fun summaryBadge(total: Int) = when {
    total >= 85 -> "아주 좋아요"
    total >= 70 -> "좋은 흐름이에요"
    total >= 55 -> "천천히 이어가면 좋아요"
    else -> "가볍게 다시 시작해 볼까요"
}
