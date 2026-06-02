@file:OptIn(io.ktor.utils.io.InternalAPI::class)

package com.gilbertohdz.sdk.network

import io.ktor.client.HttpClientConfig
import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpReceivePipeline
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import kotlin.coroutines.CoroutineContext

// The browser Fetch API transparently decompresses gzip responses but keeps the original
// Content-Length header (compressed size). Ktor validates body size after reading and throws
// "Content-Length mismatch" when it reads more bytes than declared.
// For cross-origin (CORS) requests, the browser also hides Content-Encoding from JS — so
// we cannot detect gzip via headers. Strip Content-Length whenever it is present so Ktor
// skips the post-read size validation entirely.
actual fun HttpClientConfig<*>.applyPlatformConfig() {
    install(createClientPlugin("BrowserGzipFix") {
        client.receivePipeline.intercept(HttpReceivePipeline.Before) { response ->
            if (response.headers.contains(HttpHeaders.ContentLength)) {
                proceedWith(object : HttpResponse() {
                    override val call: HttpClientCall get() = response.call
                    override val coroutineContext: CoroutineContext get() = response.coroutineContext
                    override val status: HttpStatusCode get() = response.status
                    override val version: HttpProtocolVersion get() = response.version
                    override val requestTime: GMTDate get() = response.requestTime
                    override val responseTime: GMTDate get() = response.responseTime
                    override val rawContent: ByteReadChannel get() = response.rawContent
                    override val headers: Headers = Headers.build {
                        response.headers.forEach { key, values ->
                            if (!key.equals(HttpHeaders.ContentLength, ignoreCase = true)) {
                                appendAll(key, values)
                            }
                        }
                    }
                })
            }
        }
    })
}