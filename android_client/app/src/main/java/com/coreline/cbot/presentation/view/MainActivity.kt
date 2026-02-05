package com.coreline.cbot.presentation.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.coreline.cbot.R
import com.coreline.cbot.data.repository.ChatRepositoryImpl
import com.coreline.cbot.domain.usecase.CheckHealthUseCase
import com.coreline.cbot.network.RetrofitClient
import com.coreline.cbot.presentation.viewmodel.MainViewModel

/**
 * 메인 화면 Activity
 * UI 표시 및 사용자 권한 확인만 담당합니다.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var statusIndicator: TextView
    private lateinit var statusDot: ImageView
    private lateinit var viewModel: MainViewModel
    private var permissionDialog: androidx.appcompat.app.AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initViewModel()
        observeViewModel()

        checkNotificationPermission()
        viewModel.startHealthCheck()
    }

    private fun initViews() {
        logText = findViewById(R.id.log_text)
        statusText = findViewById(R.id.status_text)
        statusIndicator = findViewById(R.id.status_indicator)
        statusDot = findViewById(R.id.status_dot)
    }

    private fun initViewModel() {
        // DI 프레임워크가 없으므로 수동으로 주입 (최대한 심플하게 유지)
        val repository = ChatRepositoryImpl(RetrofitClient.apiService)
        val checkHealthUseCase = CheckHealthUseCase(repository)
        
        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(checkHealthUseCase) as T
            }
        })[MainViewModel::class.java]
    }

    private fun observeViewModel() {
        viewModel.isServerConnected.observe(this) { isConnected ->
            if (isConnected) {
                statusIndicator.text = "서버 연결됨"
                statusDot.setImageResource(R.drawable.ic_dot_green)
            } else {
                statusIndicator.text = "서버 연결 끊김"
                statusDot.setImageResource(R.drawable.ic_dot_red)
            }
        }

        viewModel.logs.observe(this) { logs ->
            val prefix = "root@coreline:~# "
            logText.text = prefix + logs.joinToString("\n$prefix")
        }
    }

    private fun checkNotificationPermission() {
        if (!isNotificationPermissionGranted()) {
            statusText.text = "⚠️ 알림 권한 필요 (터치)"
            statusText.setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            showPermissionDialog()
        } else {
            statusText.text = "✅ 서비스 동작 중 (준비됨)"
            statusText.setOnClickListener(null)
            permissionDialog?.dismiss()
            permissionDialog = null
        }
    }

    private fun showPermissionDialog() {
        if (permissionDialog?.isShowing == true) return

        permissionDialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("알림 접근 권한 필요")
            .setMessage("카카오톡 메시지를 읽고 분석하기 위해 '알림 접근 권한'이 필요합니다. 설정 화면으로 이동하시겠습니까?")
            .setPositiveButton("이동") { _, _ ->
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            .setNegativeButton("나중에") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    override fun onResume() {
        super.onResume()
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
            .registerReceiver(logReceiver, android.content.IntentFilter("com.coreline.cbot.LOG"))
        checkNotificationPermission()
    }

    override fun onPause() {
        super.onPause()
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(logReceiver)
    }

    private val logReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.getStringExtra("log")?.let { viewModel.addLog(it) }
        }
    }

    private fun isNotificationPermissionGranted(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(packageName) == true
    }
}
