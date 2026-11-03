package com.ekko.smartfan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ekko.smartfan.data.AppPrefs
import com.ekko.smartfan.data.FanMqtt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { AppPrefs(ctx) }
    val state by FanMqtt.state.collectAsState()
    val devTime by FanMqtt.devTime.collectAsState()
    val link by FanMqtt.link.collectAsState()

    var host by remember { mutableStateOf(prefs.host) }
    var port by remember { mutableStateOf(prefs.port.toString()) }
    var user by remember { mutableStateOf(prefs.user) }
    var pass by remember { mutableStateOf(prefs.pass) }
    var savedTip by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("设置") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("MQTT 连接", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        StatusChip(state.connected)
                    }
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Broker 地址") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter { c -> c.isDigit() } },
                        label = { Text("端口") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it },
                        label = { Text("用户名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("密码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Button(
                onClick = {
                    prefs.host = host.trim()
                    prefs.port = port.toIntOrNull() ?: 1883
                    prefs.user = user.trim()
                    prefs.pass = pass
                    FanMqtt.disconnect()
                    FanMqtt.loadConfig(ctx)
                    FanMqtt.connect()
                    savedTip = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存并重新连接")
            }
            if (savedTip) {
                Text(
                    "已保存，正在重新连接…",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("关于", style = MaterialTheme.typography.titleMedium)
                    Text("智能风扇 v2.0.0", fontSize = 13.sp)
                    Text("设备时间: ${devTime}", fontSize = 13.sp)
                    Text(
                        "服务器连接: ${if (link.brokerConnected) "正常" else "断开"} · 设备: ${if (state.connected) "在线" else "离线"}",
                        fontSize = 12.sp
                    )
                    Text("命令主题: ${if (link.topicReady) link.cmdTopic else "同步中…"}", fontSize = 11.sp)
                    if (link.lastTx.isNotEmpty()) Text("最近发送: ${link.lastTx}", fontSize = 11.sp)
                    if (link.lastRx.isNotEmpty()) Text("最近接收: ${link.lastRx}", fontSize = 11.sp)
                    if (link.lastError.isNotEmpty()) {
                        Text("错误: ${link.lastError}", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                    Text(
                        "控制主题: ${FanMqtt.PREFIX}/fan/…\n定时与倒计时在手机本地执行（AlarmManager）。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
