package com.example.myhealth.session

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong

class LocalSessionRepository {
    data class LastWord(val avgLatencyMs: Int, val validRatio: Float, val rounds: Int)
    data class LastDiary(val title: String, val content: String)
    data class LastColoring(val score: Int, val analysisJson: String?)

    data class DailySummaryRow(
        val date: LocalDate,
        val total: Int,
        val word: Int,
        val diary: Int,
        val color: Int,
        val emotion: Int,
        val cognition: Int,
        val memory: Int,
        val detailJson: String?
    )

    suspend fun getLastWord(): LastWord? = withContext(Dispatchers.Default) {
        Store.lastWord
    }

    suspend fun getLastDiary(): LastDiary? = withContext(Dispatchers.Default) {
        Store.lastDiary
    }

    suspend fun getLastColoring(): LastColoring? = withContext(Dispatchers.Default) {
        Store.lastColoring
    }

    suspend fun insertDiary(
        title: String,
        content: String,
        photoBase64: String?,
        analysisJson: String?,
        sttText: String?,
        recordedSec: Int = 15
    ): Result<Long> = withContext(Dispatchers.Default) {
        Store.lastDiary = LastDiary(title = title, content = content)
        Result.success(Store.nextId.incrementAndGet())
    }

    suspend fun insertAppWord(
        rounds: Int,
        avgLatencyMs: Long,
        validRatio: Float,
        totalMs: Long,
        startedMs: Long,
        finishedMs: Long,
        detailsJson: String
    ): Result<Long> = withContext(Dispatchers.Default) {
        Store.lastWord = LastWord(
            avgLatencyMs = avgLatencyMs.toInt(),
            validRatio = validRatio,
            rounds = rounds
        )
        Result.success(Store.nextId.incrementAndGet())
    }

    suspend fun insertColoring(
        templateId: String,
        score: Int,
        analysisJson: String?,
        imageBase64: String?
    ): Result<Long> = withContext(Dispatchers.Default) {
        Store.lastColoring = LastColoring(score = score, analysisJson = analysisJson)
        Result.success(Store.nextId.incrementAndGet())
    }

    suspend fun upsertDailySummary(
        date: LocalDate,
        total: Int,
        word: Int,
        diary: Int,
        color: Int,
        emotion: Int,
        cognition: Int,
        memory: Int,
        detailJson: String?
    ): Result<Unit> = withContext(Dispatchers.Default) {
        Store.summaries[date] = DailySummaryRow(
            date = date,
            total = total,
            word = word,
            diary = diary,
            color = color,
            emotion = emotion,
            cognition = cognition,
            memory = memory,
            detailJson = detailJson
        )
        Result.success(Unit)
    }

    suspend fun getDailySummaries(start: LocalDate, end: LocalDate): List<DailySummaryRow> =
        withContext(Dispatchers.Default) {
            Store.summaries.values
                .filter { it.date in start..end }
                .sortedBy { it.date }
        }

    private object Store {
        val nextId = AtomicLong(0)
        var lastWord: LastWord? = null
        var lastDiary: LastDiary? = null
        var lastColoring: LastColoring? = null
        val summaries = linkedMapOf<LocalDate, DailySummaryRow>()
    }
}
