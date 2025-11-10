package com.example.myhealth.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore("session_prefs")

object SessionKeys {
    val USER_ID = stringPreferencesKey("user_id")
}

class SessionDataStore(private val context: Context) {
    val userIdFlow: Flow<String?> = context.sessionDataStore.data.map { it[SessionKeys.USER_ID] }
    suspend fun setUserId(id: String) = context.sessionDataStore.edit { it[SessionKeys.USER_ID] = id }
    suspend fun clear() = context.sessionDataStore.edit { it.remove(SessionKeys.USER_ID) }
}
