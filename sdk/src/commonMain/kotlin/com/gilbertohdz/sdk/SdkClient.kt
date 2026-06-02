package com.gilbertohdz.sdk

import com.gilbertohdz.sdk.api.JsonPlaceholderApi
import com.gilbertohdz.sdk.model.CreatePostRequest
import com.gilbertohdz.sdk.model.Post
import com.gilbertohdz.sdk.model.User
import com.gilbertohdz.sdk.network.createHttpClient
import com.gilbertohdz.sdk.telemetry.SdkTracer
import com.gilbertohdz.sdk.telemetry.initializeTelemetry
import de.jensklingenberg.ktorfit.Ktorfit

data class SdkConfig(
    val serviceName: String = "kmpsdk",
    val serviceVersion: String = "1.0.0",
    val baseUrl: String = "https://jsonplaceholder.typicode.com/",
    val useConsoleExporter: Boolean = true,
    val otlpEndpoint: String? = null
)

// expect/actual: each platform provides the KSP-generated createJsonPlaceholderApi() extension
internal expect fun Ktorfit.createApi(): JsonPlaceholderApi

class SdkClient(config: SdkConfig = SdkConfig()) {

    private val tracer: SdkTracer = initializeTelemetry(
        serviceName = config.serviceName,
        serviceVersion = config.serviceVersion,
        useConsoleExporter = config.useConsoleExporter,
        otlpEndpoint = config.otlpEndpoint
    )

    private val api: JsonPlaceholderApi = Ktorfit.Builder()
        .baseUrl(config.baseUrl)
        .httpClient(createHttpClient(tracer))
        .build()
        .createApi()

    @Throws(Exception::class)
    suspend fun getUsers(): List<User> {
        val span = tracer.startSpan("sdk.getUsers")
        return try {
            val result = api.getUsers()
            span.setAttribute("result.count", result.size.toLong())
            span.setStatus(isOk = true)
            result
        } catch (e: Exception) {
            span.setStatus(isOk = false, message = e.message ?: "error")
            throw e
        } finally {
            span.end()
        }
    }

    @Throws(Exception::class)
    suspend fun createPost(userId: Int, title: String, body: String): Post {
        val span = tracer.startSpan("sdk.createPost")
        span.setAttribute("post.userId", userId.toString())
        span.setAttribute("post.title", title)
        return try {
            val result = api.createPost(CreatePostRequest(userId, title, body))
            span.setAttribute("post.id.returned", result.id.toLong())
            span.setStatus(isOk = true)
            result
        } catch (e: Exception) {
            span.setStatus(isOk = false, message = e.message ?: "error")
            throw e
        } finally {
            span.end()
        }
    }

    @Throws(Exception::class)
    suspend fun getPostById(id: Int): Post {
        val span = tracer.startSpan("sdk.getPostById")
        span.setAttribute("post.id.requested", id.toLong())
        return try {
            val result = api.getPost(id)
            span.setStatus(isOk = true)
            result
        } catch (e: Exception) {
            span.setAttribute("error.type", e::class.simpleName ?: "Exception")
            span.setAttribute("error.message", e.message ?: "unknown")
            span.setStatus(isOk = false, message = e.message ?: "error")
            throw e
        } finally {
            span.end()
        }
    }

    fun close() = tracer.close()
}