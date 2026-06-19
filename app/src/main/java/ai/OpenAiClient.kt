package com.example.myhealth.ai

import com.example.myhealth.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object OpenAiClient {
    private const val CHAT_URL = "https://api.openai.com/v1/chat/completions"
    private const val MODEL = "gpt-4o-mini"

    suspend fun makeEmpathyReply(mood: String, diary: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.OPENAI_API_KEY.trim()
        if (apiKey.isBlank()) {
            return@withContext fallbackEmpathyReply(mood, diary)
        }

        val prompt = """
            너는 감성 다이어리 앱 Moodily의 AI 캐릭터 Moa야.
            사용자는 하루를 마무리하며 일기를 적었고, 지금 필요한 것은 판단이나 해결책보다 따뜻한 공감이야.

            응답 규칙:
            - 한국어로 답해.
            - 4~6문장으로 답해.
            - 첫 문장은 사용자의 감정을 구체적으로 짚어줘.
            - 섣부른 조언, 진단, 과장된 위로는 피하고 다정하게 받아줘.
            - 마지막 문장은 오늘 하루를 내려놓을 수 있는 부드러운 문장으로 마무리해.
            - 캐릭터 이름 Moa를 한 번만 자연스럽게 써도 좋아.

            오늘의 감정: $mood
            사용자의 일기:
            $diary
        """.trimIndent()

        runCatching {
            requestChatCompletion(apiKey, prompt)
        }.getOrElse {
            fallbackEmpathyReply(mood, diary)
        }
    }

    suspend fun analyzeImageReturnJson(base64Jpeg: String): String = withContext(Dispatchers.Default) {
        """{"question":"사진 속 장면을 떠올리며 그때의 기분과 함께 있었던 사람을 이야기해 주세요.","analysis":"오프라인 기본 분석"}"""
    }

    suspend fun makeDiaryJson(analysisJson: String, userSpeech: String): String = withContext(Dispatchers.Default) {
        val remembered = userSpeech.ifBlank { "오늘 마음에 남은 장면을 천천히 떠올려 보았다." }
            .replace("\"", "'")
        """{"title":"오늘의 기억","content":"$remembered 오늘의 기억을 다시 꺼내 보니 마음이 조금 차분해졌다. 다음에도 이런 시간을 편안하게 기록해 보고 싶다."}"""
    }

    suspend fun analyzeColoringReturnJson(base64Png: String): String = withContext(Dispatchers.Default) {
        """{"mood":"차분함","personality":"꼼꼼함","score":75,"summary":"색칠을 통해 안정적인 리듬과 집중력이 잘 드러났습니다."}"""
    }

    suspend fun analyzeDiaryQualities(title: String, content: String): String = withContext(Dispatchers.Default) {
        val detail = if (content.length >= 40) 0.70f else 0.45f
        """{"warmth":0.68,"positivity":0.64,"detail":$detail,"calmness":0.72,"mood":"차분함"}"""
    }

    private fun requestChatCompletion(apiKey: String, prompt: String): String {
        val payload = JSONObject()
            .put("model", MODEL)
            .put("temperature", 0.75)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", "You are Moa, a warm Korean AI diary companion."))
                    .put(JSONObject().put("role", "user").put("content", prompt))
            )
            .toString()

        val connection = (URL(CHAT_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

        return connection.use {
            outputStream.use { stream ->
                stream.write(payload.toByteArray(Charsets.UTF_8))
            }

            val responseBody = if (responseCode in 200..299) {
                inputStream.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
            } else {
                val error = errorStream?.bufferedReader(Charsets.UTF_8)?.use { reader -> reader.readText() }.orEmpty()
                throw IOException("OpenAI HTTP $responseCode: $error")
            }

            JSONObject(responseBody)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        }
    }

    private fun fallbackEmpathyReply(mood: String, diary: String): String {
        val trimmed = diary.trim()
        val firstSentence = trimmed
            .split('.', '!', '?', '。', '\n')
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            .orEmpty()

        val opening = when (mood) {
            "행복함" -> "오늘 마음에 작고 예쁜 빛이 들어온 것 같아요."
            "평온함" -> "오늘은 마음이 천천히 숨을 고른 날이었네요."
            "지침" -> "오늘 정말 많이 애썼어요. 지친 마음이 여기까지 온 것만으로도 충분해요."
            "우울함" -> "마음이 무거운 날을 혼자 지나오느라 쉽지 않았겠어요."
            "설렘" -> "설레는 마음을 품고 하루를 보낸 당신이 참 사랑스러워 보여요."
            else -> "기운이 잘 나지 않는 날에도 이렇게 마음을 적어준 건 꽤 다정한 일이에요."
        }

        val reflection = if (firstSentence.isNotBlank()) {
            "\"$firstSentence\" 이 부분에서 오늘의 마음이 조심스럽게 느껴졌어요."
        } else {
            "말이 길지 않아도, 그 안에 오늘의 무게가 담겨 있었을 거예요."
        }

        return "$opening\n\n$reflection\n\n지금은 무언가를 더 잘하려고 애쓰기보다, 오늘의 나를 조금 덜 미워하고 편히 내려놓아도 괜찮아요. Moa가 조용히 곁에 있을게요."
    }
}

private inline fun <T : HttpURLConnection, R> T.use(block: T.() -> R): R {
    return try {
        block()
    } finally {
        disconnect()
    }
}
