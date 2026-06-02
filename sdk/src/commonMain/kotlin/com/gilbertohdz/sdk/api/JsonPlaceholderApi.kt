package com.gilbertohdz.sdk.api

import com.gilbertohdz.sdk.model.CreatePostRequest
import com.gilbertohdz.sdk.model.Post
import com.gilbertohdz.sdk.model.User
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Headers
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Path

interface JsonPlaceholderApi {

    @GET("users")
    suspend fun getUsers(): List<User>

    @Headers("Content-Type: application/json")
    @POST("posts")
    suspend fun createPost(@Body body: CreatePostRequest): Post

    @GET("posts/{id}")
    suspend fun getPost(@Path("id") id: Int): Post
}