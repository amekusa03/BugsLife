package com.kusa.bugslife.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kusa.bugslife.data.MemberStatus
import com.kusa.bugslife.data.PacketType
import com.kusa.bugslife.data.PeerInfo
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MutualWatchScreen(
    todayScreenOnCount: Int,
    lastLocalScreenOnTime: Long,
    lastSentTimestamp: Long,
    lastSyncTimestamp: Long,
    nextSyncTimestamp: Long,
    isSyncing: Boolean,
    peers: List<PeerInfo>,
    timeoutDurationMs: Long,
    isSyncEnabled: Boolean = true,
    groupName: String = "",
    myMemberStatus: MemberStatus = MemberStatus.APPROVED,
    onSendStatus: (PacketType) -> Unit,
    onManualSync: () -> Unit,
    onEditPeer: (PeerInfo) -> Unit,
    onPingPeer: (PeerInfo) -> Unit,
    onTestScreenOn: () -> Unit
) {
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            currentTime = System.currentTimeMillis()
        }
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.JAPAN) }
    val hmFormat = remember { SimpleDateFormat("HH:mm", Locale.JAPAN) }
    val fullTimeFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.JAPAN) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 承認待ち / 拒否バナー
        if (myMemberStatus == MemberStatus.PENDING) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color(0xFFE65100),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "グループ「$groupName」の参加承認待ち",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100)
                            )
                            Text(
                                text = "既存メンバーの承認が完了すると自動的に見守り通信が開始されます。",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF5D4037)
                            )
                        }
                    }
                }
            }
        } else if (myMemberStatus == MemberStatus.REJECTED) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "グループ参加が拒否されました",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC62828)
                            )
                            Text(
                                text = "設定画面から別のグループ名に変更するか、相手にご確認ください。",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF37474F)
                            )
                        }
                    }
                }
            }
        }

        // ネットワーク接続 & 定期同期ステータスカード
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSyncing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isSyncing) Icons.Default.CloudSync else Icons.Default.Cloud,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "クラウド定期同期 (Firebase)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSyncing) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "同期中...",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                } else {
                                    Text(
                                        text = if (myMemberStatus == MemberStatus.APPROVED) "待機中 (高信頼・省電力)" else "承認待ち",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "グループ: $groupName",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val nextTimeStr = if (nextSyncTimestamp > 0L) hmFormat.format(Date(nextSyncTimestamp)) else "--:--"
                            val lastSyncStr = if (lastSyncTimestamp > 0L) hmFormat.format(Date(lastSyncTimestamp)) else "未実行"
                            Text(
                                text = "次回同期: $nextTimeStr (前回: $lastSyncStr)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedButton(
                            onClick = onManualSync,
                            enabled = !isSyncing && myMemberStatus == MemberStatus.APPROVED,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("今すぐ同期", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 自身の安否ステータス送信（クイックボタン）
        item {
            Text(
                text = "自分のステータスを相手に伝える",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 元気ですボタン
                ElevatedButton(
                    onClick = { onSendStatus(PacketType.STATUS_FINE) },
                    enabled = myMemberStatus == MemberStatus.APPROVED,
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = Color(0xFF2E7D32),
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SentimentVerySatisfied, contentDescription = null, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "元気です",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Text(
                            text = "即座にFirebase更新",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }

                // 良くないボタン
                ElevatedButton(
                    onClick = { onSendStatus(PacketType.STATUS_UNWELL) },
                    enabled = myMemberStatus == MemberStatus.APPROVED,
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = Color(0xFFE65100),
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SentimentDissatisfied, contentDescription = null, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "良くない",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Text(
                            text = "即座にFirebase更新",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        // 自分の端末の活動状況
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "自分のスマートフォン",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 手動点灯テストボタン
                        OutlinedButton(
                            onClick = onTestScreenOn,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("解除テスト", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("本日の操作回数", style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
                            Text(
                                text = "${todayScreenOnCount}回",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("最終画面操作", style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
                            Text(
                                text = if (lastLocalScreenOnTime > 0L) timeFormat.format(Date(lastLocalScreenOnTime)) else "記録なし",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // 見守り相手の一覧
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "見守り相手の状況 (${peers.size}人)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (peers.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.HourglassEmpty,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "相手端末からの通信を待機しています...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "（相手が同じグループ名「$groupName」を設定すると表示されます）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        } else {
            items(peers, key = { it.id }) { peer ->
                PeerSafetyCard(
                    peer = peer,
                    timeoutDurationMs = timeoutDurationMs,
                    currentTime = currentTime,
                    onEditPeer = onEditPeer,
                    onPingPeer = onPingPeer
                )
            }
        }
    }
}

@Composable
fun PeerSafetyCard(
    peer: PeerInfo,
    timeoutDurationMs: Long,
    currentTime: Long,
    onEditPeer: (PeerInfo) -> Unit,
    onPingPeer: (PeerInfo) -> Unit
) {
    val fullTimeFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.JAPAN) }
    var expandedTimestamps by remember { mutableStateOf(false) }

    val lastActivity = if (peer.unlockTimestamps.isNotEmpty()) {
        maxOf(peer.lastSeenTimestamp, peer.lastUnlockTimestamp)
    } else {
        peer.lastSeenTimestamp
    }

    val elapsed = if (lastActivity > 0L) currentTime - lastActivity else 0L
    val hasReceived = lastActivity > 0L
    val isConnectionTimedOut = hasReceived && (elapsed >= timeoutDurationMs)
    val isNoActivity = hasReceived && (peer.unlockTimestamps.isEmpty() || isConnectionTimedOut)

    val (statusColor, statusBgColor, statusIcon, statusTitle) = when {
        !hasReceived -> Quadruple(
            MaterialTheme.colorScheme.outline,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            Icons.Default.HourglassEmpty,
            "待機中 (未受信)"
        )
        isConnectionTimedOut -> Quadruple(
            Color(0xFFD32F2F),
            Color(0xFFFFEBEE),
            Icons.Default.Warning,
            "🚨 通信途絶 (${formatElapsedTime(elapsed)})"
        )
        isNoActivity -> Quadruple(
            Color(0xFFD32F2F),
            Color(0xFFFFEBEE),
            Icons.Default.Warning,
            "🚨 過去24時間 操作なし"
        )
        peer.lastStatus == PacketType.STATUS_UNWELL -> Quadruple(
            Color(0xFFE65100),
            Color(0xFFFFF3E0),
            Icons.Default.SentimentDissatisfied,
            "😣 良くない"
        )
        peer.lastStatus == PacketType.STATUS_FINE -> Quadruple(
            Color(0xFF2E7D32),
            Color(0xFFE8F5E9),
            Icons.Default.SentimentVerySatisfied,
            "😄 元気です"
        )
        else -> Quadruple(
            Color(0xFF2E7D32),
            Color(0xFFE8F5E9),
            Icons.Default.CheckCircle,
            "🟢 正常 (${peer.unlockCount24h}件 操作確認)"
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = statusBgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isNoActivity || isConnectionTimedOut) 6.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = peer.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row {
                    IconButton(onClick = { onPingPeer(peer) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "テスト通信", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { onEditPeer(peer) }) {
                        Icon(Icons.Default.Edit, contentDescription = "編集")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ステータスバッジ
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = statusColor.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusTitle,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("24時間のスマホ操作", style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
                    Text(
                        text = if (!hasReceived) "未受信" else if (isNoActivity) "0件 (無活動)" else "${peer.unlockCount24h}件",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isNoActivity) Color(0xFFD32F2F) else Color.Black
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("最終同期・受信日時", style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
                    Text(
                        text = if (hasReceived) fullTimeFormat.format(Date(peer.lastSeenTimestamp)) else "-",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (peer.lastMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "詳細: ${peer.lastMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray
                )
            }

            // タイムスタンプ履歴の開閉
            if (peer.unlockTimestamps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { expandedTimestamps = !expandedTimestamps }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.DarkGray)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "直近の操作履歴 (${peer.unlockTimestamps.size}件)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.DarkGray
                        )
                    }
                    Icon(
                        if (expandedTimestamps) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.DarkGray
                    )
                }

                AnimatedVisibility(visible = expandedTimestamps) {
                    val timeFormatShort = remember { SimpleDateFormat("HH:mm", Locale.JAPAN) }
                    val sortedTimestamps = peer.unlockTimestamps.sortedDescending()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "直近のロック解除:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.DarkGray
                        )
                        val previewList = sortedTimestamps.take(10)
                        val timesStr = previewList.joinToString(", ") { timeFormatShort.format(Date(it)) }
                        Text(
                            text = if (sortedTimestamps.size > 10) "$timesStr ... 他${sortedTimestamps.size - 10}件" else timesStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.DarkGray
                        )
                    }
                }
            }
        }
    }
}

private fun formatElapsedTime(millis: Long): String {
    val seconds = millis / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        days > 0 -> "${days}日 ${hours % 24}時間 前"
        hours > 0 -> "${hours}時間 ${minutes % 60}分 前"
        minutes > 0 -> "${minutes}分 ${seconds % 60}秒 前"
        else -> "${seconds}秒 前"
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
