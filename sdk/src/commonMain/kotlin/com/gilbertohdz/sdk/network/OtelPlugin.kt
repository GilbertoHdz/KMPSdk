package com.gilbertohdz.sdk.network

import com.gilbertohdz.sdk.telemetry.NoopSdkTracer
import com.gilbertohdz.sdk.telemetry.SdkSpan
import com.gilbertohdz.sdk.telemetry.SdkTracer
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.Url
import io.ktor.util.AttributeKey

private val SpanKey = AttributeKey<SdkSpan>("otel_http_span")

class OtelPluginConfig {
    var tracer: SdkTracer = NoopSdkTracer()
}

/**
 * Plugin de Ktor que intercepta cada request HTTP y crea un Span de OpenTelemetry.
 *
 * Atributos capturados por request:
 *   - http.request.method  (GET, POST, etc.)
 *   - url.full             (URL completa)
 *   - server.address       (host)
 *
 * Atributos capturados por response:
 *   - http.response.status_code
 *
 * Header inyectado (W3C TraceContext):
 *   traceparent: 00-{traceId}-{spanId}-01
 */
val OtelPlugin = createClientPlugin("OtelPlugin", ::OtelPluginConfig) {
    val tracer = pluginConfig.tracer

    onRequest { request, _ ->
        val method = request.method.value
        val fullUrlString = request.url.buildString()
        // Usa Url (inmutable) para acceder a encodedPath — API estable en Ktor 3.x
        val parsedUrl = Url(fullUrlString)
        val host = parsedUrl.host
        val path = parsedUrl.encodedPath

        val span = tracer.startSpan("$method $host$path")
        span.setAttribute("http.request.method", method)
        span.setAttribute("url.full", fullUrlString)
        span.setAttribute("server.address", host)

        // W3C TraceContext propagation hacia el servidor
        request.headers.append("traceparent", "00-${span.traceId}-${span.spanId}-01")

        request.attributes.put(SpanKey, span)
    }

    onResponse { response ->
        val span = response.call.request.attributes.getOrNull(SpanKey) ?: return@onResponse
        span.setAttribute("http.response.status_code", response.status.value.toLong())
        span.setStatus(isOk = response.status.value < 400)
        span.end()
    }
}