package com.gilbertohdz.sdk.network

import io.ktor.client.HttpClientConfig

// Platform-specific Ktor client configuration applied after the common setup.
expect fun HttpClientConfig<*>.applyPlatformConfig()