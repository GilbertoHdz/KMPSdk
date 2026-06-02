package com.gilbertohdz.sdk.telemetry

interface SdkSpan {
    val traceId: String
    val spanId: String
    fun setAttribute(key: String, value: String)
    fun setAttribute(key: String, value: Long)
    fun setStatus(isOk: Boolean, message: String = "")
    fun end()
}

interface SdkTracer {
    fun startSpan(name: String): SdkSpan
    fun close() {}
}

class NoopSdkSpan : SdkSpan {
    override val traceId: String = "00000000000000000000000000000000"
    override val spanId: String = "0000000000000000"
    override fun setAttribute(key: String, value: String) {}
    override fun setAttribute(key: String, value: Long) {}
    override fun setStatus(isOk: Boolean, message: String) {}
    override fun end() {}
}

class NoopSdkTracer : SdkTracer {
    override fun startSpan(name: String): SdkSpan = NoopSdkSpan()
}

expect fun initializeTelemetry(
    serviceName: String,
    serviceVersion: String,
    useConsoleExporter: Boolean,
    otlpEndpoint: String?
): SdkTracer