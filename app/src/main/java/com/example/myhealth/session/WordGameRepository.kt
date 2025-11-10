package com.example.myhealth.session

import android.app.Application
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

class WordGameRepository(app: Application) {
    private val tag = "WordGameRepository"
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
    private val sessionStore = SessionDataStore(app)

    private val api: WordGameApi by lazy {
        val logger = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder()
            .addInterceptor(logger)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        Retrofit.Builder()
            .baseUrl(ApiConst.BASE_URL)
            .client(client)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(WordGameApi::class.java)
    }

    suspend fun save(
        rounds: Int,
        avgLatencyMs: Long,
        validRatio: Float,
        totalMs: Long,
        startedMs: Long,
        finishedMs: Long,
        details: List<Map<String, Any?>>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val loginId = sessionStore.userIdFlow.first()
            val detailsJson = gson.toJson(details)
            val res = api.saveWordchain(loginId, rounds, avgLatencyMs, validRatio, totalMs, startedMs, finishedMs, detailsJson)
            Log.d(tag, "saveWordchain -> $res")
            Result.success(res)
        } catch (e: Exception) {
            Log.e(tag, "saveWordchain error", e)
            Result.failure(e)
        }
    }
}
