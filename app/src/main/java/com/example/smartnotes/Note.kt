package com.example.smartnotes

data class Note(
    var id: String = "",
    var title: String = "",
    var description: String = "",
    var timestamp: Long = System.currentTimeMillis(),
    var userId: String = ""
)