package com.gilbertohdz.sdk.model

import kotlinx.serialization.Serializable

@Serializable
data class Post(
    val id: Int = 0,
    val userId: Int,
    val title: String,
    val body: String
)

@Serializable
data class CreatePostRequest(
    val userId: Int,
    val title: String,
    val body: String
)
