package com.kusa.bugslife.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kusa.bugslife.data.AppPreferences
import com.kusa.bugslife.data.PacketType
import com.kusa.bugslife.data.PeerInfo
import com.kusa.bugslife.data.WatcherStateHolder
import com.kusa.bugslife.service.WatcherForegroundService
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    prefs: AppPreferences,
    onRequireServiceReload: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // ダイアログ状態
    var showSettingsDialog by remember { mutableStateOf(false) }
    var editingPeer by remember { mutableStateOf<PeerInfo?>(null) }
    var isAddingPeer by remember { mutableStateOf(false) }
    var showLogsSheet by remember { mutableStateOf(false) }

    // 監視State
    val logs by WatcherStateHolder.logs.collectAsState()
    val lastSentTs by WatcherStateHolder.lastSentTimestamp.collectAsState()
    val peers by WatcherStateHolder.peers.collectAsState()

    // リアルタイムUIイベントの監視
    LaunchedEffect(Unit) {
        WatcherStateHolder.uiEvents.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "遠隔見守り",
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "(${prefs.userName})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showLogsSheet = true }) {
                        BadgedBox(
                            badge = {
                                if (logs.isNotEmpty()) {
                                    Badge { Text("${logs.size}") }
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "通信ログ")
                        }
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "設定")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            MutualWatchScreen(
                todayScreenOnCount = prefs.todayScreenOnCount,
                lastSentTimestamp = lastSentTs,
                peers = peers,
                timeoutDurationMs = prefs.timeoutDurationMillis,
                onSendStatus = { type ->
                    WatcherForegroundService.sendStatus(context, type)
                },
                onAddPeer = {
                    editingPeer = null
                    isAddingPeer = true
                },
                onEditPeer = { peer ->
                    editingPeer = peer
                    isAddingPeer = true
                },
                onPingPeer = { peer ->
                    WatcherForegroundService.sendStatus(
                        context,
                        PacketType.PING,
                        "疎通確認テスト"
                    )
                },
                onTestScreenOn = {
                    WatcherForegroundService.sendStatus(
                        context,
                        PacketType.HEARTBEAT,
                        "テスト手動点灯シグナル"
                    )
                }
            )
        }
    }

    // ピア追加・編集ダイアログ
    if (isAddingPeer) {
        PeerEditDialog(
            initialPeer = editingPeer,
            onDismiss = { isAddingPeer = false },
            onSave = { updatedPeer ->
                val currentPeers = prefs.getPeers().toMutableList()
                val index = currentPeers.indexOfFirst { it.id == updatedPeer.id }
                if (index >= 0) {
                    currentPeers[index] = updatedPeer
                } else {
                    currentPeers.add(updatedPeer)
                }
                prefs.savePeers(currentPeers)
                WatcherStateHolder.updatePeers(currentPeers)
                onRequireServiceReload()
                isAddingPeer = false
            },
            onDelete = { targetPeer ->
                val currentPeers = prefs.getPeers().toMutableList()
                currentPeers.removeAll { it.id == targetPeer.id }
                prefs.savePeers(currentPeers)
                WatcherStateHolder.updatePeers(currentPeers)
                onRequireServiceReload()
                isAddingPeer = false
            }
        )
    }

    // 設定ダイアログ
    if (showSettingsDialog) {
        SettingsDialog(
            currentName = prefs.userName,
            currentPort = prefs.port,
            currentTimeoutMs = prefs.timeoutDurationMillis,
            onDismiss = { showSettingsDialog = false },
            onSave = { name, port, timeoutMs ->
                prefs.userName = name
                prefs.port = port
                prefs.timeoutDurationMillis = timeoutMs
                onRequireServiceReload()
                showSettingsDialog = false
            }
        )
    }

    // ログ履歴シート
    if (showLogsSheet) {
        LogsBottomSheet(
            logs = logs,
            onDismiss = { showLogsSheet = false }
        )
    }
}
