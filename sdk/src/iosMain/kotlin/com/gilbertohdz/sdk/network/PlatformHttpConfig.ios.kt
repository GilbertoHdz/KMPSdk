package com.gilbertohdz.sdk.network

import io.ktor.client.HttpClientConfig

// NSURLSession handles transparent decompression correctly — no extra config needed.
actual fun HttpClientConfig<*>.applyPlatformConfig() {}