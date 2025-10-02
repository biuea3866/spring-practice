package com.example.springpractice.practice1.common.model

data class LoggingModel(
    val traceId: String,
    val url: String,
    val method: String,
) {
}