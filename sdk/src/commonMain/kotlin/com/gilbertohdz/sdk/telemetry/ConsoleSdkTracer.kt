package com.gilbertohdz.sdk.telemetry

import kotlin.random.Random

// Lightweight tracer that writes span info to stdout/console.
// iOS  → Xcode debug console (via println → NSLog)
// JS   → browser console.log
class ConsoleSdkTracer(private val platformName: String) : SdkTracer {
    override fun startSpan(name: String): SdkSpan = ConsoleSdkSpan(name, platformName)
}

private class ConsoleSdkSpan(
    private val name: String,
    private val platformName: String
) : SdkSpan {
    override val traceId: String = randomHex(32)
    override val spanId: String = randomHex(16)

    private val attributes = mutableMapOf<String, String>()
    private var statusOk = true
    private var statusMsg = ""

    override fun setAttribute(key: String, value: String) { attributes[key] = value }
    override fun setAttribute(key: String, value: Long)   { attributes[key] = value.toString() }

    override fun setStatus(isOk: Boolean, message: String) {
        statusOk = isOk
        statusMsg = message
    }

    override fun end() {
        val status = if (statusOk) "OK" else "ERROR"
        val attrs  = attributes.entries.joinToString(" | ") { "${it.key}=${it.value}" }
        val msg    = buildString {
            append("[OTel:$platformName] '$name' [$status]")
            append(" trace=${traceId.take(8)}… span=$spanId")
            if (attrs.isNotEmpty()) append(" | $attrs")
            if (!statusOk && statusMsg.isNotEmpty()) append(" | error=$statusMsg")
        }
        println(msg)
    }
}

internal fun randomHex(length: Int): String {
    val hex = "0123456789abcdef"
    return (1..length).map { hex[Random.nextInt(hex.length)] }.joinToString("")
}
