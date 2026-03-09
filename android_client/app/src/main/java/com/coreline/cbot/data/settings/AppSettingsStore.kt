package com.coreline.cbot.data.settings

import android.app.Application
import android.content.Context
import com.coreline.cbot.domain.model.AppSettings
import com.coreline.cbot.domain.model.ProviderType
import com.coreline.cbot.domain.model.ResponseTargetMode
import com.coreline.cbot.domain.port.MutableSettingsProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppSettingsStore(application: Application) : MutableSettingsProvider {
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())

    val state: StateFlow<AppSettings> = _state.asStateFlow()

    override fun currentSettings(): AppSettings = _state.value

    override fun switchProvider(providerType: ProviderType) {
        val current = currentSettings()
        update(
            providerType = providerType,
            responseTargetMode = current.responseTargetMode,
            openAiApiKey = current.openAiApiKey,
            openAiModel = current.openAiModel
        )
    }

    fun update(
        providerType: ProviderType,
        responseTargetMode: ResponseTargetMode,
        openAiApiKey: String,
        openAiModel: String
    ) {
        prefs.edit()
            .putString(KEY_PROVIDER, providerType.name)
            .putString(KEY_RESPONSE_TARGET_MODE, responseTargetMode.name)
            .putString(KEY_OPENAI_API_KEY, openAiApiKey.trim())
            .putString(KEY_OPENAI_MODEL, openAiModel.trim())
            .apply()

        _state.value = load()
    }

    private fun load(): AppSettings {
        return AppSettings(
            providerType = prefs.getString(KEY_PROVIDER, ProviderType.GLM.name)
                ?.let { runCatching { ProviderType.valueOf(it) }.getOrNull() }
                ?: ProviderType.GLM,
            responseTargetMode = prefs.getString(KEY_RESPONSE_TARGET_MODE, ResponseTargetMode.SELECTED_ONLY.name)
                ?.let { runCatching { ResponseTargetMode.valueOf(it) }.getOrNull() }
                ?: ResponseTargetMode.SELECTED_ONLY,
            openAiApiKey = prefs.getString(KEY_OPENAI_API_KEY, "").orEmpty(),
            openAiModel = prefs.getString(KEY_OPENAI_MODEL, "").orEmpty()
        )
    }

    companion object {
        private const val PREFS_NAME = "cbot_settings"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_RESPONSE_TARGET_MODE = "response_target_mode"
        private const val KEY_OPENAI_API_KEY = "openai_api_key"
        private const val KEY_OPENAI_MODEL = "openai_model"
    }
}
