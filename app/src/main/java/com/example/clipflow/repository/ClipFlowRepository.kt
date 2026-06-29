package com.example.clipflow.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.clipflow.model.HistoryItem
import com.example.clipflow.model.SavedServer
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClipFlowRepository(context: Context) {
    // Dùng SharedPreferences để lưu trữ dữ liệu
    private val sharedPref: SharedPreferences = context.getSharedPreferences("ClipFlowPrefs", Context.MODE_PRIVATE)
    private val gson = Gson()
// Kiểm tra phải FirstLaunch không để hiện hướng dẫn
    fun isFirstLaunch(): Boolean {
        val isFirst = sharedPref.getBoolean("FIRST_LAUNCH", true)
        if (isFirst) sharedPref.edit().putBoolean("FIRST_LAUNCH", false).apply()
        return isFirst
    }
// Lưu Ip và Pin của PC
    fun getPcIp(): String = sharedPref.getString("PC_IP", "") ?: ""

    fun getPcPin(): String = sharedPref.getString("PC_PIN", "") ?: ""

    fun savePcConnection(ip: String, pin: String = "") {
        sharedPref.edit()
            .putString("PC_IP", ip)
            .putString("PC_PIN", pin)
            .apply()
    }

    fun clearPcConnection() {
        sharedPref.edit()
            .remove("PC_IP")
            .remove("PC_PIN")
            .apply()
    }
// Gson giúp chuyển đổi giữa đối tượng và chuỗi JSON vì SharedPreferences chỉ lưu trữ chuỗi
    fun getSavedServers(): MutableList<SavedServer> {
        val json = sharedPref.getString("SAVED_SERVERS", "[]")
        val type = object : TypeToken<MutableList<SavedServer>>() {}.type
        return gson.fromJson(json, type)
    }
// Lưu danh sách máy chủ đã lưu
    fun saveServers(servers: List<SavedServer>) {
        sharedPref.edit().putString("SAVED_SERVERS", gson.toJson(servers)).apply()
    }
// Lưu danh sách lịch sử
    fun getHistory(ip: String): MutableList<HistoryItem> {
        val historyKey = if (ip.isNotEmpty()) "HISTORY_$ip" else "HISTORY_GENERAL"
        val json = sharedPref.getString(historyKey, "[]")
        val typeToken = object : TypeToken<MutableList<HistoryItem>>() {}.type
        return gson.fromJson(json, typeToken)
    }
// Thêm lịch sử
// Dùng SimpleDateFormat để định dạng thời gian
    fun addHistory(ip: String, type: String, content: String, uriString: String? = null) {
        val historyList = getHistory(ip)
        val time = SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault()).format(Date())
        historyList.add(0, HistoryItem(type, content, time, uriString))
        if (historyList.size > 50) historyList.removeAt(50) // App lưu 50 lịch sử

        val historyKey = if (ip.isNotEmpty()) "HISTORY_$ip" else "HISTORY_GENERAL"
        sharedPref.edit().putString(historyKey, gson.toJson(historyList)).apply()
    }
}