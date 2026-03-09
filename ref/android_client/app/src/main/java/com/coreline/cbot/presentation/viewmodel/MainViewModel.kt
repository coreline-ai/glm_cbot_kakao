package com.coreline.cbot.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coreline.cbot.domain.usecase.CheckHealthUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * MainActivity의 UI 상태를 관리하는 ViewModel
 */
class MainViewModel(
    private val checkHealthUseCase: CheckHealthUseCase
) : ViewModel() {

    private val _isServerConnected = MutableLiveData<Boolean>(false)
    val isServerConnected: LiveData<Boolean> = _isServerConnected

    private val _errorState = MutableLiveData<String?>(null)
    val errorState: LiveData<String?> = _errorState

    private val _logs = MutableLiveData<List<String>>(emptyList())
    val logs: LiveData<List<String>> = _logs

    private var isChecking = true

    fun startHealthCheck() {
        viewModelScope.launch {
            while (isChecking) {
                checkHealthUseCase().fold(
                    onSuccess = { _isServerConnected.postValue(it) },
                    onFailure = { 
                        _isServerConnected.postValue(false)
                        _errorState.postValue(it.message)
                    }
                )
                delay(5000)
            }
        }
    }

    fun addLog(message: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val logLine = "[$time] $message"
        
        // UI 스레드에서 즉시 반영되도록 처리 (Race Condition 방지)
        val currentList = _logs.value.orEmpty().toMutableList()
        currentList.add(0, logLine)
        
        // 50개까지만 유지
        if (currentList.size > 50) {
            currentList.removeAt(currentList.size - 1)
        }
        
        _logs.value = currentList
    }

    override fun onCleared() {
        super.onCleared()
        isChecking = false
    }
}
