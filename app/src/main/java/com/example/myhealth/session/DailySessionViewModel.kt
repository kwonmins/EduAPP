package com.example.myhealth.session

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// 끝말잇기 요약
data class WordSummary(
    val avgLatencyMs: Long = 0L,
    val validRatio: Float = 0f
)

// 만다라 요약
data class MandalaSummary(
    val symmetry: Float = 0f,   // 0.0~1.0
    val fillRatio: Float = 0f,  // 0.0~1.0
    val warmRatio: Float = 0f   // 0.0~1.0
)

class DailySessionViewModel : ViewModel() {

    // --- 끝말잇기 ---
    private val _word = MutableStateFlow(WordSummary())
    val word: StateFlow<WordSummary> = _word
    fun setWord(avgLatencyMs: Long, validRatio: Float) {
        _word.value = WordSummary(avgLatencyMs, validRatio)
    }

    // --- 만다라 ---
    private val _mandala = MutableStateFlow(MandalaSummary())
    val mandala: StateFlow<MandalaSummary> = _mandala
    fun setMandala(symmetry: Float, fillRatio: Float, warmRatio: Float) {
        _mandala.value = MandalaSummary(
            symmetry.coerceIn(0f, 1f),
            fillRatio.coerceIn(0f, 1f),
            warmRatio.coerceIn(0f, 1f)
        )
    }

    // --- 일기(메모리 다이어리) ---
    private val _diaryText = MutableStateFlow("")
    val diaryText: StateFlow<String> = _diaryText
    fun setDiary(text: String) {
        _diaryText.value = text
    }
}
