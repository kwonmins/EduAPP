package com.example.myhealth.session

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface WordGameApi {
    @FormUrlEncoded
    @POST("wordchain_save.php") // 서버 PHP는 app_word에 저장하도록 구성
    suspend fun saveWordchain(
        @Field("login_id") loginId: String?,
        @Field("rounds") rounds: Int,
        @Field("avg_latency_ms") avgLatencyMs: Long,
        @Field("valid_ratio") validRatio: Float,
        @Field("total_ms") totalMs: Long,
        @Field("started_ms") startedMs: Long,
        @Field("finished_ms") finishedMs: Long,
        @Field("details_json") detailsJson: String
    ): String
}
