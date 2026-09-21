package com.kusa.bugslife.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kusa.bugslife.data.AppPreferences
import com.kusa.bugslife.data.MemberStatus
import com.kusa.bugslife.data.PacketType
import com.kusa.bugslife.data.PeerInfo
import com.kusa.bugslife.data.WatcherStateHolder
import com.kusa.bugslife.network.FirestorePeerMessenger
import com.kusa.bugslife.service.WatcherForegroundService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    prefs: AppPreferences,
    onRequireServiceReload: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // ダイアログ状態
    var showSettingsDialog by remember { mutableStateOf(false) }
    var editingPeer by remember { mutableStateOf<PeerInfo?>(null) }
    var showLogsSheet by remember { mutableStateOf(false) }

    // 監視State
    val logs by WatcherStateHolder.logs.collectAsState()
    val lastSentTs by WatcherStateHolder.lastSentTimestamp.collectAsState()
    val lastLocalActivityTs by WatcherStateHolder.lastLocalScreenOnTime.collectAsState()
    val todayCount by WatcherStateHolder.todayScreenOnCount.collectAsState()
    val peers by WatcherStateHolder.peers.collectAsState()
    val isSyncing by WatcherStateHolder.isSyncing.collectAsState()
    val nextSyncTs by WatcherStateHolder.nextSyncTimestamp.collectAsState()
    val lastSyncTs by WatcherStateHolder.lastSyncTimestamp.collectAsState()
    val pendingRequests by WatcherStateHolder.pendingRequests.collectAsState()
    val myMemberStatus by WatcherStateHolder.myMemberStatus.collectAsState()

    // リアルタイムUIイベントの監視
    LaunchedEffect(Unit) {
        WatcherStateHolder.uiEvents.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // 参加申請受領時の承認確認ダイアログ (端末A向け)
    val firstRequest = pendingRequests.firstOrNull()
    if (firstRequest != null) {
        AlertDialog(
            onDismissRequest = { /* 外側タップでは閉じない */ },
            title = {
                Text("新しいメンバーの参加申請", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("${firstRequest.userName}さんが参加しました。\n承認しますか？")
            },
            confirmButton = {
                Button(
                    onClick = {
                        WatcherForegroundService.approveMember(context, firstRequest.userId)
                    }
                ) {
                    Text("はい（承認する）")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        WatcherForegroundService.rejectMember(context, firstRequest.userId)
                    }
                ) {
                    Text("いいえ（拒否）")
                }
            }
        )
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
                todayScreenOnCount = if (todayCount > 0) todayCount else prefs.todayScreenOnCount,
                lastLocalScreenOnTime = if (lastLocalActivityTs > 0L) lastLocalActivityTs else prefs.lastLocalScreenOnTime,
                lastSentTimestamp = lastSentTs,
                lastSyncTimestamp = lastSyncTs,
                nextSyncTimestamp = nextSyncTs,
                isSyncing = isSyncing,
                peers = peers,
                timeoutDurationMs = prefs.timeoutDurationMillis,
                isSyncEnabled = prefs.isSyncEnabled,
                groupName = prefs.groupName,
                myMemberStatus = myMemberStatus,
                onSendStatus = { type ->
                    WatcherForegroundService.sendStatus(context, type)
                },
                onManualSync = {
                    WatcherForegroundService.triggerManualSync(context)
                },
                onEditPeer = { peer ->
                    editingPeer = peer
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

    // ピア編集ダイアログ
    editingPeer?.let { peerToEdit ->
        PeerEditDialog(
            peer = peerToEdit,
            onDismiss = { editingPeer = null },
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
                editingPeer = null
            },
            onDelete = { targetPeer ->
                val currentPeers = prefs.getPeers().toMutableList()
                currentPeers.removeAll { it.id == targetPeer.id }
                prefs.savePeers(currentPeers)
                WatcherStateHolder.updatePeers(currentPeers)
                editingPeer = null
            }
        )
    }

    // 設定ダイアログ (Firebase グループ参加・承認対応)
    if (showSettingsDialog) {
        SettingsDialog(
            currentName = prefs.userName,
            currentTimeoutMs = prefs.timeoutDurationMillis,
            currentSyncEnabled = prefs.isSyncEnabled,
            currentGroupName = prefs.groupName,
            myUserId = prefs.userId,
            onDismiss = { showSettingsDialog = false },
            onSave = { name, timeoutMs, isSyncEnabled, groupName, isJoinRequest ->
                prefs.userName = name
                prefs.timeoutDurationMillis = timeoutMs
                prefs.isSyncEnabled = isSyncEnabled
                prefs.groupName = groupName

                coroutineScope.launch {
                    val messenger = FirestorePeerMessenger(context, groupName, prefs.userId)
                    if (isJoinRequest) {
                        prefs.myMemberStatus = MemberStatus.PENDING
                        WatcherStateHolder.setMyMemberStatus(MemberStatus.PENDING)
                        messenger.requestJoinGroup(groupName, prefs.userId, name)
                        WatcherStateHolder.emitUiEvent("グループ「$groupName」に参加申請を送信しました")
                    } else {
                        prefs.myMemberStatus = MemberStatus.APPROVED
                        WatcherStateHolder.setMyMemberStatus(MemberStatus.APPROVED)
                        messenger.createGroup(groupName, prefs.userId, name)
                    }
                    onRequireServiceReload()
                }

                showSettingsDialog = false
            }
        )
    }

    // ログ履歴シート
    if (showLogsSheet) {
        LogsBottomSheet(
            logs = logs,
            onClearLogs = {
                prefs.clearCommunicationLogs()
                WatcherStateHolder.clearLogs()
            },
            onDismiss = { showLogsSheet = false }
        )
    }
}
