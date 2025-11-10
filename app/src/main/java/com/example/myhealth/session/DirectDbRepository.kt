package com.example.myhealth.session

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.sql.DriverManager
import java.sql.Statement
import java.sql.Types
import java.time.LocalDate
class DirectDbRepository {

    private val host = "211.110.140.202"
    private val port = 3306
    private val db   = "cbnu2025"
    private val user = "cbnu2025"
    private val pass = "cbnu2025@"

    // MySQL 5.1.x 호환 URL
    private val jdbcUrl =
        "jdbc:mysql://$host:$port/$db" +
                "?useUnicode=true&characterEncoding=UTF-8" +
                "&useSSL=false" +
                "&connectTimeout=8000&socketTimeout=8000"

    /* ----------------------- ensure tables ----------------------- */

    private fun ensureWordTable(conn: java.sql.Connection) {
        val ddl = """
            CREATE TABLE IF NOT EXISTS app_word (
              id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
              login_id VARCHAR(128) NULL,
              rounds TINYINT UNSIGNED NOT NULL DEFAULT 5,
              avg_latency_ms INT UNSIGNED NOT NULL,
              valid_ratio DECIMAL(6,4) NOT NULL,
              total_ms INT UNSIGNED NOT NULL,
              started_at DATETIME NOT NULL,
              finished_at DATETIME NOT NULL,
              client VARCHAR(32) NOT NULL DEFAULT 'android',
              details_json LONGTEXT NULL,
              created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
              PRIMARY KEY (id),
              KEY idx_login_started (login_id, started_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
        """.trimIndent()
        conn.createStatement().use { it.execute(ddl) }
    }

    private fun ensureDiaryTable(conn: java.sql.Connection) {
        val ddl = """
            CREATE TABLE IF NOT EXISTS app_diary (
              id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
              login_id VARCHAR(128) NULL,
              title VARCHAR(200) NOT NULL,
              content LONGTEXT NOT NULL,
              photo_base64 LONGTEXT NULL,
              analysis_json LONGTEXT NULL,
              stt_text LONGTEXT NULL,
              recorded_sec INT UNSIGNED NOT NULL DEFAULT 15,
              created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
              PRIMARY KEY (id),
              KEY idx_login_created (login_id, created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
        """.trimIndent()
        conn.createStatement().use { it.execute(ddl) }
    }

    private fun ensureColoringTable(conn: java.sql.Connection) {
        val ddl = """
            CREATE TABLE IF NOT EXISTS app_coloring (
              id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
              login_id VARCHAR(128) NULL,
              template_id VARCHAR(40) NOT NULL,
              score TINYINT UNSIGNED NOT NULL,
              analysis_json LONGTEXT NULL,
              image_base64 LONGTEXT NULL,
              created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
              PRIMARY KEY (id),
              KEY idx_login_created (login_id, created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
        """.trimIndent()
        conn.createStatement().use { it.execute(ddl) }
    }

    private fun ensureSummaryTable(conn: java.sql.Connection) {
        val ddl = """
            CREATE TABLE IF NOT EXISTS app_daily_summary(
               id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
               login_id VARCHAR(128) NULL,
               summary_date DATE NOT NULL,
               total_score INT UNSIGNED NOT NULL,
               word_score INT UNSIGNED NOT NULL,
               diary_score INT UNSIGNED NOT NULL,
               color_score INT UNSIGNED NOT NULL,
               emotion_score INT UNSIGNED NOT NULL,
               cognition_score INT UNSIGNED NOT NULL,
               memory_score INT UNSIGNED NOT NULL,
               detail_json LONGTEXT NULL,
               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
               PRIMARY KEY (id),
               UNIQUE KEY uq_login_date (login_id, summary_date),
               KEY idx_date (summary_date)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
        """.trimIndent()
        conn.createStatement().use { it.execute(ddl) }
    }

    /* ----------------------- recent fetch ----------------------- */

    data class LastWord(val avgLatencyMs: Int, val validRatio: Float, val rounds: Int)
    data class LastDiary(val title: String, val content: String)
    data class LastColoring(val score: Int, val analysisJson: String?)

    suspend fun getLastWord(loginId: String?): LastWord? = withContext(Dispatchers.IO) {
        try {
            DriverManager.getConnection(jdbcUrl, user, pass).use { conn ->
                val sql = if (loginId.isNullOrBlank())
                    "SELECT avg_latency_ms, valid_ratio, rounds FROM app_word ORDER BY id DESC LIMIT 1"
                else
                    "SELECT avg_latency_ms, valid_ratio, rounds FROM app_word WHERE login_id=? ORDER BY id DESC LIMIT 1"
                conn.prepareStatement(sql).use { ps ->
                    if (!loginId.isNullOrBlank()) ps.setString(1, loginId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) LastWord(
                            avgLatencyMs = rs.getInt(1),
                            validRatio = rs.getFloat(2),
                            rounds = rs.getInt(3)
                        ) else null
                    }
                }
            }
        } catch (_: Throwable) { null }
    }

    suspend fun getLastDiary(loginId: String?): LastDiary? = withContext(Dispatchers.IO) {
        try {
            DriverManager.getConnection(jdbcUrl, user, pass).use { conn ->
                val sql = if (loginId.isNullOrBlank())
                    "SELECT title, content FROM app_diary ORDER BY id DESC LIMIT 1"
                else
                    "SELECT title, content FROM app_diary WHERE login_id=? ORDER BY id DESC LIMIT 1"
                conn.prepareStatement(sql).use { ps ->
                    if (!loginId.isNullOrBlank()) ps.setString(1, loginId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) LastDiary(rs.getString(1) ?: "", rs.getString(2) ?: "") else null
                    }
                }
            }
        } catch (_: Throwable) { null }
    }

    suspend fun getLastColoring(loginId: String?): LastColoring? = withContext(Dispatchers.IO) {
        try {
            DriverManager.getConnection(jdbcUrl, user, pass).use { conn ->
                val sql = if (loginId.isNullOrBlank())
                    "SELECT score, analysis_json FROM app_coloring ORDER BY id DESC LIMIT 1"
                else
                    "SELECT score, analysis_json FROM app_coloring WHERE login_id=? ORDER BY id DESC LIMIT 1"
                conn.prepareStatement(sql).use { ps ->
                    if (!loginId.isNullOrBlank()) ps.setString(1, loginId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) LastColoring(rs.getInt(1), rs.getString(2)) else null
                    }
                }
            }
        } catch (_: Throwable) { null }
    }

    /* ----------------------- inserts ----------------------- */

    suspend fun insertDiary(
        loginId: String?,
        title: String,
        content: String,
        photoBase64: String?,
        analysisJson: String?,
        sttText: String?,
        recordedSec: Int = 15
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            Class.forName("com.mysql.jdbc.Driver")
            var id = -1L
            DriverManager.getConnection(jdbcUrl, user, pass).use { conn ->
                conn.autoCommit = false
                ensureDiaryTable(conn)
                val sql = """
                    INSERT INTO app_diary
                    (login_id, title, content, photo_base64, analysis_json, stt_text, recorded_sec)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
                conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
                    if (loginId.isNullOrBlank()) ps.setNull(1, Types.VARCHAR) else ps.setString(1, loginId)
                    ps.setString(2, title)
                    ps.setString(3, content)
                    ps.setString(4, photoBase64)
                    ps.setString(5, analysisJson)
                    ps.setString(6, sttText)
                    ps.setInt(7, recordedSec)
                    ps.executeUpdate()
                    ps.generatedKeys.use { rs -> if (rs.next()) id = rs.getLong(1) }
                }
                conn.commit()
            }
            Result.success(id)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun insertAppWord(
        loginId: String?,
        rounds: Int,
        avgLatencyMs: Long,
        validRatio: Float,
        totalMs: Long,
        startedMs: Long,
        finishedMs: Long,
        detailsJson: String
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            Class.forName("com.mysql.jdbc.Driver")
            var generatedId = -1L
            DriverManager.getConnection(jdbcUrl, user, pass).use { conn ->
                conn.autoCommit = false
                ensureWordTable(conn)

                val sql = """
                    INSERT INTO app_word
                    (login_id, rounds, avg_latency_ms, valid_ratio, total_ms, started_at, finished_at, client, details_json)
                    VALUES (?, ?, ?, ?, ?, FROM_UNIXTIME(?/1000), FROM_UNIXTIME(?/1000), 'android', ?)
                """.trimIndent()

                conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
                    if (loginId.isNullOrBlank()) ps.setNull(1, Types.VARCHAR) else ps.setString(1, loginId)
                    ps.setInt(2, rounds)
                    ps.setLong(3, avgLatencyMs)
                    ps.setBigDecimal(4, BigDecimal.valueOf(validRatio.toDouble()))
                    ps.setLong(5, totalMs)
                    ps.setLong(6, startedMs)
                    ps.setLong(7, finishedMs)
                    ps.setString(8, detailsJson)
                    ps.executeUpdate()
                    ps.generatedKeys.use { rs -> if (rs.next()) generatedId = rs.getLong(1) }
                }

                conn.commit()
            }
            Result.success(generatedId)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun insertColoring(
        loginId: String?,
        templateId: String,
        score: Int,
        analysisJson: String?,
        imageBase64: String?
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            Class.forName("com.mysql.jdbc.Driver")
            var newId = -1L
            DriverManager.getConnection(jdbcUrl, user, pass).use { conn ->
                conn.autoCommit = false
                ensureColoringTable(conn)
                val sql = """
                    INSERT INTO app_coloring (login_id, template_id, score, analysis_json, image_base64)
                    VALUES (?, ?, ?, ?, ?)
                """.trimIndent()
                conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
                    if (loginId.isNullOrBlank()) ps.setNull(1, Types.VARCHAR) else ps.setString(1, loginId)
                    ps.setString(2, templateId)
                    ps.setInt(3, score)
                    ps.setString(4, analysisJson)
                    ps.setString(5, imageBase64)
                    ps.executeUpdate()
                    ps.generatedKeys.use { rs -> if (rs.next()) newId = rs.getLong(1) }
                }
                conn.commit()
            }
            Result.success(newId)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /* ----------------------- daily summary ----------------------- */

    data class DailySummaryRow(
        val date: java.time.LocalDate,
        val total: Int,
        val word: Int,
        val diary: Int,
        val color: Int,
        val emotion: Int,
        val cognition: Int,
        val memory: Int,
        val detailJson: String?
    )

    suspend fun upsertDailySummary(
        loginId: String?,
        date: java.time.LocalDate,
        total: Int,
        word: Int,
        diary: Int,
        color: Int,
        emotion: Int,
        cognition: Int,
        memory: Int,
        detailJson: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Class.forName("com.mysql.jdbc.Driver")
            DriverManager.getConnection(jdbcUrl, user, pass).use { conn ->
                conn.autoCommit = false
                ensureSummaryTable(conn)

                val sql = """
                    INSERT INTO app_daily_summary
                      (login_id, summary_date, total_score, word_score, diary_score, color_score,
                       emotion_score, cognition_score, memory_score, detail_json)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                      total_score=VALUES(total_score),
                      word_score=VALUES(word_score),
                      diary_score=VALUES(diary_score),
                      color_score=VALUES(color_score),
                      emotion_score=VALUES(emotion_score),
                      cognition_score=VALUES(cognition_score),
                      memory_score=VALUES(memory_score),
                      detail_json=VALUES(detail_json)
                """.trimIndent()

                conn.prepareStatement(sql).use { ps ->
                    if (loginId.isNullOrBlank()) ps.setNull(1, Types.VARCHAR) else ps.setString(1, loginId)
                    // ★ LocalDate -> String -> java.sql.Date
                    ps.setDate(2, java.sql.Date.valueOf(date.toString()))
                    ps.setInt(3, total)
                    ps.setInt(4, word)
                    ps.setInt(5, diary)
                    ps.setInt(6, color)
                    ps.setInt(7, emotion)
                    ps.setInt(8, cognition)
                    ps.setInt(9, memory)
                    ps.setString(10, detailJson)
                    ps.executeUpdate()
                }
                conn.commit()
            }
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun getDailySummaries(
        loginId: String?,
        start: java.time.LocalDate,
        end: java.time.LocalDate
    ): List<DailySummaryRow> = withContext(Dispatchers.IO) {
        val out = mutableListOf<DailySummaryRow>()
        try {
            DriverManager.getConnection(jdbcUrl, user, pass).use { conn ->
                ensureSummaryTable(conn)

                val sql = if (loginId.isNullOrBlank())
                    """SELECT summary_date,total_score,word_score,diary_score,color_score,
                              emotion_score,cognition_score,memory_score,detail_json
                       FROM app_daily_summary
                       WHERE summary_date BETWEEN ? AND ?
                       ORDER BY summary_date"""
                else
                    """SELECT summary_date,total_score,word_score,diary_score,color_score,
                              emotion_score,cognition_score,memory_score,detail_json
                       FROM app_daily_summary
                       WHERE login_id=? AND summary_date BETWEEN ? AND ?
                       ORDER BY summary_date"""

                conn.prepareStatement(sql).use { ps ->
                    var idx = 1
                    if (!loginId.isNullOrBlank()) {
                        ps.setString(idx++, loginId)
                    }
                    // ★ LocalDate -> String -> java.sql.Date
                    ps.setDate(idx++, java.sql.Date.valueOf(start.toString()))
                    ps.setDate(idx,   java.sql.Date.valueOf(end.toString()))


                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            val date = LocalDate.parse(rs.getString(1))  // ← 핵심 변경

                            out += DailySummaryRow(
                                date = date,                         // ← 여기 사용
                                total = rs.getInt(2),
                                word = rs.getInt(3),
                                diary = rs.getInt(4),
                                color = rs.getInt(5),
                                emotion = rs.getInt(6),
                                cognition = rs.getInt(7),
                                memory = rs.getInt(8),
                                detailJson = rs.getString(9)
                            )
                        }
                    }
                }
            }
        } catch (_: Throwable) { }
        out
    }
}
