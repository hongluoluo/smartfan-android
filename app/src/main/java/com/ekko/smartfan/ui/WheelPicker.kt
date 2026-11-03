package com.ekko.smartfan.ui

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 惯性滚轮选择器：中间高亮带 + 松手必吸附 + 逐格音效 + 点击某项直接选中。
 *
 * 关键点（v1.4 修复）：
 * 1. 初始位置通过 rememberLazyListState(initialFirstVisibleItemIndex) 给定，
 *    不再用 scrollToItem —— 否则吸附监听会在首帧把位置重置为 0。
 * 2. 吸附监听等首帧之后再启动（ready 门控），避免开窗瞬间误触发。
 * 3. 松手后无论是否带动量，一律对齐到最近的整数项并立即上报，
 *    不会出现"停在两个数字中间导致取值不生效"。
 */
@Composable
fun WheelPicker(
    count: Int,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 42.dp,
    visibleCount: Int = 5,
    playTick: Boolean = true,
    formatter: (Int) -> String = { "%02d".format(it) }
) {
    val safeCount = count.coerceAtLeast(1)
    val init = selected.coerceIn(0, safeCount - 1)
    val itemPx = with(LocalDensity.current) { itemHeight.toPx() }
    val pad = itemHeight * (visibleCount / 2)
    val scope = rememberCoroutineScope()

    val state = rememberLazyListState(initialFirstVisibleItemIndex = init)

    // 音效发生器（页面销毁时释放）
    val tone = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_SYSTEM, 42) }.getOrNull()
    }
    DisposableEffect(Unit) {
        onDispose { runCatching { tone?.release() } }
    }

    // 首帧之后再启用吸附/音效，避免开窗时误把位置归零
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        ready = true
    }

    val centerPos by remember {
        derivedStateOf { state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset / itemPx }
    }

    val centerIndex by remember {
        derivedStateOf { centerPos.roundToInt().coerceIn(0, safeCount - 1) }
    }

    // 松手后吸附对齐：只在停稳时执行一次，并立即上报选中值
    LaunchedEffect(state, safeCount, ready) {
        if (!ready) return@LaunchedEffect
        snapshotFlow { state.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (!scrolling) {
                    val pos = state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset / itemPx
                    val target = pos.roundToInt().coerceIn(0, safeCount - 1)
                    if (abs(pos - target) > 0.001f) {
                        state.animateScrollToItem(target, 0)
                    }
                    onSelected(target)
                }
            }
    }

    // 实时上报中间数值 + 逐格音效（滚动过程中即生效，避免点"确定"时取值滞后）
    LaunchedEffect(state, safeCount, ready) {
        if (!ready) return@LaunchedEffect
        var last = init
        snapshotFlow { centerIndex }
            .distinctUntilChanged()
            .collect { idx ->
                if (idx != last) {
                    last = idx
                    if (playTick) runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 12) }
                }
                onSelected(idx)
            }
    }

    Box(
        modifier = modifier.height(itemHeight * visibleCount),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    RoundedCornerShape(12.dp)
                )
        )
        LazyColumn(
            state = state,
            contentPadding = PaddingValues(vertical = pad),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(safeCount) { i ->
                val d = abs(i - centerPos)
                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth()
                        .clickable {
                            onSelected(i)
                            scope.launch { state.animateScrollToItem(i, 0) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formatter(i),
                        fontSize = if (d < 0.5f) 24.sp else 18.sp,
                        fontWeight = if (d < 0.5f) FontWeight.Bold else FontWeight.Normal,
                        color = if (d < 0.5f) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                                .copy(alpha = (0.8f - d * 0.18f).coerceIn(0.15f, 0.8f))
                        }
                    )
                }
            }
        }
    }
}
