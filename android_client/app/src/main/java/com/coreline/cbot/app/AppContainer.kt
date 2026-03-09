package com.coreline.cbot.app

import android.app.Application
import com.coreline.cbot.BuildConfig
import com.coreline.cbot.core.PromptBuilder
import com.coreline.cbot.core.RecentMessageGuard
import com.coreline.cbot.core.ResponseTargetFilter
import com.coreline.cbot.core.ReplySanitizer
import com.coreline.cbot.core.SecurityGuard
import com.coreline.cbot.core.WakeWordParser
import com.coreline.cbot.data.codexproxy.CodexProxyApiService
import com.coreline.cbot.data.codexproxy.CodexProxyLlmGateway
import com.coreline.cbot.data.llm.RoutingLlmGateway
import com.coreline.cbot.data.memory.InMemoryConversationMemoryStore
import com.coreline.cbot.data.monitoring.InMemoryMonitoringStore
import com.coreline.cbot.data.openai.OpenAiApiService
import com.coreline.cbot.data.openai.OpenAiLlmGateway
import com.coreline.cbot.data.reply.KakaoReplySender
import com.coreline.cbot.data.security.EmbeddedSecretProvider
import com.coreline.cbot.data.security.NativeSecrets
import com.coreline.cbot.data.settings.AppSettingsStore
import com.coreline.cbot.data.stockproxy.StockProxyApiService
import com.coreline.cbot.data.stockproxy.StockProxyLlmGateway
import com.coreline.cbot.data.zai.ZaiApiService
import com.coreline.cbot.data.zai.ZaiLlmGateway
import com.coreline.cbot.domain.usecase.BuildPromptUseCase
import com.coreline.cbot.domain.usecase.GenerateReplyUseCase
import com.coreline.cbot.domain.usecase.GetProviderHealthUseCase
import com.coreline.cbot.domain.usecase.HandleIncomingNotificationUseCase
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class AppContainer(application: Application) {
    private val settingsStore = AppSettingsStore(application)
    private val currentResponseModeLabel = when (settingsStore.currentSettings().responseTargetMode) {
        com.coreline.cbot.domain.model.ResponseTargetMode.SELECTED_ONLY -> "선택 1"
        com.coreline.cbot.domain.model.ResponseTargetMode.ALL -> "선택 2"
    }
    val monitoringStore = InMemoryMonitoringStore(
        providerName = settingsStore.currentSettings().currentProviderName(),
        model = settingsStore.currentSettings().currentModel(),
        responseModeLabel = currentResponseModeLabel
    )

    private val promptBuilder = PromptBuilder()
    private val replySanitizer = ReplySanitizer()
    private val wakeWordParser = WakeWordParser()
    private val responseTargetFilter = ResponseTargetFilter(settingsStore)
    private val recentMessageGuard = RecentMessageGuard()
    private val conversationMemoryStore = InMemoryConversationMemoryStore()
    private val securityGuard = SecurityGuard(application)
    private val nativeSecrets = NativeSecrets()
    private val embeddedSecretProvider = EmbeddedSecretProvider(application, nativeSecrets)
    private val replySender = KakaoReplySender()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.GLM_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val openAiRetrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.OPENAI_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val codexProxyRetrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.CODEX_PROXY_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val stockProxyRetrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.STOCK_PROXY_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val zaiApiService = retrofit.create(ZaiApiService::class.java)
    private val openAiApiService = openAiRetrofit.create(OpenAiApiService::class.java)
    private val codexProxyApiService = codexProxyRetrofit.create(CodexProxyApiService::class.java)
    private val stockProxyApiService = stockProxyRetrofit.create(StockProxyApiService::class.java)
    private val glmGateway = ZaiLlmGateway(zaiApiService, embeddedSecretProvider)
    private val openAiGateway = OpenAiLlmGateway(openAiApiService, settingsStore, embeddedSecretProvider::getOpenAiApiKey)
    private val codexProxyGateway = CodexProxyLlmGateway(codexProxyApiService)
    private val stockProxyGateway = StockProxyLlmGateway(stockProxyApiService)
    private val llmGateway = RoutingLlmGateway(settingsStore, glmGateway, openAiGateway, codexProxyGateway, stockProxyGateway)

    private val buildPromptUseCase = BuildPromptUseCase(promptBuilder, settingsStore)

    val generateReplyUseCase = GenerateReplyUseCase(
        buildPromptUseCase = buildPromptUseCase,
        llmGateway = llmGateway,
        replySanitizer = replySanitizer
    )

    val handleIncomingNotificationUseCase = HandleIncomingNotificationUseCase(
        wakeWordParser = wakeWordParser,
        responseTargetFilter = responseTargetFilter,
        securityGuard = securityGuard,
        recentMessageGuard = recentMessageGuard,
        conversationMemoryStore = conversationMemoryStore,
        generateReplyUseCase = generateReplyUseCase,
        replySender = replySender,
        monitoringStore = monitoringStore,
        settingsProvider = settingsStore
    )

    val getProviderHealthUseCase = GetProviderHealthUseCase(
        settingsProvider = settingsStore,
        embeddedSecretProvider = embeddedSecretProvider,
        securityGuard = securityGuard,
        monitoringStore = monitoringStore
    )

    val appSettingsStore = settingsStore
}
