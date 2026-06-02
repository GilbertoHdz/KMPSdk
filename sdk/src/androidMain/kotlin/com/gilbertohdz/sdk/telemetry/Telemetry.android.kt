package com.gilbertohdz.sdk.telemetry

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.exporter.logging.LoggingSpanExporter
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor

actual fun initializeTelemetry(
    serviceName: String,
    serviceVersion: String,
    useConsoleExporter: Boolean,
    otlpEndpoint: String?
): SdkTracer {
    val resource = Resource.getDefault().merge(
        Resource.create(
            Attributes.of(
                AttributeKey.stringKey("service.name"), serviceName,
                AttributeKey.stringKey("service.version"), serviceVersion
            )
        )
    )

    val providerBuilder = SdkTracerProvider.builder().setResource(resource)

    if (useConsoleExporter) {
        providerBuilder.addSpanProcessor(
            SimpleSpanProcessor.create(LoggingSpanExporter.create())
        )
    }

    otlpEndpoint?.let { endpoint ->
        val exporter = OtlpHttpSpanExporter.builder()
            .setEndpoint(endpoint)
            .build()
        providerBuilder.addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
    }

    val tracerProvider = providerBuilder.build()

    val openTelemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
        .build()

    val otelTracer = openTelemetry.getTracer(serviceName)

    return object : SdkTracer {
        override fun startSpan(name: String): SdkSpan {
            val span = otelTracer.spanBuilder(name).startSpan()
            return object : SdkSpan {
                override val traceId: String get() = span.spanContext.traceId
                override val spanId: String get() = span.spanContext.spanId

                override fun setAttribute(key: String, value: String) {
                    span.setAttribute(key, value)
                }

                override fun setAttribute(key: String, value: Long) {
                    span.setAttribute(AttributeKey.longKey(key), value)
                }

                override fun setStatus(isOk: Boolean, message: String) {
                    if (isOk) span.setStatus(StatusCode.OK)
                    else span.setStatus(StatusCode.ERROR, message)
                }

                override fun end() = span.end()
            }
        }

        override fun close() {
            tracerProvider.shutdown().join(5, java.util.concurrent.TimeUnit.SECONDS)
        }
    }
}