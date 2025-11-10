package com.example.myhealth.ui

import com.example.myhealth.session.DailySessionViewModel
import android.app.Application
import android.graphics.*
import android.graphics.Color as AColor            // ← 안드로이드 Color는 AColor 별칭
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color as CColor // ← Compose Color는 CColor 별칭
import androidx.compose.ui.graphics.toArgb         // ← Compose Color → ARGB(Int)
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import com.example.myhealth.ai.OpenAiClient
import com.example.myhealth.session.DirectDbRepository
import com.example.myhealth.session.SessionDataStore
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlin.random.Random

@Composable
fun MandalaScreen( // 라우트 이름은 유지, 화면은 ‘도형 색칠하기’
    vm: DailySessionViewModel, // 안 써도 호환 위해 남김
    onDone: () -> Unit
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as Application
    val session = remember { SessionDataStore(app) }
    val loginId by session.userIdFlow.collectAsState(initial = null)
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val repo = remember { DirectDbRepository() }

    // ===== 1) 템플릿 생성 & 상태 =====
    val templateId = remember { listOf("ring", "flower", "star", "tworings", "petals", "web")[Random.nextInt(6)] }
    var bmp by remember { mutableStateOf(makeTemplate(templateId, 1024)) } // 흰 배경 + 검은 윤곽선
    var showW by remember { mutableStateOf(0f) }
    var showH by remember { mutableStateOf(0f) }

    // 팔레트 (Compose Color 사용)
    val palette = listOf(
        CColor(0xFFE53935), CColor(0xFFFF9800), CColor(0xFFFFEB3B),
        CColor(0xFF8BC34A), CColor(0xFF00BCD4), CColor(0xFF3F51B5),
        CColor(0xFFE91E63), CColor(0xFF795548), CColor(0xFF9E9E9E), CColor(0xFFFFFFFF)
    )
    var selected by remember { mutableStateOf(palette[0]) }

    // 되돌리기 스택
    val history = remember { ArrayDeque<Bitmap>() }

    // ===== 2) 색칠(터치→Flood Fill) =====
    fun floodFillTap(displayX: Float, displayY: Float, boxW: Float, boxH: Float) {
        val bw = bmp.width.toFloat(); val bh = bmp.height.toFloat()
        val scale = min(boxW / bw, boxH / bh)
        val dx = (boxW - bw * scale) / 2f
        val dy = (boxH - bh * scale) / 2f
        val x = ((displayX - dx) / scale).toInt().coerceIn(0, bmp.width - 1)
        val y = ((displayY - dy) / scale).toInt().coerceIn(0, bmp.height - 1)

        val targetColor = bmp.getPixel(x, y)
        val newColor = selected.toArgb() // ← Compose Color → Int
        if (targetColor == newColor) return

        // 히스토리 저장(최대 8개)
        history.addLast(bmp.copy(Bitmap.Config.ARGB_8888, true))
        if (history.size > 8) history.removeFirst()

        bmp = bmp.copy(Bitmap.Config.ARGB_8888, true)
        floodFill(bmp, x, y, targetColor, newColor, tolerance = 12)
    }

    // ===== 3) 분석 + 저장 =====
    var analysisJson by remember { mutableStateOf<String?>(null) }
    var score by remember { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }

    fun analyzeAndSave(goNext: Boolean) {
        scope.launch {
            try {
                val pngB64 = bitmapToBase64Png(bmp)
                val json = OpenAiClient.analyzeColoringReturnJson(pngB64)
                analysisJson = json
                val parsedScore = Regex(""""score"\s*:\s*(\d{1,3})""").find(json)?.groupValues?.getOrNull(1)?.toIntOrNull()
                score = (parsedScore ?: 75).coerceIn(0, 100)

                saving = true
                val res = repo.insertColoring(
                    loginId = loginId,
                    templateId = templateId,
                    score = score!!,
                    analysisJson = json,
                    imageBase64 = pngB64
                )
                saving = false
                snack.showSnackbar(res.fold(
                    onSuccess = { "저장 완료(id=$it) · 점수 $score" },
                    onFailure = { "저장 실패: ${it.localizedMessage}" }
                ))
                if (goNext) onDone()
            } catch (e: Throwable) {
                snack.showSnackbar("분석 실패: ${e.localizedMessage}")
            }
        }
    }

    // ===== 4) UI =====
    Scaffold(snackbarHost = { SnackbarHost(snack) }) { inner ->
        Column(
            Modifier.padding(inner).fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("도형 색칠하기", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text("오늘의 그림은 이것입니다", color = MaterialTheme.colorScheme.secondary)

            Spacer(Modifier.height(12.dp))

            // 캔버스(정사각형)
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .border(1.dp, MaterialTheme.colorScheme.outline)
                    .background(MaterialTheme.colorScheme.surface)
                    .pointerInput(selected, bmp) {
                        detectTapGestures { off ->
                            floodFillTap(off.x, off.y, showW, showH)
                        }
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    showW = size.width; showH = size.height
                    drawImage(bmp.asImageBitmap())
                }
            }

            Spacer(Modifier.height(10.dp))

            // 팔레트
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                palette.forEach { c ->
                    val border = if (c == selected) 3.dp else 1.dp
                    Box(
                        Modifier
                            .size(28.dp)
                            .background(c, shape = MaterialTheme.shapes.small) // ← Compose Color 그대로 사용
                            .border(border, MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small)
                            .pointerInput(Unit) { detectTapGestures { selected = c } }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 액션 버튼들
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { if (history.isNotEmpty()) bmp = history.removeLast() },
                    enabled = history.isNotEmpty()
                ) { Text("되돌리기") }

                OutlinedButton(
                    onClick = { bmp = makeTemplate(templateId, 1024); history.clear(); analysisJson = null; score = null }
                ) { Text("비우기") }

              //  Button(onClick = { analyzeAndSave(false) }, enabled = !saving) { Text("AI 분석") }
                Button(onClick = { analyzeAndSave(true) }, enabled = !saving) {
                    Text(if (saving) "저장 중…" else "저장하고 다음")
                }
            }

            // 분석 결과 카드(예쁘게)
            if (analysisJson != null) {
                val a = remember(analysisJson) { parseColoringAnalysis(analysisJson!!) }
                val scroll = rememberScrollState()

                Spacer(Modifier.height(12.dp))
                Card(
                    Modifier
                        .fillMaxWidth()
                        // 내용이 길면 카드 안에서만 스크롤 되도록 최대 높이 한정
                        .heightIn(min = 0.dp, max = 220.dp)
                ) {
                    Column(
                        Modifier
                            .padding(16.dp)
                            .verticalScroll(scroll)   // ← 카드 내부 스크롤
                    ) {
                        Text("AI 분석 결과", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!a.mood.isNullOrBlank()) AssistChip(
                                onClick = {},
                                label = { Text(a.mood!!) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            )
                            if (!a.personality.isNullOrBlank()) AssistChip(
                                onClick = {},
                                label = { Text(a.personality!!) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            )
                        }

                        if (a.score != null) {
                            Spacer(Modifier.height(14.dp))
                            Text("점수", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LinearProgressIndicator(
                                    progress = (a.score!!.coerceIn(0,100) / 100f),
                                    modifier = Modifier.weight(1f).height(10.dp),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    color = scoreColor(a.score!!)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text("${a.score}점", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        if (!a.summary.isNullOrBlank()) {
                            Spacer(Modifier.height(14.dp))
                            Text("요약", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.height(6.dp))
                            // 스크롤 박스 안이므로 줄수 제한 제거
                            Text(a.summary!!, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

        }
    }
}
// 분석 JSON 파싱
private data class ColoringAnalysis(
    val mood: String?, val personality: String?, val score: Int?, val summary: String?
)

private fun parseColoringAnalysis(raw: String): ColoringAnalysis {
    // ```json ... ``` 같은 코드펜스 제거
    val json = raw
        .replace("```", "")
        .replace(Regex("^json\\s*", RegexOption.IGNORE_CASE), "")
        .trim()

    fun str(key: String) =
        Regex(""""$key"\s*:\s*"(.*?)"""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .find(json)?.groupValues?.getOrNull(1)?.trim()

    fun int(key: String) =
        Regex(""""$key"\s*:\s*(\d{1,3})""", RegexOption.IGNORE_CASE)
            .find(json)?.groupValues?.getOrNull(1)?.toIntOrNull()

    return ColoringAnalysis(
        mood = str("mood"),
        personality = str("personality"),
        score = int("score"),
        summary = str("summary")
    )
}

// 점수에 따른 색상 (빨-노-초)
private fun scoreColor(score: Int): androidx.compose.ui.graphics.Color = when (score) {
    in 0..59  -> androidx.compose.ui.graphics.Color(0xFFE53935)
    in 60..79 -> androidx.compose.ui.graphics.Color(0xFFFFA000)
    else      -> androidx.compose.ui.graphics.Color(0xFF43A047)
}
/* ========= 유틸 ========== */

// 템플릿(윤곽선) 생성: 흰 배경 + 검은 선
private fun makeTemplate(id: String, size: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(AColor.WHITE) // ← 안드로이드 Color는 AColor로
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AColor.BLACK
        style = Paint.Style.STROKE
        strokeWidth = size * 0.02f
    }
    val cx = size / 2f; val cy = size / 2f
    val r = size * 0.45f

    fun ring(radius: Float) { c.drawCircle(cx, cy, radius, p) }
    fun dottedRing(radius: Float) {
        val dash = Paint(p).apply { pathEffect = DashPathEffect(floatArrayOf(20f, 24f), 0f) }
        c.drawCircle(cx, cy, radius, dash)
    }
    fun petals(count: Int, inner: Float, outer: Float) {
        val path = android.graphics.Path()
        repeat(count) { i ->
            val a = (i * 360f / count) * (Math.PI / 180).toFloat()
            val x1 = cx + inner * kotlin.math.cos(a)
            val y1 = cy + inner * kotlin.math.sin(a)
            val x2 = cx + outer * kotlin.math.cos(a + 0.12f)
            val y2 = cy + outer * kotlin.math.sin(a + 0.12f)
            val x3 = cx + inner * kotlin.math.cos(a + 0.24f)
            val y3 = cy + inner * kotlin.math.sin(a + 0.24f)
            path.reset()
            path.moveTo(x1, y1); path.quadTo(x2, y2, x3, y3)
            c.drawPath(path, p)
        }
    }

    when (id) {
        "ring" -> { ring(r); ring(r*0.7f); ring(r*0.4f); dottedRing(r*0.55f) }
        "flower" -> { ring(r); petals(24, r*0.55f, r*0.95f); ring(r*0.35f) }
        "star" -> {
            ring(r)
            val path = android.graphics.Path()
            val n = 5
            val pts = (0 until n).map { i ->
                val a = (i * 360f / n - 90) * (Math.PI / 180).toFloat()
                Offset(cx + r*0.7f*kotlin.math.cos(a), cy + r*0.7f*kotlin.math.sin(a))
            }
            path.moveTo(pts[0].x, pts[0].y)
            listOf(2,4,1,3,0).forEach { j -> path.lineTo(pts[j].x, pts[j].y) }
            c.drawPath(path, p); ring(r*0.4f)
        }
        "tworings" -> { ring(r); dottedRing(r*0.8f); ring(r*0.6f); dottedRing(r*0.4f); ring(r*0.2f) }
        "petals" -> { ring(r); petals(36, r*0.5f, r*0.9f); ring(r*0.3f); petals(24, r*0.25f, r*0.45f) }
        "web" -> {
            ring(r); ring(r*0.8f); ring(r*0.6f); ring(r*0.4f); ring(r*0.2f)
            val spokes = 24
            repeat(spokes) { i ->
                val a = (i * 360f / spokes) * (Math.PI / 180).toFloat()
                c.drawLine(
                    cx + r*kotlin.math.cos(a), cy + r*kotlin.math.sin(a),
                    cx - r*kotlin.math.cos(a), cy - r*kotlin.math.sin(a), p
                )
            }
        }
    }
    return bmp
}

// Flood Fill (경계: 검은 윤곽선). tolerance는 경계 누수 방지
private fun floodFill(bmp: Bitmap, sx: Int, sy: Int, targetStart: Int, newColor: Int, tolerance: Int = 10) {
    val w = bmp.width; val h = bmp.height
    val px = IntArray(w*h)
    bmp.getPixels(px, 0, w, 0, 0, w, h)
    val start = sy * w + sx
    val base = px[start]
    if (base == newColor) return

    fun similar(a: Int, b: Int): Boolean {
        val ar = (a shr 16) and 0xFF; val ag = (a shr 8) and 0xFF; val ab = a and 0xFF
        val br = (b shr 16) and 0xFF; val bg = (b shr 8) and 0xFF; val bb = b and 0xFF
        return (kotlin.math.abs(ar-br)+kotlin.math.abs(ag-bg)+kotlin.math.abs(ab-bb)) <= tolerance
    }

    val stack = ArrayDeque<Int>()
    stack.add(start)
    while (stack.isNotEmpty()) {
        val idx = stack.removeLast()
        if (idx !in 0 until px.size) continue
        if (!similar(px[idx], base)) continue
        px[idx] = newColor
        val x = idx % w
        if (x > 0) stack.add(idx - 1)
        if (x < w - 1) stack.add(idx + 1)
        stack.add(idx - w)
        stack.add(idx + w)
    }
    bmp.setPixels(px, 0, w, 0, 0, w, h)
}

// 비트맵 → PNG Base64
private fun bitmapToBase64Png(bmp: Bitmap): String {
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}
