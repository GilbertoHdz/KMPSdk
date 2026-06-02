@file:OptIn(ExperimentalJsExport::class)

package com.gilbertohdz.sdk

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.promise
import kotlin.js.Promise

// JS-friendly model wrappers (avoids @Serializable/@JsExport conflict)
@JsExport class JsUser(val id: Int, val name: String, val username: String, val email: String)
@JsExport class JsPost(val id: Int, val userId: Int, val title: String, val body: String)

private val scope = MainScope()

private val client by lazy {
    // Read optional OTLP endpoint via asDynamic() — more reliable than js() in K2 production builds.
    val endpoint: String? = try {
        kotlinx.browser.window.asDynamic().__OTEL_ENDPOINT__?.toString()
            ?.takeIf { it.startsWith("http") }
    } catch (e: Throwable) { null }

    SdkClient(SdkConfig(
        serviceName        = "kmpsdk-web",
        serviceVersion     = "1.0.0",
        useConsoleExporter = true,
        otlpEndpoint       = endpoint
    ))
}

@JsExport
fun sdkGetUsers(): Promise<Array<JsUser>> = scope.promise {
    client.getUsers().map { JsUser(it.id, it.name, it.username, it.email) }.toTypedArray()
}

@JsExport
fun sdkCreatePost(): Promise<JsPost> = scope.promise {
    val p = client.createPost(
        userId = 1,
        title  = "Web Post from KMP SDK",
        body   = "Created via OpenTelemetry-instrumented KMP SDK"
    )
    JsPost(p.id, p.userId, p.title, p.body)
}

@JsExport
fun sdkGetPostError(): Promise<JsPost> = scope.promise {
    val p = client.getPostById(99999)
    JsPost(p.id, p.userId, p.title, p.body)
}