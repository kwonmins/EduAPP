package com.example.myhealth.ai

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

object OpenAiClient {
    // ⚠️ 배포용으로는 안전하게 보관하세요
    //private const val OPENAI_API_KEY =
        //""

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private const val CHAT_URL = "https://api.openai.com/v1/chat/completions"
    private const val MODEL = "gpt-4o-mini"

    // 응답 스키마(필요한 부분만)
    data class Resp(val choices: List<Choice>?)
    data class Choice(val message: Msg?)
    data class Msg(val content: String?)

    /** 1) 이미지 분석 → 질문/요약 JSON 문자열 반환 */
    suspend fun analyzeImageReturnJson(base64Jpeg: String): String = withContext(Dispatchers.IO) {
        val prompt = """
            너는 사진을 보고 한국어로 15초 동안 말하게 만들 질문을 한 문단으로 만든다.
            사진 유형/장소/계절/분위기를 "analysis"로, 사용자가 말할 항목 안내를 "question"으로 JSON으로만 출력해.
            예시:
            {"question":"가족사진이 보이네요. 지금 계절과 기분은 어떤가요? 함께 찍은 분들을 소개해 주세요.",
             "analysis":"가족 · 실내 · 겨울 느낌 · 따뜻한 분위기"}
        """.trimIndent()

        // 멀티모달 메시지: input_text + image_url  (메인 스레드 금지 → withContext(IO))
        val payload = gson.toJson(
            mapOf(
                "model" to MODEL,
                "temperature" to 0.2,
                "messages" to listOf(
                    mapOf(
                        "role" to "user",
                        "content" to listOf(
                            mapOf("type" to "text", "text" to prompt),   // ← 여기!
                            mapOf(
                                "type" to "image_url",
                                "image_url" to mapOf("url" to "data:image/jpeg;base64,$base64Jpeg")
                            )
                        )
                    )
                )
            )
        ).toRequestBody(JSON)

        val req = Request.Builder()
            .url(CHAT_URL)
            .addHeader("Authorization", "Bearer $OPENAI_API_KEY")
            .post(payload)
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: $body")
            val parsed = gson.fromJson(body, Resp::class.java)
            val content = parsed.choices?.firstOrNull()?.message?.content?.trim()
            // content가 비어있으면 원문을 그대로 넘겨서 상위에서 파싱 시도
            return@use content?.takeIf { it.isNotEmpty() } ?: body
        }
    }

    /** 2) 일기 작성 → {"title":"...","content":"..."} JSON 문자열 반환 */
    suspend fun makeDiaryJson(analysisJson: String, userSpeech: String): String = withContext(Dispatchers.IO) {
        val prompt = """
            다음은 이미지 분석 결과와 사용자의 15초 구술이다.
            오늘의 '메모리 다이어리'를 한국어로 작성해라.
            - 톤: 담백하고 따뜻하게, 3~6문장
            - 첫 줄은 요약 제목
            - JSON으로만 응답: {"title":"...","content":"..."}
            [analysis] $analysisJson
            [user] $userSpeech
        """.trimIndent()

        val payload = gson.toJson(
            mapOf(
                "model" to MODEL,
                "temperature" to 0.3,
                "messages" to listOf(mapOf("role" to "user", "content" to prompt))
            )
        ).toRequestBody(JSON)

        val req = Request.Builder()
            .url(CHAT_URL)
            .addHeader("Authorization", "Bearer $OPENAI_API_KEY")
            .post(payload)
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: $body")
            val parsed = gson.fromJson(body, Resp::class.java)
            val content = parsed.choices?.firstOrNull()?.message?.content?.trim()
            return@use content?.takeIf { it.isNotEmpty() } ?: body
        }
    }
    // 색칠 결과 분석: {"mood":"...", "personality":"...", "score":85, "summary":"..."} JSON 문자열 반환
    suspend fun analyzeColoringReturnJson(base64Png: String): String = withContext(Dispatchers.IO) {
        val prompt = """
        너는 아동 심리 상담을 돕는 보조가야.
        사용자가 색칠한 '도형 색칠하기' 이미지를 보고 현재 기분과 성격 경향을 간단히 평가하고 0~100점으로 점수를 매겨라.
        색 선택(따뜻/차가움), 채색의 강약/면적, 빈칸/정돈도 등을 근거로 설명하되 과도한 단정은 피하라.
        JSON 으로만 응답:
        {"mood":"간단 한줄", "personality":"간단 한줄", "score": 0..100, "summary":"두세 문장"}
    """.trimIndent()

        val payload = gson.toJson(
            mapOf(
                "model" to "gpt-4o-mini",
                "temperature" to 0.3,
                "messages" to listOf(
                    mapOf(
                        "role" to "user",
                        "content" to listOf(
                            mapOf("type" to "text", "text" to prompt),
                            mapOf("type" to "image_url",
                                "image_url" to mapOf("url" to "data:image/png;base64,$base64Png"))
                        )
                    )
                )
            )
        ).toRequestBody(JSON)

        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $OPENAI_API_KEY")
            .post(payload)
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: $body")
            val parsed = gson.fromJson(body, Resp::class.java)
            parsed.choices?.firstOrNull()?.message?.content?.trim().takeUnless { it.isNullOrBlank() } ?: body
        }
    }

    // OpenAiClient.kt 내부 (object OpenAiClient 안)

    suspend fun analyzeDiaryQualities(title: String, content: String): String =
        withContext(Dispatchers.IO) {
            // 모델에 줄 프롬프트
            val prompt = """
            아래 일기 "제목"과 "본문"을 읽고 0~1 범위의 점수로 평가해.
            JSON만 반환하고, 다른 설명/코드펜스는 절대 넣지 마.
            keys: warmth, positivity, detail, calmness, mood
            예시: {"warmth":0.78,"positivity":0.72,"detail":0.61,"calmness":0.70,"mood":"따뜻하고 안정적"}

            제목: $title
            본문: $content
        """.trimIndent()

            // Chat Completions 멀티모달 형식(텍스트만)
            val payload = gson.toJson(
                mapOf(
                    "model" to MODEL,                // 예: "gpt-4o-mini"
                    "temperature" to 0.2,
                    "messages" to listOf(
                        mapOf(
                            "role" to "user",
                            "content" to listOf(
                                mapOf("type" to "text", "text" to prompt)
                            )
                        )
                    )
                )
            ).toRequestBody(JSON)

            val req = Request.Builder()
                .url(CHAT_URL)                      // 예: "https://api.openai.com/v1/chat/completions"
                .addHeader("Authorization", "Bearer $OPENAI_API_KEY")
                .post(payload)
                .build()

            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: $body")

                // 모델 답변에서 content만 추출 (없으면 원문 반환)
                val parsed = gson.fromJson(body, Resp::class.java)
                parsed.choices?.firstOrNull()?.message?.content?.trim()
                    .takeUnless { it.isNullOrBlank() } ?: body
            }
        }

}
