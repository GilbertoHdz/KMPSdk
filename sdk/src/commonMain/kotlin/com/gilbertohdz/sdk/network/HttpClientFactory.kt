package com.gilbertohdz.sdk.network

import com.gilbertohdz.sdk.telemetry.SdkTracer
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

fun createHttpClient(tracer: SdkTracer): HttpClient = HttpClient {
    // Throws ClientRequestException for 4xx and ServerResponseException for 5xx
    expectSuccess = true

    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
        })
    }
    install(OtelPlugin) {
        this.tracer = tracer
    }

    applyPlatformConfig()
}