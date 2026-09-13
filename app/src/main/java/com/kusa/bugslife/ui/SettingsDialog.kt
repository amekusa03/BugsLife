package com.kusa.bugslife.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kusa.bugslife.util.NetworkUtils

@Composable
fun SettingsDialog(
    currentName: String,
    currentPort: Int,
    currentTimeoutMs: Long,
    currentSkyWayEnabled: Boolean,
    currentSkyWayAppId: String,
    currentSkyWaySecretKey: String,
    currentSkyWayRoomName: String,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        port: Int,
        timeoutMs: Long,
        isSkyWayEnabled: Boolean,
        skywayAppId: String,
        skywaySecretKey: String,
        skywayRoomName: String
    ) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var portStr by remember { mutableStateOf(currentPort.toString()) }
    var selectedTimeoutMs by remember { mutableStateOf(currentTimeoutMs) }

    var isSkyWayEnabled by remember { mutableStateOf(currentSkyWayEnabled) }
    var skywayAppId by remember { mutableStateOf(currentSkyWayAppId) }
    var skywaySecretKey by remember { mutableStateOf(currentSkyWaySecretKey) }
    var skywayRoomName by remember { mutableStateOf(currentSkyWayRoomName) }

    val localIp = remember { NetworkUtils.getLocalIpAddress() }

    val timeoutOptions = listOf(
        Pair("1分 (動作テスト用)", 60 * 1000L),
        Pair("5分 (動作テスト用)", 5 * 60 * 1000L),
        Pair("1時間", 60 * 60 * 1000L),
        Pair("12時間", 12 * 60 * 60 * 1000L),
        Pair("24時間 (通常運用)", 24 * 60 * 60 * 1000L)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("アプリ設定")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 自端末情報カード
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                            Text("あなたの端末のIPアドレス (LAN)", style = MaterialTheme.typography.labelMedium)
                        }
                        Text(
                            text = localIp,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // ユーザー名設定
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("あなたの表示名") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                // SkyWay インターネットP2P設定
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "SkyWay インターネット接続",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "外出先や4G/5G回線でもP2P通信",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = isSkyWayEnabled,
                        onCheckedChange = { isSkyWayEnabled = it }
                    )
                }

                if (isSkyWayEnabled) {
                    OutlinedTextField(
                        value = skywayRoomName,
                        onValueChange = { skywayRoomName = it },
                        label = { Text("見守りグループ名 (Room名)") },
                        leadingIcon = { Icon(Icons.Default.MeetingRoom, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "※同じグループ名を登録した端末同士で相互接続されます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = skywayAppId,
                        onValueChange = { skywayAppId = it },
                        label = { Text("SkyWay Application ID") },
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = skywaySecretKey,
                        onValueChange = { skywaySecretKey = it },
                        label = { Text("SkyWay Secret Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider()

                // 監視タイムアウト設定
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "無反応検知タイムアウト",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                timeoutOptions.forEach { (label, duration) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTimeoutMs = duration }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedTimeoutMs == duration),
                            onClick = { selectedTimeoutMs = duration }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                HorizontalDivider()

                // 受信ポート番号
                OutlinedTextField(
                    value = portStr,
                    onValueChange = { portStr = it },
                    label = { Text("LAN UDPポート番号 (初期値: 8888)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val port = portStr.toIntOrNull() ?: 8888
                    onSave(
                        name.trim(),
                        port,
                        selectedTimeoutMs,
                        isSkyWayEnabled,
                        skywayAppId.trim(),
                        skywaySecretKey.trim(),
                        skywayRoomName.trim()
                    )
                },
                enabled = name.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}
