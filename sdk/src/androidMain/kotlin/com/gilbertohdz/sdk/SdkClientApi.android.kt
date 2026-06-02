package com.gilbertohdz.sdk

import com.gilbertohdz.sdk.api.JsonPlaceholderApi
import com.gilbertohdz.sdk.api.createJsonPlaceholderApi
import de.jensklingenberg.ktorfit.Ktorfit

internal actual fun Ktorfit.createApi(): JsonPlaceholderApi = createJsonPlaceholderApi()