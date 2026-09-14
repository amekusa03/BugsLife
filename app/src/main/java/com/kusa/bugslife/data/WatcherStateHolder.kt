package com.kusa.bugslife.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object WatcherStateHolder {
    private val _logs = MutableStateFlow<List<CommunicationLog>>(emptyList())
    val logs: StateFlow<List<CommunicationLog>> = _logs.asStateFlow()

    private val _lastSentTimestamp = MutableStateFlow(0L)
    val lastSentTimestamp: StateFlow<Long> = _lastSentTimestamp.asStateFlow()

    private val _peers = MutableStateFlow<List<PeerInfo>>(emptyList())
    val peers: StateFlow<List<PeerInfo>> = _peers.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _nextSyncTimestamp = MutableStateFlow(0L)
    val nextSyncTimestamp: StateFlow<Long> = _nextSyncTimestamp.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(0L)
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    private val _uiEvents = MutableSharedFlow<String>()
    val uiEvents: SharedFlow<String> = _uiEvents.asSharedFlow()

    fun setSyncing(syncing: Boolean) {
        _isSyncing.value = syncing
    }

    fun setNextSyncTimestamp(ts: Long) {
        _nextSyncTimestamp.value = ts
    }

    fun setLastSyncTimestamp(ts: Long) {
        _lastSyncTimestamp.value = ts
    }

    fun updatePeers(peers: List<PeerInfo>) {
        _peers.value = peers
    }

    fun addLog(log: CommunicationLog) {
        val current = _logs.value.toMutableList()
        current.add(0, log)
        if (current.size > 100) {
            current.removeAt(current.lastIndex)
        }
        _logs.value = current
    }

    fun setLastSentTimestamp(ts: Long) {
        _lastSentTimestamp.value = ts
    }

    suspend fun emitUiEvent(event: String) {
        _uiEvents.emit(event)
    }

    fun emitUiEventSync(event: String) {
        _uiEvents.tryEmit(event)
    }
}
