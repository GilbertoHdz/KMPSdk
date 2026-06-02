package com.gilbertohdz.sdk.telemetry

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import platform.posix.gettimeofday
import platform.posix.timeval

// Sends spans to an OTLP HTTP endpoint via Ktor Darwin engine.
// A dedicated HttpClient is created WITHOUT OtelPlugin to avoid recursive instrumentation.
internal class OtlpHttpSdkTracer(
    private val serviceName: String,
    private val serviceVersion: String,
    private val otlpEndpoint: String,
    private val consoleEnabled: Boolean
) : SdkTracer {

    // No ContentNegotiation / OtelPlugin — raw POST only, no recursive tracing.
    // expectSuccess = false so a non-2xx from the collector doesn't throw.
    private val httpClient by lazy {
        HttpClient(Darwin) { expectSuccess = false }
    }

    override fun startSpan(name: String): SdkSpan = OtlpHttpSdkSpan(
        name           = name,
        serviceName    = serviceName,
        serviceVersion = serviceVersion,
        otlpEndpoint   = otlpEndpoint,
        consoleEnabled = consoleEnabled,
        httpClient     = httpClient
    )

    override fun close() = httpClient.close()
}

private class OtlpHttpSdkSpan(
    private val name: String,
    private val serviceName: String,
    private val serviceVersion: String,
    private val otlpEndpoint: String,
    private val consoleEnabled: Boolean,
    private val httpClient: HttpClient
) : SdkSpan {
    override val traceId: String = randomHex(32)
    override val spanId: String  = randomHex(16)

    private val startMs: Long    = nowMillis()
    private val attributes       = mutableMapOf<String, String>()
    private var statusOk  = true
    private var statusMsg = ""

    override fun setAttribute(key: String, value: String) { attributes[key] = value }
    override fun setAttribute(key: String, value: Long)   { attributes[key] = value.toString() }

    override fun setStatus(isOk: Boolean, message: String) {
        statusOk  = isOk
        statusMsg = message
    }

    override fun end() {
        val endMs = nowMillis()
        if (consoleEnabled) printToConsole()
        exportOtlp(endMs)
    }

    private fun printToConsole() {
        val status = if (statusOk) "OK" else "ERROR"
        val attrs  = attributes.entries.joinToString(" | ") { "${it.key}=${it.value}" }
        println(buildString {
            append("[OTel:iOS] '$name' [$status]")
            append(" trace=${traceId.take(8)}… span=$spanId")
            if (attrs.isNotEmpty()) append(" | $attrs")
            if (!statusOk && statusMsg.isNotEmpty()) append(" | error=$statusMsg")
        })
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun exportOtlp(endMs: Long) {
        val startNs    = "${startMs}000000"
        val endNs      = "${endMs}000000"
        val statusCode = if (statusOk) 1 else 2

        val attrsJson = attributes.entries.joinToString(",") { (k, v) ->
            """{"key":"${k.escJson()}","value":{"stringValue":"${v.escJson()}"}}"""
        }
        val statusJson = buildString {
            append("""{"code":$statusCode""")
            if (!statusOk && statusMsg.isNotEmpty()) {
                append(""","message":"${statusMsg.escJson()}"""")
            }
            append("}")
        }

        val body = """
{
  "resourceSpans": [{
    "resource": {
      "attributes": [
        {"key":"service.name","value":{"stringValue":"${serviceName.escJson()}"}},
        {"key":"service.version","value":{"stringValue":"${serviceVersion.escJson()}"}}
      ]
    },
    "scopeSpans": [{
      "scope": {"name":"${serviceName.escJson()}"},
      "spans": [{
        "traceId": "$traceId",
        "spanId":  "$spanId",
        "name":    "${name.escJson()}",
        "kind":    1,
        "startTimeUnixNano": "$startNs",
        "endTimeUnixNano":   "$endNs",
        "attributes": [$attrsJson],
        "status": $statusJson
      }]
    }]
  }]
}""".trimIndent()

        // Fire-and-forget — telemetry must never block or crash the host app
        GlobalScope.launch {
            try {
                httpClient.post(otlpEndpoint) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            } catch (e: Exception) {
                println("[OTel:iOS] OTLP export failed: ${e.message}")
            }
        }
    }

    private fun String.escJson() = replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}

// Wall-clock milliseconds via POSIX gettimeofday — available on all Apple targets.
@OptIn(ExperimentalForeignApi::class)
private fun nowMillis(): Long = memScoped {
    val tv = alloc<timeval>()
    gettimeofday(tv.ptr, null)
    tv.tv_sec * 1_000L + tv.tv_usec / 1_000L
}