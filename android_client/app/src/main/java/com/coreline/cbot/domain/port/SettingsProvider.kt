package com.coreline.cbot.domain.port

import com.coreline.cbot.domain.model.ProviderType
import com.coreline.cbot.domain.model.AppSettings

interface SettingsProvider {
    fun currentSettings(): AppSettings
}

interface MutableSettingsProvider : SettingsProvider {
    fun switchProvider(providerType: ProviderType)
}
