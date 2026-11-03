package com.ekko.smartfan.data

import java.util.UUID

/**
 * 定时规则（与设备端槽位一一对应）
 * days: 周一=1 … 周日=7
 * action: ON / OFF / SPEED
 * slot: 设备槽位 1..6
 */
data class TimerRule(
    val id: String = UUID.randomUUID().toString(),
    val hour: Int = 8,
    val minute: Int = 0,
    val days: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val action: String = "ON",
    val speed: Int = 60,
    val enabled: Boolean = true,
    val slot: Int = 0
) {
    val actionLabel: String
        get() = when (action) {
            "ON" -> "开启"
            "OFF" -> "关闭"
            else -> "设为 $speed%"
        }

    val timeText: String
        get() = "%02d:%02d".format(hour, minute)

    val daysText: String
        get() {
            if (days.isEmpty()) return "未选星期"
            if (days.size == 7) return "每天"
            if (days == setOf(1, 2, 3, 4, 5)) return "工作日"
            if (days == setOf(6, 7)) return "周末"
            return (1..7).filter { it in days }.joinToString(" ") { DAY_NAMES[it - 1] }
        }

    companion object {
        val DAY_NAMES = listOf("一", "二", "三", "四", "五", "六", "日")
    }
}
