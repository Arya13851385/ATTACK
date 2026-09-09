package com.localnet.emergency.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localnet.emergency.model.AlertLevel
import com.localnet.emergency.model.AlertState
import com.localnet.emergency.model.ConnectionState
import com.localnet.emergency.repository.AlertRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlertViewModel @Inject constructor(
    private val repository: AlertRepository
) : ViewModel() {

    val alertState: StateFlow<AlertState> = repository.alertState
    val connectionState: StateFlow<ConnectionState> = repository.connectionState

    fun triggerAlert(level: AlertLevel, note: String?, room: String?) {
        repository.triggerAlert(level, note, room)
    }

    fun connectManually(host: String, port: Int) {
        repository.connectManually(host, port)
    }

    fun retryDiscovery() {
        viewModelScope.launch { repository.startConnecting() }
    }
}
