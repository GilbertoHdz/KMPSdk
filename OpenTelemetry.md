# OpenTelemetry in KMP SDK

This document explains how OpenTelemetry tracing is implemented in this Kotlin Multiplatform SDK, how to run the local web demo, and how to visualize traces in **Jaeger** using Docker.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [SDK Configuration](#sdk-configuration)
- [Platform Support](#platform-support)
  - [Android](#android)
  - [iOS](#ios)
  - [JavaScript / Browser](#javascript--browser)
- [Running the Web Demo](#running-the-web-demo)
  - [Mode 1 — Local (console only)](#mode-1--local-console-only)
  - [Mode 2 — Jaeger (Docker)](#mode-2--jaeger-docker)
- [Jaeger UI](#jaeger-ui)
- [How Spans Are Structured](#how-spans-are-structured)
- [Key Implementation Details](#key-implementation-details)

---

## Overview

Every HTTP call made through the SDK is automatically instrumented with an OpenTelemetry span. Each span captures:

| Attribute | Example |
|---|---|
| `http.request.method` | `GET` |
| `url.full` | `https://jsonplaceholder.typicode.com/users` |
| `server.address` | `jsonplaceholder.typicode.com` |
| `http.response.status_code` | `200` |

The SDK also creates higher-level **operation spans** that wrap the full business logic call, including error details if one occurs.

W3C `traceparent` headers are injected into every outbound request for end-to-end distributed tracing compatibility.

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                      KMP SDK                             │
│                                                          │
│  SdkClient                                               │
│    startSpan("sdk.getUsers")                             │
│      → Ktor HTTP client                                  │
│          OtelPlugin (auto-instrumented per request)      │
│            startSpan("GET /users")                       │
│            inject traceparent header                     │
│          → jsonplaceholder.typicode.com                  │
└──────────────────┬───────────────────────────────────────┘
                   │ OTLP / HTTP  :4318
                   ▼
┌──────────────────────────────────┐
│   OTel Collector                 │
│   (otel/collector-contrib)       │
│   CORS enabled for browser       │
└──────────────┬───────────────────┘
               │ OTLP / HTTP  (internal Docker network)
               ▼
┌──────────────────────────────────┐
│   Jaeger all-in-one              │
│   UI → http://localhost:16686    │
└──────────────────────────────────┘
```

### Host addresses by platform

| Platform | Reaches host at |
|---|---|
| Android emulator | `10.0.2.2` |
| iOS simulator | `localhost` |
| iOS physical device | Local IP (e.g. `192.168.x.x`) |
| Browser JS | `localhost` |

---

## SDK Configuration

```kotlin
data class SdkConfig(
    val serviceName: String        = "kmpsdk",
    val serviceVersion: String     = "1.0.0",
    val baseUrl: String            = "https://jsonplaceholder.typicode.com/",
    val useConsoleExporter: Boolean = true,   // print spans to stdout / Logcat / Xcode console
    val otlpEndpoint: String?      = null     // null = console only
)
```

| `otlpEndpoint` | `useConsoleExporter` | Behavior |
|---|---|---|
| `null` | `true` | Spans printed to console only |
| `null` | `false` | No-op (traces disabled) |
| set | `true` | Spans sent to collector **and** printed |
| set | `false` | Spans sent to collector only |

---

## Platform Support

### Android

Uses the **OpenTelemetry Java SDK** (`io.opentelemetry:opentelemetry-sdk:1.62.0`).

Supports both exporters:
- **Logging** — `LoggingSpanExporter` (stdout / Logcat)
- **OTLP HTTP** — `OtlpHttpSpanExporter` with batch processing

```kotlin
// ViewModel / Application
val client = SdkClient(
    SdkConfig(
        serviceName  = "my-android-app",
        otlpEndpoint = "http://10.0.2.2:4318/v1/traces"  // emulator → host
    )
)
```

The app's `AndroidManifest.xml` references a `network_security_config.xml` that allows cleartext HTTP only to `10.0.2.2`, so production traffic remains TLS-only.

---

### iOS

Uses a lightweight **OTLP HTTP JSON exporter** built on top of **Ktor Darwin** engine.

- When `otlpEndpoint` is `null` → spans are printed to the Xcode console with the `[OTel:iOS]` tag.
- When `otlpEndpoint` is set → spans are POSTed as OTLP JSON via `GlobalScope.launch { httpClient.post(...) }` (fire-and-forget). Console output is preserved when `useConsoleExporter = true`.

Timestamps use `platform.posix.gettimeofday` (millisecond precision, converted to nanoseconds).

A dedicated `HttpClient(Darwin)` is created **without** `OtelPlugin` to avoid recursive instrumentation.

```swift
// Swift ViewModel
let client = SdkClient(config: SdkConfig(
    serviceName: "my-ios-app",
    serviceVersion: "1.0.0",
    baseUrl: "https://jsonplaceholder.typicode.com/",
    useConsoleExporter: true,
    otlpEndpoint: "http://localhost:4318/v1/traces"      // simulator
    // otlpEndpoint: "http://192.168.x.x:4318/v1/traces" // physical device
))
```

**ATS configuration** — iOS blocks HTTP by default. Add to your app's `Info.plist`:

```xml
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSExceptionDomains</key>
    <dict>
        <key>localhost</key>
        <dict>
            <key>NSExceptionAllowsInsecureHTTPLoads</key>
            <true/>
        </dict>
    </dict>
</dict>
```

---

### JavaScript / Browser

Uses a custom **OTLP HTTP JSON exporter** built on top of `window.fetch()` via an IIFE pattern (avoids Kotlin/JS K2 production-build variable mangling).

- When `otlpEndpoint` is `null` → spans are printed to DevTools console with the `[OTel:JS]` tag.
- When `otlpEndpoint` is set → spans are POSTed to the collector. Console output is preserved alongside when `useConsoleExporter = true`.
- The endpoint is read from `window.__OTEL_ENDPOINT__` (set by `index.html` before the SDK module loads).

**Browser / CORS note:** The browser Fetch API transparently decompresses gzip responses but keeps the original `Content-Length` header (compressed size). The SDK includes `BrowserGzipFix` — a Ktor plugin that strips `Content-Length` from the response before Ktor validates body size, preventing a false "Content-Length mismatch" error on cross-origin JSON calls.

---

## Running the Web Demo

Prerequisites: **Python 3** (bundled with macOS), **Gradle**, and optionally **Docker**.

### Mode 1 — Local (console only)

No Docker required. Traces go to the browser DevTools console.

```bash
bash examples/web/kmpsdk/kmplocal/serve.sh
```

Opens: `http://localhost:8080/examples/web/kmpsdk/kmplocal/`

Open **DevTools → Console** and filter by `OTel:JS`.

```
[OTel:JS] 'GET jsonplaceholder.typicode.com/users' [OK]
  trace=c5f313bd… span=955f6502…
  http.request.method=GET | http.response.status_code=200

[OTel:JS] 'sdk.getUsers' [OK]
  trace=e2f6c247… span=27cd9813…
  result.count=10
```

---

### Mode 2 — Jaeger (Docker)

Requires **Docker Desktop** running.

```bash
bash examples/web/kmpsdk/kmplocal/serve-jaeger.sh
```

This script:
1. Verifies Docker is running
2. Frees port 8080 if occupied
3. Starts OTel Collector + Jaeger via `docker compose up -d`
4. Waits for the collector to be ready on `:4318`
5. Builds the JS SDK
6. Opens the demo at `http://localhost:8080/.../?jaeger=1`
7. Opens Jaeger UI at `http://localhost:16686`

The `?jaeger=1` query parameter activates OTLP export — no code change required.

**To stop:**

```bash
# Ctrl+C stops the web server
cd docker && docker compose down   # stops the Docker stack
```

**Manual Docker commands:**

```bash
cd docker
docker compose up -d                        # start
docker compose ps                           # status
docker logs $(docker ps --filter "name=otel-collector" -q) --tail 30   # collector logs
docker compose down                         # stop
```

---

## Jaeger UI

After running `serve-jaeger.sh` (or pointing Android/iOS to the collector) and triggering some calls:

1. Open **http://localhost:16686**
2. In the **Service** dropdown → select your service (`kmpsdk-web`, `kmpsdk-android`, `kmpsdk-ios`)
3. Click **Find Traces**
4. Click any trace to expand the span timeline

---

## How Spans Are Structured

```
sdk.getUsers
├── attributes
│   └── result.count = 10
└── status: OK

GET jsonplaceholder.typicode.com/users
├── attributes
│   ├── http.request.method       = GET
│   ├── url.full                  = https://jsonplaceholder.typicode.com/users
│   ├── server.address            = jsonplaceholder.typicode.com
│   └── http.response.status_code = 200
└── status: OK
```

Error example (`getPostById(99999)`):

```
sdk.getPostById
├── attributes
│   ├── post.id.requested = 99999
│   ├── error.type        = ClientRequestException
│   └── error.message     = Client request ... invalid: 404
└── status: ERROR

GET jsonplaceholder.typicode.com/posts/99999
├── attributes
│   └── http.response.status_code = 404
└── status: ERROR
```

---

## Key Implementation Details

| File | Purpose |
|---|---|
| `sdk/src/commonMain/.../telemetry/Telemetry.kt` | `SdkSpan` / `SdkTracer` interfaces + `NoopSdkTracer` |
| `sdk/src/commonMain/.../telemetry/ConsoleSdkTracer.kt` | Lightweight console tracer (shared by all targets when no endpoint is set) |
| `sdk/src/androidMain/.../telemetry/Telemetry.android.kt` | Full OTel Java SDK — OTLP HTTP batch exporter + Logging exporter |
| `sdk/src/iosMain/.../telemetry/Telemetry.ios.kt` | iOS `initializeTelemetry` — routes to `OtlpHttpSdkTracer` or `ConsoleSdkTracer` |
| `sdk/src/iosMain/.../telemetry/OtlpHttpSdkTracer.kt` | iOS OTLP exporter — Ktor Darwin engine, `gettimeofday` timestamps, `GlobalScope` fire-and-forget |
| `sdk/src/jsMain/.../telemetry/Telemetry.js.kt` | JS `initializeTelemetry` — routes to `OtlpHttpSdkTracer` or `ConsoleSdkTracer` |
| `sdk/src/jsMain/.../telemetry/OtlpHttpSdkTracer.kt` | JS OTLP exporter — `window.fetch()` via IIFE, `Date.now()` timestamps |
| `sdk/src/commonMain/.../network/OtelPlugin.kt` | Ktor plugin — auto-instruments every HTTP request with a span |
| `sdk/src/commonMain/.../network/HttpClientFactory.kt` | Wires `OtelPlugin` into the Ktor HTTP client |
| `sdk/src/jsMain/.../network/PlatformHttpConfig.js.kt` | `BrowserGzipFix` — strips `Content-Length` to avoid CORS/gzip Content-Length mismatch |
| `docker/docker-compose.yml` | OTel Collector + Jaeger services |
| `docker/otel-collector.yaml` | Collector config — CORS for browser, batch processor, OTLP HTTP → Jaeger |