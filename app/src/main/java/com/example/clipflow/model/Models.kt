package com.example.clipflow.model
data class SavedServer(val name: String, val ip: String, val pin: String)
data class HistoryItem(val type: String, val content: String, val time: String, val uriString: String? = null)