package com.ekko.smartfan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ekko.smartfan.data.FanMqtt
import com.ekko.smartfan.data.RuleCodec
import com.ekko.smartfan.data.TimerRule
import com.ekko.smartfan.ui.theme.Purple

private val chipColors
    @Composable get() = FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surface,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedContainerColor = Purple,
        selectedLabelColor = Color.White
    )

@Composable
fun TimerScreen() {
    val rulesMap by FanMqtt.rules.collectAsState()
    val conn by FanMqtt.state.collectAsState()

    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TimerRule?>(null) }
    var editingSlot by remember { mutableIntStateOf(1) }

    val slots = (1..FanMqtt.SLOT_COUNT).map { it to RuleCodec.decode(it, rulesMap["rule$it"]) }
    val usedSlots = slots.filter { it.second != null }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val free = slots.firstOrNull { it.second == null }?.first
                    editingSlot = free ?: 1
                    editing = null
                    showDialog = true
                },
                containerColor = Purple
            ) {
                Icon(Icons.Filled.Add, contentDescription = "新增定时", tint = Color.White)
            }
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("星期定时", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                StatusChip(conn.connected)
            }
            Text(
                "规则保存在设备 ${FanMqtt.SLOT_COUNT} 个槽位里，手机不在线也照常执行",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            if (usedSlots.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("设备上还没有定时规则", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "点右下角 + 添加，例如工作日 08:00 开启 60%",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            usedSlots.forEach { (slot, rule) ->
                if (rule != null) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clickable { editing = rule; editingSlot = slot; showDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    rule.timeText,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (rule.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "槽位 $slot · ${rule.daysText} · ${rule.actionLabel}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = rule.enabled,
                                onCheckedChange = { on ->
                                    val days = if (on) (if (rule.days.isEmpty()) (1..7).toSet() else rule.days) else emptySet()
                                    FanMqtt.setRule(slot, RuleCodec.encode(rule.copy(enabled = on, days = days, slot = slot)))
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Purple
                                )
                            )
                            IconButton(onClick = { FanMqtt.setRule(slot, "0") }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "已用 ${usedSlots.size}/${FanMqtt.SLOT_COUNT} 个槽位",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showDialog) {
        RuleDialog(
            initial = editing,
            slot = editingSlot,
            onDismiss = { showDialog = false },
            onSave = { rule ->
                FanMqtt.setRule(rule.slot, RuleCodec.encode(rule))
                showDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleDialog(initial: TimerRule?, slot: Int, onDismiss: () -> Unit, onSave: (TimerRule) -> Unit) {
    var hour by remember { mutableIntStateOf(initial?.hour ?: 8) }
    var minute by remember { mutableIntStateOf(initial?.minute ?: 0) }
    var days by remember {
        mutableStateOf(
            when {
                initial == null -> setOf(1, 2, 3, 4, 5, 6, 7)
                initial.days.isEmpty() -> setOf(1, 2, 3, 4, 5, 6, 7)
                else -> initial.days
            }
        )
    }
    var action by remember { mutableStateOf(initial?.action ?: "ON") }
    var speed by remember { mutableIntStateOf(initial?.speed ?: 60) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增定时（槽位 $slot）" else "编辑定时（槽位 $slot）") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    WheelPicker(
                        count = 24,
                        selected = hour,
                        onSelected = { hour = it },
                        modifier = Modifier.weight(1f)
                    )
                    Text(":", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    WheelPicker(
                        count = 60,
                        selected = minute,
                        onSelected = { minute = it },
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("重复", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { d ->
                        FilterChip(
                            selected = d in days,
                            onClick = { days = if (d in days) days - d else days + d },
                            label = { Text(TimerRule.DAY_NAMES[d - 1], fontSize = 12.sp) },
                            colors = chipColors
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "每天" to setOf(1, 2, 3, 4, 5, 6, 7),
                        "工作日" to setOf(1, 2, 3, 4, 5),
                        "周末" to setOf(6, 7)
                    ).forEach { (label, set) ->
                        FilterChip(
                            selected = days == set,
                            onClick = { days = set },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = chipColors
                        )
                    }
                }

                Text("动作", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ON" to "开启", "OFF" to "关闭", "SPEED" to "设定转速").forEach { (v, label) ->
                        FilterChip(
                            selected = action == v,
                            onClick = { action = v },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = chipColors
                        )
                    }
                }

                if (action == "SPEED") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("转速", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        Text("$speed%", fontSize = 13.sp, color = Purple, fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = speed.toFloat(),
                        onValueChange = { speed = it.toInt().coerceIn(1, 100) },
                        valueRange = 1f..100f
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val fixedDays = if (days.isEmpty()) setOf(1, 2, 3, 4, 5, 6, 7) else days
                onSave(
                    (initial ?: TimerRule()).copy(
                        hour = hour,
                        minute = minute,
                        days = fixedDays,
                        action = action,
                        speed = speed,
                        enabled = true,
                        slot = slot
                    )
                )
            }) { Text("保存到设备") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
