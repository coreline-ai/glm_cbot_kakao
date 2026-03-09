package com.coreline.cbot.presentation.view

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.coreline.cbot.CBotApplication
import com.coreline.cbot.R
import com.coreline.cbot.domain.model.EngineStatus
import com.coreline.cbot.domain.model.ProviderType
import com.coreline.cbot.domain.model.ResponseTargetMode
import com.coreline.cbot.presentation.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var statusIndicator: TextView
    private lateinit var statusDot: ImageView
    private lateinit var metricsText: TextView
    private lateinit var providerSpinner: Spinner
    private lateinit var targetModeSpinner: Spinner
    private lateinit var openAiKeyInput: EditText
    private lateinit var openAiModelInput: EditText
    private lateinit var controlHintText: TextView
    private lateinit var applyButton: Button
    private lateinit var selfTestButton: Button
    private lateinit var openAiFields: View
    private lateinit var viewModel: MainViewModel
    private var permissionDialog: AlertDialog? = null
    private val providerOptions = listOf("GLM", "OpenAI", "CodexProxy", "StockProxy")
    private val targetModeOptions = listOf(
        "선택 1 · 최경환/JU신이라불러라만 응답",
        "선택 2 · 모두 응답"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initViewModel()
        observeViewModel()
        refreshPermissionState()
        handleAutomationIntent(intent)
    }

    private fun initViews() {
        logText = findViewById(R.id.log_text)
        statusText = findViewById(R.id.status_text)
        statusIndicator = findViewById(R.id.status_indicator)
        statusDot = findViewById(R.id.status_dot)
        metricsText = findViewById(R.id.metrics_text)
        providerSpinner = findViewById(R.id.provider_spinner)
        targetModeSpinner = findViewById(R.id.target_mode_spinner)
        openAiKeyInput = findViewById(R.id.openai_key_input)
        openAiModelInput = findViewById(R.id.openai_model_input)
        controlHintText = findViewById(R.id.control_hint_text)
        applyButton = findViewById(R.id.apply_button)
        selfTestButton = findViewById(R.id.self_test_button)
        openAiFields = findViewById(R.id.openai_fields)

        providerSpinner.adapter = buildSpinnerAdapter(providerOptions)
        targetModeSpinner.adapter = buildSpinnerAdapter(targetModeOptions)
    }

    private fun initViewModel() {
        val container = (application as CBotApplication).container
        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(
                    monitoringStore = container.monitoringStore,
                    appSettingsStore = container.appSettingsStore,
                    generateReplyUseCase = container.generateReplyUseCase,
                    getProviderHealthUseCase = container.getProviderHealthUseCase
                ) as T
            }
        })[MainViewModel::class.java]
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.monitoringState.collectLatest { state ->
                statusIndicator.text = when (state.engineStatus) {
                    EngineStatus.READY -> "LLM 연결됨"
                    EngineStatus.BUSY -> "LLM 응답 중"
                    EngineStatus.SECURITY_LOCK -> "보안 잠금"
                    EngineStatus.PERMISSION_REQUIRED -> "권한 필요"
                    EngineStatus.CONFIG_REQUIRED -> "설정/릴리스 필요"
                    EngineStatus.DEGRADED -> "LLM 지연"
                    EngineStatus.INITIALIZING -> "LLM 초기화 중"
                }

                statusDot.setImageResource(
                    when (state.engineStatus) {
                        EngineStatus.READY -> R.drawable.ic_dot_green
                        EngineStatus.BUSY, EngineStatus.DEGRADED -> R.drawable.ic_dot_yellow
                        EngineStatus.INITIALIZING -> R.drawable.ic_dot_yellow
                        EngineStatus.PERMISSION_REQUIRED,
                        EngineStatus.CONFIG_REQUIRED,
                        EngineStatus.SECURITY_LOCK -> R.drawable.ic_dot_red
                    }
                )

                val latencyText = state.lastLatencyMs?.let { "${it}ms" } ?: "--"
                val lastSuccessText = state.lastSuccessAt ?: "--"
                val lastFailureText = state.lastFailureAt ?: "--"
                metricsText.text = "provider: ${state.providerName} | model: ${state.model} | mode: ${state.responseModeLabel} | latency: $latencyText | ok: $lastSuccessText | fail: $lastFailureText"

                statusText.text = when (state.engineStatus) {
                    EngineStatus.PERMISSION_REQUIRED -> "알림 권한 필요 (터치)"
                    EngineStatus.CONFIG_REQUIRED -> state.lastError ?: "릴리스 키 필요"
                    EngineStatus.SECURITY_LOCK -> state.lastError ?: "보안 잠금"
                    EngineStatus.DEGRADED -> state.lastError ?: "응답 지연"
                    EngineStatus.BUSY -> "메시지 처리 중"
                    EngineStatus.READY -> "자동 응답 대기 중"
                    EngineStatus.INITIALIZING -> "앱 준비 중"
                }

                val prefix = "root@coreline:~# "
                val metaLine = buildString {
                    append("req=${state.lastRequestId ?: "-"}")
                    append(" | finish=${state.lastFinishReason ?: "-"}")
                    append(" | tokens=${state.lastTokenUsage ?: "-"}")
                }
                val logLines = buildList {
                    add("[meta] $metaLine")
                    addAll(state.logs.map { "[${it.timestamp}] ${it.message}" })
                }
                logText.text = prefix + logLines.joinToString("\n$prefix")
            }
        }

        lifecycleScope.launch {
            viewModel.settingsState.collectLatest { settings ->
                val providerIndex = when (settings.providerType) {
                    ProviderType.GLM -> 0
                    ProviderType.OPENAI -> 1
                    ProviderType.CODEX_PROXY -> 2
                    ProviderType.STOCK_PROXY -> 3
                }
                if (providerSpinner.selectedItemPosition != providerIndex) {
                    providerSpinner.setSelection(providerIndex, false)
                }

                val targetModeIndex = when (settings.responseTargetMode) {
                    ResponseTargetMode.SELECTED_ONLY -> 0
                    ResponseTargetMode.ALL -> 1
                }
                if (targetModeSpinner.selectedItemPosition != targetModeIndex) {
                    targetModeSpinner.setSelection(targetModeIndex, false)
                }

                if (openAiKeyInput.text.toString() != settings.openAiApiKey) {
                    openAiKeyInput.setText(settings.openAiApiKey)
                }
                if (openAiModelInput.text.toString() != settings.openAiModel) {
                    openAiModelInput.setText(settings.openAiModel)
                }

                openAiFields.visibility = if (settings.providerType == ProviderType.OPENAI) View.VISIBLE else View.GONE
                controlHintText.text = providerHint(settings.providerType)
            }
        }

        applyButton.setOnClickListener {
            val providerType = when (providerSpinner.selectedItemPosition) {
                1 -> ProviderType.OPENAI
                2 -> ProviderType.CODEX_PROXY
                3 -> ProviderType.STOCK_PROXY
                else -> ProviderType.GLM
            }
            val responseTargetMode = if (targetModeSpinner.selectedItemPosition == 1) {
                ResponseTargetMode.ALL
            } else {
                ResponseTargetMode.SELECTED_ONLY
            }

            viewModel.applySettings(
                providerType = providerType,
                responseTargetMode = responseTargetMode,
                openAiApiKey = openAiKeyInput.text.toString(),
                openAiModel = openAiModelInput.text.toString()
            )
            Toast.makeText(this, R.string.settings_applied, Toast.LENGTH_SHORT).show()
        }

        selfTestButton.setOnClickListener {
            val prompt = when (viewModel.settingsState.value.providerType) {
                ProviderType.STOCK_PROXY -> "005930 최근 흐름 요약해줘"
                else -> "하이"
            }
            viewModel.runSelfTest(prompt)
            Toast.makeText(this, "Self-test requested", Toast.LENGTH_SHORT).show()
        }

        providerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val isOpenAi = position == 1
                openAiFields.visibility = if (isOpenAi) View.VISIBLE else View.GONE
                controlHintText.text = when (position) {
                    1 -> providerHint(ProviderType.OPENAI)
                    2 -> providerHint(ProviderType.CODEX_PROXY)
                    3 -> providerHint(ProviderType.STOCK_PROXY)
                    else -> providerHint(ProviderType.GLM)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAutomationIntent(intent)
    }

    private fun refreshPermissionState() {
        val permissionGranted = isNotificationPermissionGranted()
        viewModel.refreshStatus(permissionGranted)

        if (!permissionGranted) {
            statusText.setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            showPermissionDialog()
        } else {
            statusText.setOnClickListener(null)
            permissionDialog?.dismiss()
            permissionDialog = null
        }
    }

    private fun showPermissionDialog() {
        if (permissionDialog?.isShowing == true) {
            return
        }

        permissionDialog = AlertDialog.Builder(this)
            .setTitle(R.string.notification_permission_title)
            .setMessage(R.string.notification_permission_message)
            .setPositiveButton(R.string.notification_permission_action) { _, _ ->
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            .setNegativeButton(R.string.notification_permission_later) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun isNotificationPermissionGranted(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(packageName) == true
    }

    private fun buildSpinnerAdapter(items: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(this, R.layout.spinner_item_control, items).apply {
            setDropDownViewResource(R.layout.spinner_item_dropdown)
        }
    }

    private fun providerHint(providerType: ProviderType): String {
        return when (providerType) {
            ProviderType.GLM -> "GLM 선택 시 내장 모델과 키를 즉시 반영합니다."
            ProviderType.OPENAI -> "OpenAI 선택 시 모델명과 키 override를 즉시 반영합니다."
            ProviderType.CODEX_PROXY -> "CodexProxy 선택 시 adb reverse와 로컬 codex_proxy가 필요합니다."
            ProviderType.STOCK_PROXY -> "StockProxy 선택 시 adb reverse tcp:4327 tcp:4327 와 로컬 stock_proxy가 필요합니다. 질문에 6자리 종목코드(예: 005930)를 포함하세요."
        }
    }

    private fun handleAutomationIntent(intent: Intent?) {
        val extras = intent?.extras ?: return
        val provider = extras.getString("provider")
        val runSelfTest = extras.getBoolean("run_self_test", false)
        val prompt = extras.getString("prompt").orEmpty()

        if (provider != null) {
            val current = viewModel.settingsState.value
            val providerType = when (provider.uppercase()) {
                "GLM" -> ProviderType.GLM
                "OPENAI" -> ProviderType.OPENAI
                "CODEX_PROXY", "CODEXPROXY", "CODEX" -> ProviderType.CODEX_PROXY
                "STOCK_PROXY", "STOCKPROXY", "STOCK" -> ProviderType.STOCK_PROXY
                else -> null
            }
            if (providerType != null) {
                viewModel.applySettings(
                    providerType = providerType,
                    responseTargetMode = current.responseTargetMode,
                    openAiApiKey = current.openAiApiKey,
                    openAiModel = current.openAiModel
                )
            }
        }

        if (runSelfTest) {
            window.decorView.post {
                val fallbackPrompt = when (viewModel.settingsState.value.providerType) {
                    ProviderType.STOCK_PROXY -> "005930 최근 흐름 요약해줘"
                    else -> "하이"
                }
                viewModel.runSelfTest(prompt.ifBlank { fallbackPrompt })
            }
        }
    }
}
