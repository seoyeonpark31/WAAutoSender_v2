package com.waauto

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Contact(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val phone: String  // 국가코드 포함: 821012345678
)

data class Group(
    val id: String,
    val name: String,
    val contacts: List<Contact> = emptyList(),
    val message: String = ""
)

object DataManager {
    private const val PREF_NAME = "waauto_prefs"
    private const val KEY_GROUPS = "groups_v2"
    private val gson = Gson()

    // 5개 고정 그룹 정의
    val PREDEFINED = listOf(
        Triple("aclass",   "Aclass",   "#2196F3"),
        Triple("bclass",   "Bclass",   "#4CAF50"),
        Triple("makeup_a", "makeup A", "#E91E63"),
        Triple("makeup_b", "makeup B", "#FF5722"),
        Triple("free",     "자유",      "#9C27B0")
    )

    fun getGroups(context: Context): List<Group> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_GROUPS, null) ?: return initDefaultGroups(context)
        return try {
            val type = object : TypeToken<List<Group>>() {}.type
            gson.fromJson(json, type) ?: initDefaultGroups(context)
        } catch (e: Exception) {
            initDefaultGroups(context)
        }
    }

    fun getGroup(context: Context, groupId: String): Group? =
        getGroups(context).find { it.id == groupId }

    fun saveGroup(context: Context, group: Group) {
        val groups = getGroups(context).toMutableList()
        val idx = groups.indexOfFirst { it.id == group.id }
        if (idx >= 0) groups[idx] = group else groups.add(group)
        saveAllGroups(context, groups)
    }

    fun saveAllGroups(context: Context, groups: List<Group>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_GROUPS, gson.toJson(groups)).apply()
    }

    private fun initDefaultGroups(context: Context): List<Group> {
        val defaults = PREDEFINED.map { (id, name, _) -> Group(id = id, name = name) }
        saveAllGroups(context, defaults)
        return defaults
    }

    fun getDelayBetweenMessages(): Long = 4000L

    // ─── Google Sheets 연동 ───────────────────────────────────────

    private const val KEY_SHEET_URL = "sheet_script_url"

    fun saveSheetUrl(context: Context, url: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SHEET_URL, url).apply()
    }

    fun getSheetUrl(context: Context): String =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SHEET_URL, "") ?: ""

    data class SyncResult(val success: Boolean, val message: String, val synced: Int = 0)

    // 클래스 이름 → 그룹 ID 매핑
    private fun classToGroupId(classType: String): String? = when (classType.uppercase().trim()) {
        "A" -> "aclass"
        "B" -> "bclass"
        "MAKEUP A", "MAKEUP_A" -> "makeup_a"
        "MAKEUP B", "MAKEUP_B" -> "makeup_b"
        else -> null
    }

    suspend fun syncFromSheet(context: Context): SyncResult = withContext(Dispatchers.IO) {
        val url = getSheetUrl(context)
        if (url.isBlank()) return@withContext SyncResult(false, "시트 URL을 먼저 설정해주세요\n(🔗 URL 설정 버튼 클릭)")

        return@withContext try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/json")

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                return@withContext SyncResult(false, "서버 응답 오류: $responseCode")
            }

            val raw = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(raw)

            if (!json.optBoolean("success", false)) {
                return@withContext SyncResult(false, json.optString("error", "응답 오류"))
            }

            val array = json.getJSONArray("contacts")

            // 그룹별 연락처 분류
            val grouped = mutableMapOf<String, MutableList<Contact>>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val name  = item.optString("name").trim()
                val phone = item.optString("phone").trim()
                val cls   = item.optString("classType").trim()
                val groupId = classToGroupId(cls) ?: continue
                if (name.isBlank() || phone.isBlank()) continue
                grouped.getOrPut(groupId) { mutableListOf() }
                    .add(Contact(id = System.currentTimeMillis() + i, name = name, phone = phone))
            }

            if (grouped.isEmpty()) {
                return@withContext SyncResult(false, "매핑된 연락처가 없어요.\n클래스가 A 또는 B인지 확인해주세요.")
            }

            // 그룹 업데이트 (기존 메시지는 유지, 연락처만 교체)
            val groups = getGroups(context).toMutableList()
            var totalSynced = 0
            grouped.forEach { (groupId, contacts) ->
                val idx = groups.indexOfFirst { it.id == groupId }
                if (idx >= 0) {
                    groups[idx] = groups[idx].copy(contacts = contacts)
                    totalSynced += contacts.size
                }
            }
            saveAllGroups(context, groups)

            val summary = grouped.entries.joinToString(", ") { (id, list) ->
                val name = PREDEFINED.find { it.first == id }?.second ?: id
                "$name ${list.size}명"
            }
            SyncResult(true, "✅ 동기화 완료!\n$summary", totalSynced)

        } catch (e: Exception) {
            SyncResult(false, "❌ 오류: ${e.message}")
        }
    }
}
