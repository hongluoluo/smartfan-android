package com.ekko.smartfan.data

/**
 * 设备端规则字符串编解码。
 * 格式: HH:MM|DAYS|ACTION|SPEED
 *   DAYS  : 1-7 数字组合（1=周一…7=周日），* = 每天，0 = 停用（永不匹配）
 *   ACTION: ON / OFF / SPEED
 *   SPEED : 1-100（SPEED 动作使用）
 * 设备侧固件按 | 解析，这里保持完全一致的语义。
 */
object RuleCodec {

    fun encodeMinutes(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

    fun encode(rule: TimerRule): String {
        val days = when {
            !rule.enabled -> "0"
            rule.days.size == 7 -> "*"
            rule.days.isEmpty() -> "0"
            else -> rule.days.sorted().joinToString("")
        }
        val action = when (rule.action) {
            "OFF" -> "OFF"
            "SPEED" -> "SPEED"
            else -> "ON"
        }
        val speed = rule.speed.coerceIn(1, 100)
        return "${encodeMinutes(rule.hour, rule.minute)}|$days|$action|$speed"
    }

    /** 解析设备状态字符串；返回 null 表示空槽或非法内容 */
    fun decode(slot: Int, raw: String?): TimerRule? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty() || s == "0") return null
        val parts = s.split("|")
        if (parts.size < 3) return null
        val hm = parts[0]
        if (hm.length < 5 || hm[2] != ':') return null
        val hour = hm.substring(0, 2).toIntOrNull() ?: return null
        val minute = hm.substring(3, 5).toIntOrNull() ?: return null
        val daysField = parts[1]
        val enabled = daysField != "0"
        val days = when {
            daysField == "*" -> (1..7).toSet()
            daysField == "0" -> emptySet()
            else -> daysField.mapNotNull { c -> c.digitToIntOrNull() }.filter { it in 1..7 }.toSet()
        }
        val action = when (parts[2].uppercase()) {
            "OFF" -> "OFF"
            "SPEED" -> "SPEED"
            else -> "ON"
        }
        val speed = parts.getOrNull(3)?.toIntOrNull() ?: 60
        return TimerRule(
            id = "slot$slot",
            hour = hour,
            minute = minute,
            days = days,
            action = action,
            speed = speed.coerceIn(1, 100),
            enabled = enabled,
            slot = slot
        )
    }
}
