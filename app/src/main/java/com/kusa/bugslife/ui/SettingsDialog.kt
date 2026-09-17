package com.kusa.bugslife.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kusa.bugslife.network.FirestorePeerMessenger
import kotlinx.coroutines.launch

@Composable
fun SettingsDialog(
    currentName: String,
    currentTimeoutMs: Long,
    currentSyncEnabled: Boolean,
    currentGroupName: String,
    myUserId: String,
    onDismiss: () -> Unit,
    onSave: (name: String, timeoutMs: Long, isSyncEnabled: Boolean, groupName: String, isJoinRequest: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var groupName by remember { mutableStateOf(currentGroupName) }
    var isSyncEnabled by remember { mutableStateOf(currentSyncEnabled) }
    var selectedTimeoutMs by remember { mutableLongStateOf(currentTimeoutMs) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isCheckingGroup by remember { mutableStateOf(false) }
    var showJoinConfirmDialog by remember { mutableStateOf(false) }

    val timeoutOptions = listOf(
        "12時間" to 12 * 60 * 60 * 1000L,
        "24時間（推奨）" to 24 * 60 * 60 * 1000L,
        "36時間" to 36 * 60 * 60 * 1000L,
        "48時間" to 48 * 60 * 60 * 1000L
    )

    if (showJoinConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showJoinConfirmDialog = false },
            title = { Text("グループ参加の確認", fontWeight = FontWeight.Bold) },
            text = {
                Text("既にグループ「${groupName.trim()}」が存在します。\nこのグループに参加申請しますか？")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showJoinConfirmDialog = false
                        onSave(
                            name.trim(),
                            selectedTimeoutMs,
                            isSyncEnabled,
                            groupName.trim(),
                            true // 参加申請 (PENDING)
                        )
                    }
                ) {
                    Text("Yes（参加する）")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showJoinConfirmDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("見守り設定", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // ユーザー名
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("あなたの名前・ニックネーム") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // Firebase クラウド同期設定
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "クラウド見守り同期 (Firebase)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "毎時05分または状態変更時に自動同期",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = isSyncEnabled,
                        onCheckedChange = { isSyncEnabled = it }
                    )
                }

                if (isSyncEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text("見守りグループ名（合言葉）") },
                        leadingIcon = { Icon(Icons.Default.Group, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "※同じグループ名を入力すると、既存メンバーに承認をリクエストできます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

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

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // 定時同期・省電力の端末設定案内
                Text(
                    text = "毎時05分 定時同期の設定",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "画面消灯中（スリープ時）でも定時に通信を行うため、以下のOS設定を「許可 / 制限なし」にしてください。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                // バッテリー最適化除外ボタン
                OutlinedButton(
                    onClick = {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            } else {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.BatteryAlert, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("バッテリー最適化の除外設定を開く")
                }

                // アラームとリマインダー権限設定ボタン (Android 12+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("アラームとリマインダー許可を開く")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedGroup = groupName.trim()
                    if (isSyncEnabled && trimmedGroup.isNotBlank() && trimmedGroup != currentGroupName) {
                        isCheckingGroup = true
                        coroutineScope.launch {
                            val messenger = FirestorePeerMessenger(context, trimmedGroup, myUserId)
                            val exists = messenger.checkGroupExists(trimmedGroup)
                            isCheckingGroup = false
                            if (exists) {
                                showJoinConfirmDialog = true
                            } else {
                                onSave(
                                    name.trim(),
                                    selectedTimeoutMs,
                                    isSyncEnabled,
                                    trimmedGroup,
                                    false
                                )
                            }
                        }
                    } else {
                        onSave(
                            name.trim(),
                            selectedTimeoutMs,
                            isSyncEnabled,
                            trimmedGroup,
                            false
                        )
                    }
                },
                enabled = !isCheckingGroup && name.isNotBlank() && (!isSyncEnabled || groupName.isNotBlank())
            ) {
                if (isCheckingGroup) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("確認中...")
                } else {
                    Text("保存")
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !isCheckingGroup) {
                Text("キャンセル")
            }
        }
    )
}
