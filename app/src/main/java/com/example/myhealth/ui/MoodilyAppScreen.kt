@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.myhealth.ui

import android.media.MediaPlayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myhealth.R
import com.example.myhealth.ai.OpenAiClient
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class MoodilyTab { Home, Write, Archive }
private enum class MoodilyStep { Onboarding, Mood, Diary, Reply, Card }

private data class MoodOption(
    val label: String,
    val tone: String,
    val color: Color,
    val face: String
)

private data class DiaryEntry(
    val date: LocalDate,
    val mood: MoodOption,
    val text: String,
    val reply: String,
    val comfortLine: String
)

private val MoodOptions = listOf(
    MoodOption("행복함", "작게 반짝인 마음", Color(0xFFF6D77A), "•‿•"),
    MoodOption("평온함", "고요히 내려앉은 마음", Color(0xFF9BD8D0), "˘‿˘"),
    MoodOption("지침", "조금 쉬고 싶은 마음", Color(0xFF99BBD9), "–_–"),
    MoodOption("우울함", "무겁게 젖은 마음", Color(0xFFAEB6BE), "._."),
    MoodOption("설렘", "몽글몽글 피어난 마음", Color(0xFFF1A9AD), "♡‿♡"),
    MoodOption("무기력", "기운을 아껴야 하는 마음", Color(0xFFD4CEC3), "…")
)

@Composable
fun MoodilyAppScreen() {
    var tab by remember { mutableStateOf(MoodilyTab.Home) }
    var step by remember { mutableStateOf(MoodilyStep.Onboarding) }
    var selectedMood by remember { mutableStateOf<MoodOption?>(null) }
    var diaryText by remember { mutableStateOf("") }
    var aiReply by remember { mutableStateOf("") }
    var comfortLine by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    val entries = remember { mutableStateListOf<DiaryEntry>() }
    val scope = rememberCoroutineScope()
    val bgmResId = remember(tab, selectedMood) { bgmFor(tab, selectedMood) }

    MoodilyBgmPlayer(trackResId = bgmResId, enabled = true)

    fun resetWriteFlow() {
        tab = MoodilyTab.Write
        step = MoodilyStep.Mood
        selectedMood = null
        diaryText = ""
        aiReply = ""
        comfortLine = ""
    }

    fun generateReply() {
        val mood = selectedMood ?: return
        scope.launch {
            isGenerating = true
            aiReply = OpenAiClient.makeEmpathyReply(mood.label, diaryText)
            comfortLine = makeComfortLine(mood, diaryText)
            entries.removeAll { it.date == LocalDate.now() }
            entries.add(
                0,
                DiaryEntry(
                    date = LocalDate.now(),
                    mood = mood,
                    text = diaryText,
                    reply = aiReply,
                    comfortLine = comfortLine
                )
            )
            isGenerating = false
            step = MoodilyStep.Reply
        }
    }

    Scaffold(
        topBar = {
            MoodilyTopBar(
                step = step,
                tab = tab,
                onBack = {
                    when {
                        tab != MoodilyTab.Write -> tab = MoodilyTab.Home
                        step == MoodilyStep.Diary -> step = MoodilyStep.Mood
                        step == MoodilyStep.Reply -> step = MoodilyStep.Diary
                        step == MoodilyStep.Card -> step = MoodilyStep.Reply
                        else -> tab = MoodilyTab.Home
                    }
                }
            )
        },
        bottomBar = {
            MoodilyNavigation(tab = tab) { next ->
                tab = next
                if (next == MoodilyTab.Write && step == MoodilyStep.Onboarding) {
                    step = MoodilyStep.Mood
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { inner ->
        Box(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
        ) {
            when (tab) {
                MoodilyTab.Home -> HomeExperience(
                    entries = entries,
                    onStart = { resetWriteFlow() },
                    onArchive = { tab = MoodilyTab.Archive }
                )

                MoodilyTab.Write -> when (step) {
                    MoodilyStep.Onboarding -> OnboardingScreen(onStart = { step = MoodilyStep.Mood })
                    MoodilyStep.Mood -> MoodSelectScreen(
                        selectedMood = selectedMood,
                        onSelect = { selectedMood = it },
                        onNext = { if (selectedMood != null) step = MoodilyStep.Diary }
                    )
                    MoodilyStep.Diary -> DiaryWriteScreen(
                        mood = selectedMood,
                        text = diaryText,
                        onTextChange = { diaryText = it },
                        isGenerating = isGenerating,
                        onGenerate = { if (diaryText.isNotBlank()) generateReply() }
                    )
                    MoodilyStep.Reply -> AiReplyScreen(
                        mood = selectedMood,
                        diary = diaryText,
                        reply = aiReply,
                        comfortLine = comfortLine,
                        onCard = { step = MoodilyStep.Card }
                    )
                    MoodilyStep.Card -> EmotionalCardScreen(
                        mood = selectedMood,
                        comfortLine = comfortLine,
                        onArchive = { tab = MoodilyTab.Archive }
                    )
                }

                MoodilyTab.Archive -> ArchiveScreen(entries = entries)
            }
        }
    }
}

@Composable
private fun MoodilyTopBar(step: MoodilyStep, tab: MoodilyTab, onBack: () -> Unit) {
    val canGoBack = tab != MoodilyTab.Home || step != MoodilyStep.Onboarding
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = "Moodily",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        navigationIcon = {
            if (canGoBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로")
                }
            }
        }
    )
}

@Composable
private fun MoodilyNavigation(tab: MoodilyTab, onSelect: (MoodilyTab) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NavItem(tab, MoodilyTab.Home, Icons.Filled.Home, "홈", onSelect, Modifier.weight(1f))
            NavItem(tab, MoodilyTab.Write, Icons.Filled.Edit, "기록", onSelect, Modifier.weight(1f))
            NavItem(tab, MoodilyTab.Archive, Icons.Filled.CalendarMonth, "캘린더", onSelect, Modifier.weight(1f))
        }
    }
}

@Composable
private fun NavItem(
    selected: MoodilyTab,
    item: MoodilyTab,
    icon: ImageVector,
    label: String,
    onSelect: (MoodilyTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = selected == item
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable { onSelect(item) }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HomeExperience(
    entries: List<DiaryEntry>,
    onStart: () -> Unit,
    onArchive: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HeroPanel(onStart = onStart)
        }
        item {
            TodayPreview(entry = entries.firstOrNull(), onArchive = onArchive)
        }
        item {
            MoodPaletteStrip()
        }
    }
}

@Composable
private fun HeroPanel(onStart: () -> Unit) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MoaMascot(
                modifier = Modifier.size(132.dp),
                moodColor = Color(0xFFB8B0DD),
                sleepy = false
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "오늘의 마음을\n조용히 들어드릴게요.",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 31.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Moa가 하루 끝에 남은 감정을 다정하게 정리해줘요.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onStart,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Filled.NightsStay, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("오늘 마음 적어보기")
            }
        }
    }
}

@Composable
private fun TodayPreview(entry: DiaryEntry?, onArchive: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9F5))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MoaMascot(
                modifier = Modifier.size(58.dp),
                moodColor = entry?.mood?.color ?: Color(0xFFCEC7E9),
                sleepy = entry?.mood?.label == "지침" || entry?.mood?.label == "우울함"
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry?.comfortLine ?: "아직 오늘의 마음을 적지 않았어요.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry?.let { "${it.date.format(DateTimeFormatter.ofPattern("M월 d일"))} · ${it.mood.label}" }
                        ?: "짧아도 괜찮아요. 한 문장만 적어도 충분해요.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            FilledTonalButton(onClick = onArchive) {
                Text("보기")
            }
        }
    }
}

@Composable
private fun MoodPaletteStrip() {
    Column {
        Text("오늘 마음은 어떤 색인가요?", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MoodOptions.forEach { mood ->
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(mood.color)
                        .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                )
            }
        }
    }
}

@Composable
private fun OnboardingScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        MoaMascot(modifier = Modifier.size(156.dp), moodColor = Color(0xFFB8B0DD))
        Spacer(Modifier.height(20.dp))
        Text(
            "오늘의 마음을\n조용히 들어드릴게요.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 36.sp
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "하루 끝에 남은 말들을 Moa에게 천천히 건네주세요.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text("시작하기")
        }
    }
}

@Composable
private fun MoodSelectScreen(
    selectedMood: MoodOption?,
    onSelect: (MoodOption) -> Unit,
    onNext: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("오늘 마음은 어떤 색인가요?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("딱 맞는 감정이 없어도 괜찮아요. 가장 가까운 색을 골라주세요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(360.dp)
            ) {
                items(MoodOptions) { mood ->
                    MoodCard(
                        mood = mood,
                        selected = selectedMood == mood,
                        onClick = { onSelect(mood) }
                    )
                }
            }
        }
        item {
            MoaMessage(text = selectedMood?.let { "오늘은 ${it.tone}이네요. 그 마음을 조금만 더 들려줄래요?" }
                ?: "Moa가 여기서 기다리고 있을게요.")
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onNext,
                enabled = selectedMood != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("오늘 마음 적어보기")
            }
        }
    }
}

@Composable
private fun MoodCard(mood: MoodOption, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.15f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) mood.color.copy(alpha = 0.34f) else MaterialTheme.colorScheme.surface
        ),
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, mood.color) else null
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            MoodFace(mood)
            Spacer(Modifier.height(10.dp))
            Text(mood.label, fontWeight = FontWeight.Bold)
            Text(mood.tone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun MoodFace(mood: MoodOption) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(mood.color.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Text(mood.face, fontWeight = FontWeight.Bold, color = Color(0xFF514845))
    }
}

@Composable
private fun DiaryWriteScreen(
    mood: MoodOption?,
    text: String,
    onTextChange: (String) -> Unit,
    isGenerating: Boolean,
    onGenerate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        MoaMessage(text = "${mood?.label ?: "오늘"} 마음이 머문 장면을 자유롭게 적어보세요.")
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            minLines = 12,
            placeholder = { Text("오늘, 마음에 남은 장면이 있었나요?\n누군가에게 말하지 못한 마음도 괜찮아요.") },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(22.dp)
        )
        Spacer(Modifier.height(12.dp))
        if (isGenerating) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
        }
        Button(
            onClick = onGenerate,
            enabled = text.isNotBlank() && !isGenerating,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Send, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (isGenerating) "Moa가 읽는 중..." else "Moa에게 보내기")
        }
    }
}

@Composable
private fun AiReplyScreen(
    mood: MoodOption?,
    diary: String,
    reply: String,
    comfortLine: String,
    onCard: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("AI 공감 답장", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item {
            DiaryBubble(text = diary, alignEnd = true, color = MaterialTheme.colorScheme.surface)
        }
        item {
            Row(verticalAlignment = Alignment.Top) {
                MoaMascot(modifier = Modifier.size(58.dp), moodColor = mood?.color ?: Color(0xFFCEC7E9))
                Spacer(Modifier.width(10.dp))
                DiaryBubble(text = reply, alignEnd = false, color = Color(0xFFF7EEF0), modifier = Modifier.weight(1f))
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9F5))) {
                Column(Modifier.padding(16.dp)) {
                    Text("오늘의 위로 문장", color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(6.dp))
                    Text(comfortLine, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Button(onClick = onCard, modifier = Modifier.fillMaxWidth()) {
                Text("감성 카드로 간직하기")
            }
        }
    }
}

@Composable
private fun DiaryBubble(text: String, alignEnd: Boolean, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            color = color,
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(if (alignEnd) 0.86f else 1f)
        ) {
            Text(text, modifier = Modifier.padding(16.dp), lineHeight = 22.sp)
        }
    }
}

@Composable
private fun EmotionalCardScreen(
    mood: MoodOption?,
    comfortLine: String,
    onArchive: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("하루 마무리 카드", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f),
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9F2))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                mood?.color?.copy(alpha = 0.26f) ?: Color(0xFFE3DFF4),
                                Color(0xFFFFFCF7)
                            )
                        )
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                MoaMascot(modifier = Modifier.size(138.dp), moodColor = mood?.color ?: Color(0xFFCEC7E9))
                Spacer(Modifier.height(20.dp))
                Text(mood?.label ?: "오늘의 마음", color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(8.dp))
                Text(
                    comfortLine,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 30.sp
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Button(onClick = onArchive, modifier = Modifier.fillMaxWidth()) {
            Text("캘린더에서 보기")
        }
    }
}

@Composable
private fun ArchiveScreen(entries: List<DiaryEntry>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("감정 아카이브", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("날짜마다 남은 마음의 색을 조용히 모아둘게요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            CalendarPreview(entries = entries)
        }
        if (entries.isEmpty()) {
            item {
                MoaMessage("아직 기록이 없어요. 오늘의 마음 한 조각부터 남겨볼까요?")
            }
        } else {
            lazyItems(entries) { entry ->
                ArchiveEntryCard(entry)
            }
        }
    }
}

@Composable
private fun CalendarPreview(entries: List<DiaryEntry>) {
    val today = LocalDate.now()
    val days = (1..31).toList()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${today.monthValue}월", fontWeight = FontWeight.Bold)
                Text("감정 캘린더", color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(214.dp)
            ) {
                items(days) { day ->
                    val entry = entries.firstOrNull { it.date.dayOfMonth == day }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(entry?.mood?.color ?: Color(0xFFE9E2DA))
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(day.toString(), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveEntryCard(entry: DiaryEntry) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9F5))) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoodFace(entry.mood)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(entry.date.format(DateTimeFormatter.ofPattern("M월 d일")), fontWeight = FontWeight.Bold)
                    Text(entry.mood.label, color = MaterialTheme.colorScheme.secondary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(entry.comfortLine, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(entry.reply, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 21.sp)
        }
    }
}

@Composable
private fun MoaMessage(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9F5)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            MoaMascot(modifier = Modifier.size(52.dp), moodColor = Color(0xFFCEC7E9))
            Spacer(Modifier.width(12.dp))
            Text(text, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp)
        }
    }
}

@Composable
private fun MoaMascot(
    modifier: Modifier = Modifier,
    moodColor: Color,
    sleepy: Boolean = false
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(moodColor.copy(alpha = 0.58f), Color.Transparent),
                center = Offset(w * 0.52f, h * 0.48f),
                radius = w * 0.66f
            ),
            radius = w * 0.62f,
            center = Offset(w * 0.5f, h * 0.5f)
        )

        drawCircle(Color(0xFFFFFCF7), radius = w * 0.22f, center = Offset(w * 0.34f, h * 0.52f))
        drawCircle(Color(0xFFFFFCF7), radius = w * 0.28f, center = Offset(w * 0.52f, h * 0.43f))
        drawCircle(Color(0xFFFFFCF7), radius = w * 0.22f, center = Offset(w * 0.68f, h * 0.53f))
        drawRoundRect(
            color = Color(0xFFFFFCF7),
            topLeft = Offset(w * 0.25f, h * 0.47f),
            size = Size(w * 0.52f, h * 0.24f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.14f, w * 0.14f)
        )

        val eyeY = h * 0.49f
        if (sleepy) {
            drawLine(Color(0xFF594D49), Offset(w * 0.42f, eyeY), Offset(w * 0.47f, eyeY), strokeWidth = 3f, cap = StrokeCap.Round)
            drawLine(Color(0xFF594D49), Offset(w * 0.59f, eyeY), Offset(w * 0.64f, eyeY), strokeWidth = 3f, cap = StrokeCap.Round)
        } else {
            drawCircle(Color(0xFF594D49), radius = w * 0.018f, center = Offset(w * 0.44f, eyeY))
            drawCircle(Color(0xFF594D49), radius = w * 0.018f, center = Offset(w * 0.62f, eyeY))
        }
        drawArc(
            color = Color(0xFFB67A80),
            startAngle = 15f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = Offset(w * 0.48f, h * 0.49f),
            size = Size(w * 0.10f, h * 0.08f),
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )
        drawCircle(Color(0xFFF2A5AA).copy(alpha = 0.42f), radius = w * 0.035f, center = Offset(w * 0.36f, h * 0.56f))
        drawCircle(Color(0xFFF2A5AA).copy(alpha = 0.42f), radius = w * 0.035f, center = Offset(w * 0.70f, h * 0.56f))

        val star = Path().apply {
            moveTo(w * 0.78f, h * 0.24f)
            lineTo(w * 0.81f, h * 0.31f)
            lineTo(w * 0.88f, h * 0.34f)
            lineTo(w * 0.81f, h * 0.37f)
            lineTo(w * 0.78f, h * 0.44f)
            lineTo(w * 0.75f, h * 0.37f)
            lineTo(w * 0.68f, h * 0.34f)
            lineTo(w * 0.75f, h * 0.31f)
            close()
        }
        drawPath(star, Color(0xFFF5D36C))
        drawCircle(Color.White.copy(alpha = 0.72f), radius = w * 0.018f, center = Offset(w * 0.22f, h * 0.32f))
        drawCircle(Color.White.copy(alpha = 0.72f), radius = w * 0.014f, center = Offset(w * 0.83f, h * 0.62f))
    }
}

private fun makeComfortLine(mood: MoodOption, diary: String): String {
    val base = when (mood.label) {
        "행복함" -> "오늘의 반짝임을 오래 간직해도 좋아요."
        "평온함" -> "고요했던 마음도 충분히 소중한 기록이에요."
        "지침" -> "오늘은 버틴 것만으로도 충분히 잘했어요."
        "우울함" -> "무거운 마음을 혼자 들고 오느라 애썼어요."
        "설렘" -> "몽글거린 마음이 내일에도 다정히 남기를 바라요."
        else -> "기운이 적은 날에도 당신의 하루는 사라지지 않아요."
    }
    return if (diary.length > 80) base else "$base 짧은 기록도 마음을 돌보는 시작이에요."
}

@Composable
private fun MoodilyBgmPlayer(trackResId: Int, enabled: Boolean) {
    val context = LocalContext.current

    DisposableEffect(trackResId, enabled) {
        if (!enabled) {
            return@DisposableEffect onDispose { }
        }

        val player = MediaPlayer.create(context, trackResId)?.apply {
            isLooping = true
            setVolume(0.16f, 0.16f)
            start()
        }

        onDispose {
            player?.stop()
            player?.release()
        }
    }
}

private fun bgmFor(tab: MoodilyTab, mood: MoodOption?): Int {
    if (tab == MoodilyTab.Home) return R.raw.bgm_home
    if (tab == MoodilyTab.Archive) return R.raw.bgm_peaceful

    return when (MoodOptions.indexOf(mood)) {
        0 -> R.raw.bgm_happy
        1 -> R.raw.bgm_peaceful
        2 -> R.raw.bgm_tired
        3 -> R.raw.bgm_sad
        4 -> R.raw.bgm_excited
        5 -> R.raw.bgm_low
        else -> R.raw.bgm_home
    }
}
