package com.gilbertohdz.sdk.telemetry

actual fun initializeTelemetry(
    serviceName: String,
    serviceVersion: String,
    useConsoleExporter: Boolean,
    otlpEndpoint: String?
): SdkTracer = when {
    otlpEndpoint != null -> OtlpHttpSdkTracer(
        serviceName    = serviceName,
        serviceVersion = serviceVersion,
        otlpEndpoint   = otlpEndpoint,
        consoleEnabled = useConsoleExporter
    )
    useConsoleExporter   -> ConsoleSdkTracer("JS")
    else                 -> NoopSdkTracer()
}
