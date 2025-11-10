package com.example.myhealth.session

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

// (서버가 JSON을 줄 때 파싱용; 문자열 응답이면 무시됨)
data class LoginUser(val id: String?)
data class LoginResponse(val ok: Boolean?, val token: String?, val user: LoginUser?)

interface AuthApi {
    @FormUrlEncoded
    @POST("login.php")
    suspend fun loginRaw(
        @Field("id") id: String,
        @Field("password") pw: String
    ): String
}
