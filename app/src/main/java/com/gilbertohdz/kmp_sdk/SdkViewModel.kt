package com.gilbertohdz.kmp_sdk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gilbertohdz.sdk.SdkClient
import com.gilbertohdz.sdk.SdkConfig
import com.gilbertohdz.sdk.model.Post
import com.gilbertohdz.sdk.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val NON_EXISTENT_POST_ID = 99999

// Android emulator reaches the host machine at 10.0.2.2.
// Requires the OTel stack running: cd docker && docker compose up -d
// For a physical device on the same network, replace with your machine's local IP.
private const val OTLP_ENDPOINT = "http://10.0.2.2:4318/v1/traces"

sealed class UiState<out T> {
    data object Idle : UiState<Nothing>()
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

class SdkViewModel : ViewModel() {

    private val sdkClient = SdkClient(
        SdkConfig(
            serviceName        = "kmpsdk-android",
            serviceVersion     = "1.0.0",
            useConsoleExporter = true,          // traces también en Logcat (tag: OTel)
            otlpEndpoint       = OTLP_ENDPOINT  // → OTel Collector → Jaeger :16686
        )
    )

    private val _usersState = MutableStateFlow<UiState<List<User>>>(UiState.Idle)
    val usersState = _usersState.asStateFlow()

    private val _postState = MutableStateFlow<UiState<Post>>(UiState.Idle)
    val postState = _postState.asStateFlow()

    private val _errorState = MutableStateFlow<UiState<Post>>(UiState.Idle)
    val errorState = _errorState.asStateFlow()

    fun getUsers() {
        viewModelScope.launch {
            _usersState.value = UiState.Loading
            try {
                val users = sdkClient.getUsers()
                _usersState.value = UiState.Success(users)
            } catch (e: Exception) {
                _usersState.value = UiState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    fun createPost() {
        viewModelScope.launch {
            _postState.value = UiState.Loading
            try {
                val post = sdkClient.createPost(
                    userId = 1,
                    title = "Post desde KMP SDK",
                    body = "Creado con OpenTelemetry + Ktorfit"
                )
                _postState.value = UiState.Success(post)
            } catch (e: Exception) {
                _postState.value = UiState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    fun getPostWithError() {
        viewModelScope.launch {
            _errorState.value = UiState.Loading
            try {
                val post = sdkClient.getPostById(NON_EXISTENT_POST_ID)
                _errorState.value = UiState.Success(post)
            } catch (e: Exception) {
                _errorState.value = UiState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sdkClient.close()
    }
}