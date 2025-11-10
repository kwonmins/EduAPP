package com.example.myhealth.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.myhealth.ai.OpenAiClient
import com.example.myhealth.session.DirectDbRepository
import com.example.myhealth.session.SessionDataStore
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.Locale

@Composable
fun MemoryDiaryScreen(
    onDone: () -> Unit
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as Application
    val session = remember { SessionDataStore(app) }
    val loginId by session.userIdFlow.collectAsState(initial = null)

    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val db = remember { DirectDbRepository() }

    // 상태
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var photoBase64 by remember { mutableStateOf<String?>(null) }
    var analysisJson by remember { mutableStateOf<String?>(null) }
    var question by remember { mutableStateOf<String?>(null) }

    var isAnalyzing by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var secondLeft by remember { mutableStateOf(15) }
    var sttText by remember { mutableStateOf("") }

    var diaryTitle by remember { mutableStateOf<String?>(null) }
    var diaryContent by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var autoGenTried by remember { mutableStateOf(false) } // STT 실패 폴백 한번만

    // 권한 (RECORD_AUDIO)
    val micPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) scope.launch { snack.showSnackbar("마이크 권한이 필요합니다.") }
    }

    // 포토 피커
    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            photoUri = uri
            // 초기화
            analysisJson = null; question = null
            sttText = ""; diaryTitle = null; diaryContent = null; autoGenTried = false

            // base64 인코딩 후 분석
            val b64 = uriToBase64(ctx, uri)
            photoBase64 = b64
            scope.launch {
                try {
                    isAnalyzing = true
                    val json = OpenAiClient.analyzeImageReturnJson(b64!!)
                    analysisJson = json
                    question = extractField(json, "question") ?: json
                } catch (e: Throwable) {
                    snack.showSnackbar("분석 실패: ${e.localizedMessage}")
                } finally {
                    isAnalyzing = false
                }
            }
        }
    }

    // 일기 생성 공용 함수(분석만으로도 생성 가능)
    fun generateDiary() {
        val analysis = analysisJson ?: "{}"
        scope.launch {
            try {
                val diaryJson = OpenAiClient.makeDiaryJson(analysis, sttText.ifBlank { "" })
                diaryTitle = extractField(diaryJson, "title") ?: "오늘의 기록"
                diaryContent = extractField(diaryJson, "content") ?: diaryJson
            } catch (e: Throwable) {
                snack.showSnackbar("일기 생성 실패: ${e.localizedMessage}")
            }
        }
    }

    // UI
    Scaffold(snackbarHost = { SnackbarHost(snack) }) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("메모리 다이어리", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))

            // 안내문구: 사진 선택 전만 보여줌
            if (photoUri == null) {
                InfoHintCard()
                Spacer(Modifier.height(12.dp))
            }

            // 1) 사진 선택/미리보기 — 디자인 수정
            if (photoUri == null) {
                // 첫 업로드 전: 화면 중앙에 큰 버튼
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 6.dp)
                        .height(180.dp)
                ) {
                    Button(
                        onClick = {
                            pickPhoto.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !isAnalyzing && !isRecording && !saving,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.88f)
                            .height(68.dp)
                    ) {
                        Icon(
                            painterResource(id = android.R.drawable.ic_menu_camera),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "사진 올리기",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (isAnalyzing) {
                    LinearProgressIndicator(
                        Modifier
                            .fillMaxWidth(0.88f)
                            .padding(top = 4.dp)
                    )
                }
            } else {
                // 사진을 이미 골랐을 때: 상단에 작게 "다른 사진 선택"
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            pickPhoto.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !isAnalyzing && !isRecording && !saving,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(44.dp)
                    ) { Text("다른 사진 선택") }

                    Spacer(Modifier.weight(1f))
                    if (isAnalyzing) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            // ⬇️ 새 사각형 미리보기 (화면 높이의 약 28%)
            if (photoUri != null) {
                val conf = LocalConfiguration.current
                val photoHeight = (conf.screenHeightDp.dp * 0.28f)

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(photoHeight)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = photoUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 2) 질문 표시
            if (question != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("질문", color = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.height(6.dp))
                        Text(question!!)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // 3) 15초 음성 녹음(안드로이드 STT)
            if (question != null && diaryContent == null) {
                RecordSection(
                    onRequestMic = { micPerm.launch(Manifest.permission.RECORD_AUDIO) },
                    onStartRecord = {
                        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
                            scope.launch { snack.showSnackbar("이 기기에서 음성 인식이 지원되지 않습니다.") }
                            return@RecordSection null
                        }
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREAN.toLanguageTag())
                            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        }
                        SpeechRecognizer.createSpeechRecognizer(ctx) to intent
                    },
                    onRecord = { recognizer, intent, stopAfter ->
                        isRecording = true
                        sttText = ""
                        secondLeft = 15
                        val timer = object : CountDownTimer(15000, 1000) {
                            override fun onTick(millisUntilFinished: Long) {
                                secondLeft = (millisUntilFinished / 1000).toInt()
                            }
                            override fun onFinish() { stopAfter() }
                        }
                        recognizer.setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {}
                            override fun onBeginningOfSpeech() {}
                            override fun onRmsChanged(rmsdB: Float) {}
                            override fun onBufferReceived(buffer: ByteArray?) {}
                            override fun onEndOfSpeech() {}
                            override fun onError(error: Int) {
                                isRecording = false; timer.cancel()
                                // STT 실패 → 분석만으로 자동 생성(1회)
                                if (diaryContent == null && !autoGenTried) {
                                    autoGenTried = true
                                    generateDiary()
                                }
                            }
                            override fun onPartialResults(partialResults: Bundle?) {
                                val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                if (!list.isNullOrEmpty()) sttText = list.joinToString(" ")
                            }
                            override fun onEvent(eventType: Int, params: Bundle?) {}
                            override fun onResults(results: Bundle?) {
                                isRecording = false
                                timer.cancel()
                                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                if (!list.isNullOrEmpty()) sttText = list.joinToString(" ")

                                // 결과가 비었어도 폴백으로 일기 생성
                                if (diaryContent == null && (sttText.isNotBlank() || !autoGenTried)) {
                                    autoGenTried = true
                                    generateDiary()
                                }
                            }
                        })
                        recognizer.startListening(intent)
                        timer.start()
                    },
                    onStopRecord = { recognizer ->
                        recognizer.stopListening()
                        recognizer.cancel()
                        recognizer.destroy()
                        isRecording = false
                        // 사용자가 중간에 멈춘 경우도 폴백 1회
                        if (diaryContent == null && sttText.isBlank() && !autoGenTried) {
                            autoGenTried = true
                            generateDiary()
                        }
                    },
                    isRecording = isRecording,
                    secondLeft = secondLeft,
                    sttText = sttText
                )
            }

            // 4) 버튼들: 일기 생성 / 다음으로
            if (question != null && diaryContent == null) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { generateDiary() },
                        enabled = !isAnalyzing && !isRecording
                    ) { Text(if (sttText.isBlank()) "분석만으로 일기 생성" else "일기 생성") }

                    OutlinedButton(onClick = { onDone() }) { Text("다음으로") }
                }
            }

            // 5) 결과 미리보기 + 저장/다음
            if (diaryContent != null) {
                Spacer(Modifier.height(16.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(diaryTitle ?: "오늘의 기록", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(diaryContent!!)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                saving = true
                                val res = db.insertDiary(
                                    loginId = loginId,
                                    title = diaryTitle ?: "오늘의 기록",
                                    content = diaryContent!!,
                                    photoBase64 = photoBase64,
                                    analysisJson = analysisJson,
                                    sttText = sttText,
                                    recordedSec = 15
                                )
                                saving = false
                                snack.showSnackbar(res.fold(
                                    onSuccess = { "저장 완료 (id=$it)" },
                                    onFailure = { "저장 실패: ${it.localizedMessage}" }
                                ))
                                onDone() // 저장 후 다음 미션으로 이동
                            }
                        },
                        enabled = !saving
                    ) { Text(if (saving) "저장 중…" else "저장하고 다음") }
                }
            }
        }
    }
}

/* --- 안내 카드 --- */

@Composable
private fun InfoHintCard() {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                "추억이 깃든 사진을 업로드해보세요",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "AI의 질문에 답을 하면 일기 형태로 만들어드립니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/* --- 컴포넌트 & 유틸 --- */

@Composable
private fun RecordSection(
    onRequestMic: () -> Unit,
    onStartRecord: () -> Pair<SpeechRecognizer, Intent>?, // return recognizer,intent
    onRecord: (SpeechRecognizer, Intent, stopAfter: () -> Unit) -> Unit,
    onStopRecord: (SpeechRecognizer) -> Unit,
    isRecording: Boolean,
    secondLeft: Int,
    sttText: String
) {
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("음성 응답 (15초)", color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isRecording) {
                    Button(onClick = {
                        onRequestMic()
                        val pair = onStartRecord() ?: return@Button
                        recognizer = pair.first
                        onRecord(pair.first, pair.second) {
                            recognizer?.let { onStopRecord(it) }
                        }
                    }) { Text("녹음 시작") }
                } else {
                    Button(onClick = { recognizer?.let { onStopRecord(it) } }) { Text("정지") }
                }
                Spacer(Modifier.width(12.dp))
                RecordingBadge(isRecording = isRecording, secondLeft = secondLeft)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = sttText,
                onValueChange = {},
                label = { Text("인식된 내용(자동)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
        }
    }
}

@Composable
private fun RecordingBadge(isRecording: Boolean, secondLeft: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val alpha = if (isRecording) 1f else 0.35f
        Icon(
            painter = painterResource(id = android.R.drawable.presence_audio_online),
            contentDescription = null,
            modifier = Modifier.size(24.dp).alpha(alpha),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(6.dp))
        Text(if (isRecording) "녹음중 • ${secondLeft}s" else "대기", color = MaterialTheme.colorScheme.primary)
    }
}

private fun uriToBase64(ctx: android.content.Context, uri: Uri): String? {
    return try {
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            val bmp = BitmapFactory.decodeStream(input) ?: return null
            val out = ByteArrayOutputStream()
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
            android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
        }
    } catch (_: Throwable) { null }
}

/** {"key":"value"} 형태에서 value만 뽑아오기 */
private fun extractField(json: String, key: String): String? {
    val regex = Regex(""""$key"\s*:\s*"([^"]+)"""")
    return regex.find(json)?.groupValues?.getOrNull(1)
}
