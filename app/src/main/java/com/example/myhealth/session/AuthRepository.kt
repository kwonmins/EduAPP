package com.example.myhealth.session

import android.app.Application
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

class AuthRepository(app: Application) {
    private val tag = "AuthRepository"
    private val gson: Gson = GsonBuilder().setLenient().create()

    private val api: AuthApi by lazy {
        val logger = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder()
            .addInterceptor(logger)
            // 🔧 IP 사용 시 가상호스트 라우팅 위해 Host 헤더 강제
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Host", ApiConst.VHOST_HOSTNAME)
                    .build()
                chain.proceed(req)
            }
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        Retrofit.Builder()
            .baseUrl(ApiConst.BASE_URL)
            .client(client)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(AuthApi::class.java)
    }

    private val store = SessionDataStore(app)
    val userIdFlow = store.userIdFlow

    private fun parseUserIdFromAnything(body: String): String? {
        if (body.trim().startsWith("{")) {
            runCatching {
                val obj = gson.fromJson(body, LoginResponse::class.java)
                if (obj.ok == true || !obj.token.isNullOrEmpty() || obj.user?.id != null)
                    return obj.user?.id
            }.onFailure { Log.w(tag, "JSON parse failed: ${it.message}") }
        }
        Regex("""(?i)(ok|success|1)\W*([A-Za-z0-9_@.\-]+)?""").find(body)?.let { m ->
            val uid = m.groupValues.getOrNull(2); if (!uid.isNullOrBlank()) return uid
        }
        Regex("""id\s*=\s*([A-Za-z0-9_@.\-]+)""").find(body)?.let { return it.groupValues[1] }
        return null
    }

    suspend fun login(id: String, pw: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = api.loginRaw(id, pw)
            Log.d(tag, "loginRaw response: $body")
            val uid = parseUserIdFromAnything(body) ?: id
            val looksFail = body.contains("fail", true) || body.contains("error", true) || body.contains("invalid", true)
            if (!looksFail && uid.isNotBlank()) { store.setUserId(uid); Result.success(uid) }
            else Result.failure(IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다."))
        } catch (e: Exception) {
            Log.e(tag, "login error", e)
            Result.failure(Exception("서버 연결 실패: ${e.localizedMessage ?: e.javaClass.simpleName}"))
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) { store.clear() }
}
