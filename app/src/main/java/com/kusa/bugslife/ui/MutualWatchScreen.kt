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
import androidx.compose.material.icons.filled.HourglassEmpty
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
import com.kusa.bugslife.data.PacketType
import com.kusa.bugslife.data.PeerInfo
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MutualWatchScreen(
    todayScreenOnCount: Int,
    lastSentTimestamp: Long,
    lastSyncTimestamp: Long,
    nextSyncTimestamp: Long,
    isSyncing: Boolean,
    peers: List<PeerInfo>,
    timeoutDurationMs: Long,
    isSkyWayEnabled: Boolean,
    skywayRoomName: String,
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
        // ネットワーク接続 & 毎時05分同期ステータスカード
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
                                text = "毎時05分 定期同期モデル",
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
                                        text = "同期中 (接続中)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                } else {
                                    Text(
                                        text = "スリープ待機 (省電力)",
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
                                text = "Room: $skywayRoomName",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val nextTimeStr = if (nextSyncTimestamp > 0L) hmFormat.format(Date(nextSyncTimestamp)) else "--:--"
                            val lastSyncStr = if (lastSyncTimestamp > 0L) hmFormat.format(Date(lastSyncTimestamp)) else "未実行"
                            Text(
                                text = "次回同期: $nextTimeStr (前回: $lastSyncStr)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        OutlinedButton(
                            onClick = onManualSync,
                            enabled = !isSyncing && isSkyWayEnabled,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = ButtonDefaults.ContentPadding
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("今すぐ同期", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 自端末のステータスカード (親切な説明付き)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
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
                                text = "あなたの端末 (見守られ & 見守り中)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("本日のスマホ操作回数", style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
                            Text(
                                text = "${todayScreenOnCount} 回",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("最終送信時刻", style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
                            Text(
                                text = if (lastSentTimestamp > 0L) fullTimeFormat.format(Date(lastSentTimestamp)) else "次回05分に送信",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "※ スマホを操作（画面ロック解除）すると回数が記録され、毎時05分にまとめて相手へ安全に送信されます。（※電話着信や通知だけで誤カウントされる心配はありません）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ワンタップ安否送信ボタン
        item {
            Text(
                text = "ワンタップ安否報告",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 元気ですボタン
                ElevatedButton(
                    onClick = { onSendStatus(PacketType.STATUS_FINE) },
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = Color(0xFF2E7D32),
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 3.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "😄", fontSize = 22.sp)
                        Text(
                            text = "元気です！",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 良くないボタン
                ElevatedButton(
                    onClick = { onSendStatus(PacketType.STATUS_UNWELL) },
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = Color(0xFFC62828),
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 3.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "😣", fontSize = 22.sp)
                        Text(
                            text = "良くない",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 相互見守り相手リスト
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "見守り相手の状況 (${peers.size}台)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (peers.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.HourglassEmpty,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "見守り相手の登録待機中",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "同一Room名（見守りグループ名）を設定した相手端末と毎時05分に同期すると、自動的に一覧に登録されます。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(peers, key = { it.id }) { peer ->
                val hasReceived = peer.lastSeenTimestamp > 0L
                val elapsedMs = if (hasReceived) currentTime - peer.lastSeenTimestamp else Long.MAX_VALUE
                val isTimedOut = hasReceived && elapsedMs >= timeoutDurationMs

                val (statusColor, statusBgColor, statusIcon, statusTitle) = when {
                    !hasReceived -> Quadruple(
                        Color.Gray,
                        Color(0xFFEEEEEE),
                        Icons.Default.HourglassEmpty,
                        "信号待機中"
                    )
                    isTimedOut -> Quadruple(
                        Color(0xFFD32F2F),
                        Color(0xFFFFEBEE),
                        Icons.Default.Warning,
                        "⚠️ 24時間無反応 (安否確認要)"
                    )
                    peer.lastStatus == PacketType.STATUS_UNWELL -> Quadruple(
                        Color(0xFFE65100),
                        Color(0xFFFFF3E0),
                        Icons.Default.SentimentDissatisfied,
                        "😣 体調が良くない"
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
                        "🟢 正常 (操作確認あり)"
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = statusBgColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isTimedOut) 6.dp else 2.dp)
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
                                Text("前回の操作検知から", style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
                                Text(
                                    text = if (hasReceived) formatElapsedTime(elapsedMs) else "未受信",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isTimedOut) Color(0xFFD32F2F) else Color.Black
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text("最終受信日時", style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
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
