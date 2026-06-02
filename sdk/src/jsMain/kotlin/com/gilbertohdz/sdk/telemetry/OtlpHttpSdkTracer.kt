package com.gilbertohdz.sdk.telemetry

import kotlin.js.Date

// Sends spans to an OTLP HTTP endpoint (JSON format) via window.fetch().
// Used when SdkConfig.otlpEndpoint is set on the JS target.
internal class OtlpHttpSdkTracer(
    private val serviceName: String,
    private val serviceVersion: String,
    private val otlpEndpoint: String,
    private val consoleEnabled: Boolean
) : SdkTracer {
    override fun startSpan(name: String): SdkSpan = OtlpHttpSdkSpan(
        name = name,
        serviceName = serviceName,
        serviceVersion = serviceVersion,
        otlpEndpoint = otlpEndpoint,
        consoleEnabled = consoleEnabled
    )
}

private class OtlpHttpSdkSpan(
    private val name: String,
    private val serviceName: String,
    private val serviceVersion: String,
    private val otlpEndpoint: String,
    private val consoleEnabled: Boolean
) : SdkSpan {
    override val traceId: String = randomHex(32)
    override val spanId: String = randomHex(16)

    // Date.now() → milliseconds as Double; toLong() is safe for current epoch values
    private val startMs: Long = Date.now().toLong()
    private val attributes = mutableMapOf<String, String>()
    private var statusOk = true
    private var statusMsg = ""

    override fun setAttribute(key: String, value: String) { attributes[key] = value }
    override fun setAttribute(key: String, value: Long) { attributes[key] = value.toString() }

    override fun setStatus(isOk: Boolean, message: String) {
        statusOk = isOk
        statusMsg = message
    }

    override fun end() {
        val endMs = Date.now().toLong()
        if (consoleEnabled) printToConsole()
        exportOtlp(endMs)
    }

    private fun printToConsole() {
        val status = if (statusOk) "OK" else "ERROR"
        val attrs = attributes.entries.joinToString(" | ") { "${it.key}=${it.value}" }
        println(buildString {
            append("[OTel:JS] '$name' [$status]")
            append(" trace=${traceId.take(8)}… span=$spanId")
            if (attrs.isNotEmpty()) append(" | $attrs")
            if (!statusOk && statusMsg.isNotEmpty()) append(" | error=$statusMsg")
        })
    }

    private fun exportOtlp(endMs: Long) {
        // Append 6 zeros to convert ms → ns without precision loss (no arithmetic on large Longs)
        val startNs = "${startMs}000000"
        val endNs   = "${endMs}000000"

        // OTLP status: 0 = UNSET, 1 = OK, 2 = ERROR
        val statusCode = if (statusOk) 1 else 2

        val attrsJson = attributes.entries.joinToString(",") { (k, v) ->
            """{"key":"${k.escJson()}","value":{"stringValue":"${v.escJson()}"}}"""
        }
        val statusJson = buildString {
            append("""{"code":$statusCode""")
            if (!statusOk && statusMsg.isNotEmpty()) append(""","message":"${statusMsg.escJson()}"""")
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

        otlpFetch(otlpEndpoint, body)
    }

    private fun String.escJson() = replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}

// Fire-and-forget OTLP/HTTP JSON POST via window.fetch().
// Uses an IIFE so url/body are passed as JS arguments — avoids K2 production-build
// variable mangling that can break direct js() variable references.
private fun otlpFetch(url: String, body: String) {
    // language=JavaScript
    val send: dynamic = js("(function(u,b){fetch(u,{method:'POST',headers:{'Content-Type':'application/json'},body:b}).catch(function(e){console.warn('[OTel:JS] OTLP export failed:',e.message)})})")
    send(url, body)
}