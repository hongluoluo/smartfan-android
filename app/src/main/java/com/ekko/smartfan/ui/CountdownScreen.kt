package com.ekko.smartfan.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ekko.smartfan.data.FanMqtt
import com.ekko.smartfan.ui.theme.Purple
import com.ekko.smartfan.ui.theme.PurpleDark
import com.ekko.smartfan.ui.theme.PurpleLight
import kotlinx.coroutines.delay
import kotlin.math.min

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CountdownScreen() {
    val deviceRemaining by FanMqtt.cdRemaining.collectAsState()
    val deviceAction by FanMqtt.cdAction.collectAsState()

    var selectedMinutes by remember { mutableIntStateOf(30) }
    var customMinutes by remember { mutableIntStateOf(90) }
    var showCustom by remember { mutableStateOf(false) }
    var totalSec by remember { mutableLongStateOf(0L) }
    var startPendingAt by remember { mutableLongStateOf(0L) }
    var startHint by remember { mutableStateOf("") }

    // 设备 3s 上报一次，本地按秒插值显示，避免圆环跳格
    var shown by remember { mutableLongStateOf(deviceRemaining) }
    var lastSync by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(deviceRemaining) {
        shown = deviceRemaining
        lastSync = System.currentTimeMillis()
        if (deviceRemaining <= 0L) totalSec = 0L
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            val elapsed = (System.currentTimeMillis() - lastSync) / 1000
            shown = (deviceRemaining - elapsed).coerceAtLeast(0L)
        }
    }

    // 启动后 8 秒仍无剩余时间 → 提示设备未响应（通常是设备时间未同步或离线）
    LaunchedEffect(deviceRemaining, startPendingAt) {
        if (startPendingAt > 0L) {
            if (deviceRemaining > 0L) {
                startPendingAt = 0L
                startHint = ""
            } else {
                delay(8000)
                if (startPendingAt > 0L && deviceRemaining <= 0L) {
                    startHint = "设备未响应：请确认设备在线且已完成 NTP 授时（设置页可看设备时间）"
                    startPendingAt = 0L
                }
            }
        }
    }

    val running = deviceRemaining > 0L
    val total = if (totalSec > 0L) totalSec else (selectedMinutes * 60L)
    val progress = if (total > 0L) (shown.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "glow"
    )

    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surface,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedContainerColor = Purple,
        selectedLabelColor = Color.White
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text("倒计时", style = MaterialTheme.typography.headlineMedium)
        Text(
            "倒计时在设备上执行，App 关闭或手机关机都不影响",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = min(size.width, size.height) / 2f
                val stroke = r * 0.10f
                drawCircle(
                    color = Purple.copy(alpha = 0.12f),
                    radius = r - stroke / 2f,
                    style = Stroke(width = stroke)
                )
                if (progress > 0f) {
                    drawArc(
                        brush = Brush.sweepGradient(listOf(PurpleLight, Purple, PurpleDark, PurpleLight)),
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                        alpha = 0.55f + pulse * 0.45f
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatRemaining(shown * 1000),
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (running) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (running) "设备倒计时中 · 到点${if (deviceAction == "ON") "开启" else "关闭"}" else "未设置倒计时",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                if (running) {
                    OutlinedButton(
                        onClick = { FanMqtt.cancelCountdown() },
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.5.dp, Purple)
                    ) {
                        Text("取消倒计时", color = Purple, fontSize = 14.sp)
                    }
                } else {
                    Button(
                        onClick = {
                            totalSec = selectedMinutes * 60L
                            startHint = ""
                            startPendingAt = System.currentTimeMillis()
                            FanMqtt.startCountdown(selectedMinutes, deviceAction)
                        },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Purple,
                            contentColor = Color.White
                        )
                    ) {
                        Text("开始倒计时", fontSize = 14.sp)
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("时长", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(30, 60, 120).forEach { m ->
                        FilterChip(
                            selected = selectedMinutes == m,
                            onClick = { selectedMinutes = m },
                            label = { Text("$m 分钟", fontSize = 12.sp, maxLines = 1) },
                            colors = chipColors
                        )
                    }
                }

                val customActive = selectedMinutes !in listOf(30, 60, 120)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = customActive,
                        onClick = { showCustom = true },
                        label = {
                            Text(
                                text = if (customActive) formatMinutes(selectedMinutes) else "自定义时长",
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        colors = chipColors
                    )
                }

                if (startHint.isNotEmpty()) {
                    Text(startHint, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }

                Text("到点动作", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("OFF" to "关闭风扇", "ON" to "开启风扇").forEach { (v, label) ->
                        FilterChip(
                            selected = deviceAction == v,
                            onClick = { FanMqtt.setCdAction(v) },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = chipColors
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showCustom) {
        CustomDurationDialog(
            initialMinutes = customMinutes,
            onDismiss = { showCustom = false },
            onConfirm = { m ->
                customMinutes = m
                selectedMinutes = m
                showCustom = false
            }
        )
    }
}

@Composable
private fun CustomDurationDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var hours by remember { mutableIntStateOf((initialMinutes / 60).coerceIn(0, 23)) }
    var mins by remember { mutableIntStateOf(initialMinutes % 60) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义时长") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    WheelPicker(
                        count = 24,
                        selected = hours,
                        onSelected = { hours = it },
                        modifier = Modifier.weight(1f)
                    )
                    Text("时", fontSize = 16.sp, modifier = Modifier.padding(horizontal = 4.dp))
                    WheelPicker(
                        count = 60,
                        selected = mins,
                        onSelected = { mins = it },
                        modifier = Modifier.weight(1f)
                    )
                    Text("分", fontSize = 16.sp, modifier = Modifier.padding(horizontal = 4.dp))
                }
                Spacer(Modifier.height(8.dp))
                val totalMin = hours * 60 + mins
                Text(
                    if (totalMin <= 0) "至少需要 1 分钟" else "共 $totalMin 分钟",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val m = (hours * 60 + mins).coerceAtLeast(1)
                onConfirm(m)
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun formatMinutes(m: Int): String {
    val h = m / 60
    val mm = m % 60
    return when {
        h == 0 -> "$mm 分钟"
        mm == 0 -> "$h 小时"
        else -> "$h 小时 $mm 分"
    }
}

private fun formatRemaining(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
