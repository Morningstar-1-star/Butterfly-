package com.example.model

enum class AppScreen {
    HOME,
    PROVIDERS
}

data class ProviderUiItem(
    val id: String,
    val name: String,
    val description: String = "",
    val isEnabled: Boolean = true,
    val isDefault: Boolean = false
)
