package com.ekko.smartfan.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ekko.smartfan.data.AppPrefs
import com.ekko.smartfan.data.FanMqtt
import com.ekko.smartfan.ui.theme.Purple
import com.ekko.smartfan.ui.theme.PurpleDark
import com.ekko.smartfan.ui.theme.PurpleLight
import kotlinx.coroutines.delay
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(onOpenSettings: () -> Unit) {
    val state by FanMqtt.state.collectAsState()
    val link by FanMqtt.link.collectAsState()
    val ctx = LocalContext.current
    val prefs = remember { AppPrefs(ctx) }

    var dragging by remember { mutableStateOf(false) }
    var localSpeed by remember { mutableIntStateOf(prefs.lastSpeed) }

    // 乐观开关：点击立即响应，2.5 秒内无设备回显则回滚并提示
    var uiOn by remember { mutableStateOf(state.isOn) }
    var pendingAt by remember { mutableLongStateOf(0L) }
    var hint by remember { mutableStateOf("") }

    // 只在状态稳定 400ms 后才同步给 UI，避免瞬时异常消息造成开关抖动
    LaunchedEffect(state.isOn) {
        if (pendingAt == 0L) {
            delay(400)
            uiOn = state.isOn
        }
    }
    LaunchedEffect(pendingAt, state.isOn) {
        if (pendingAt > 0L) {
            if (state.isOn == uiOn) {
                pendingAt = 0L
                hint = ""
            } else {
                delay(2500)
                if (pendingAt > 0L && state.isOn != uiOn) {
                    uiOn = state.isOn
                    pendingAt = 0L
                    hint = "设备未确认，已恢复为实际状态"
                }
            }
        }
    }

    LaunchedEffect(state.speed, state.isOn) {
        if (!dragging && state.isOn) localSpeed = state.speed
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("智能风扇", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${prefs.host}:${prefs.port}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusChip(link.brokerConnected && state.connected)
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "设置", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(0.82f).aspectRatio(1f),
                    contentAlignment = Alignment.Center
                ) {
                    FanDial(
                        isOn = state.isOn,
                        speed = if (state.isOn) localSpeed.toFloat() else 0f,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                    )
                    Text(
                        text = when {
                            !link.brokerConnected -> "未连接服务器"
                            !link.topicReady -> "正在同步设备…"
                            !state.connected -> "设备离线"
                            uiOn -> "运行中"
                            else -> "已关闭"
                        },
                        fontSize = 14.sp,
                        color = if (state.isOn) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (hint.isNotEmpty()) {
                    Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        if (state.isOn) "风扇已开启" else "风扇已关闭",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Switch(
                        checked = uiOn,
                        enabled = (link.topicReady || link.brokerConnected) && pendingAt == 0L,
                        onCheckedChange = { on ->
                            uiOn = on
                            hint = ""
                            val ok = if (on) FanMqtt.turnOnWithSpeed(if (localSpeed < 1) 60 else localSpeed)
                                     else FanMqtt.setPower(false)
                            pendingAt = System.currentTimeMillis()
                            if (!ok) {
                                hint = "命令未发出（设备状态未同步），稍后重试"
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Purple,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
        }

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("风速", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        "$localSpeed%",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Purple
                    )
                }
                Slider(
                    value = localSpeed.toFloat(),
                    onValueChange = {
                        dragging = true
                        localSpeed = it.toInt().coerceIn(1, 100)
                    },
                    onValueChangeFinished = {
                        dragging = false
                        prefs.lastSpeed = localSpeed
                        val ok = if (uiOn || state.isOn) FanMqtt.setSpeed(localSpeed)
                                 else FanMqtt.turnOnWithSpeed(localSpeed)
                        if (ok) {
                            uiOn = true
                            pendingAt = System.currentTimeMillis()
                        } else {
                            hint = "命令未发出（设备状态未同步），稍后重试"
                        }
                    },
                    valueRange = 1f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = Purple,
                        activeTrackColor = Purple,
                        inactiveTrackColor = MaterialTheme.colorScheme.surface
                    )
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(30 to "低", 60 to "中", 80 to "高", 100 to "全速").forEach { (v, label) ->
                        FilterChip(
                            selected = localSpeed == v,
                            onClick = {
                                localSpeed = v
                                prefs.lastSpeed = v
                                val ok = FanMqtt.turnOnWithSpeed(v)
                                if (ok) {
                                    uiOn = true
                                    hint = ""
                                    pendingAt = System.currentTimeMillis()
                                } else {
                                    hint = "命令未发出（设备状态未同步），稍后重试"
                                }
                            },
                            label = { Text(label, fontSize = 13.sp, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Purple,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun FanDial(isOn: Boolean, speed: Float, modifier: Modifier = Modifier) {
    var rotation by remember { mutableFloatStateOf(0f) }
    val animatedSpeed by animateFloatAsState(
        targetValue = if (isOn) speed else 0f,
        animationSpec = tween(durationMillis = 700),
        label = "speed"
    )

    LaunchedEffect(isOn, speed) {
        var last = 0L
        val degPerSec = 40f + speed * 6f
        while (true) {
            withFrameNanos { now ->
                if (last != 0L && isOn) {
                    val dt = (now - last) / 1_000_000_000f
                    rotation = (rotation + dt * degPerSec) % 360f
                }
                last = now
            }
        }
    }

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val bladeColor = if (isOn) Purple else OffColor.copy(alpha = 0.45f)

    Canvas(modifier = modifier) {
        val r = min(size.width, size.height) / 2f
        val stroke = r * 0.11f
        drawCircle(color = trackColor, radius = r - stroke / 2f, style = Stroke(width = stroke))
        if (animatedSpeed > 0.5f) {
            drawArc(
                brush = Brush.sweepGradient(listOf(PurpleLight, Purple, PurpleDark, PurpleLight)),
                startAngle = -90f,
                sweepAngle = 360f * (animatedSpeed / 100f),
                useCenter = false,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = androidx.compose.ui.geometry.Size(width = size.width - stroke, height = size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }

        val bladeR = r * 0.62f
        val c = center
        val alpha = if (isOn) 1f else 0.45f
        rotate(degrees = rotation, pivot = c) {
            repeat(4) { i ->
                rotate(degrees = i * 90f, pivot = c) {
                    val p = Path().apply {
                        moveTo(c.x, c.y)
                        cubicTo(
                            c.x + bladeR * 0.10f, c.y - bladeR * 0.55f,
                            c.x + bladeR * 0.95f, c.y - bladeR * 0.80f,
                            c.x + bladeR * 0.92f, c.y - bladeR * 0.12f
                        )
                        cubicTo(
                            c.x + bladeR * 0.88f, c.y + bladeR * 0.30f,
                            c.x + bladeR * 0.30f, c.y + bladeR * 0.24f,
                            c.x, c.y
                        )
                        close()
                    }
                    drawPath(p, color = bladeColor.copy(alpha = alpha))
                }
            }
            drawCircle(color = if (isOn) PurpleDark else OffColor, radius = r * 0.12f)
            drawCircle(color = Color.White.copy(alpha = 0.85f), radius = r * 0.05f)
        }
    }
}
