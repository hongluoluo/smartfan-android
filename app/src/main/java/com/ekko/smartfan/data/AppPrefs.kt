package com.ekko.smartfan.data

import android.content.Context

class AppPrefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("smartfan", Context.MODE_PRIVATE)

    var host: String
        get() = sp.getString("host", "") ?: ""
        set(v) = sp.edit().putString("host", v).apply()

    var port: Int
        get() = sp.getInt("port", 1883)
        set(v) = sp.edit().putInt("port", v).apply()

    var user: String
        get() = sp.getString("user", "") ?: ""
        set(v) = sp.edit().putString("user", v).apply()

    var pass: String
        get() = sp.getString("pass", "") ?: ""
        set(v) = sp.edit().putString("pass", v).apply()

    var lastSpeed: Int
        get() = sp.getInt("last_speed", 60)
        set(v) = sp.edit().putInt("last_speed", v).apply()

    var countdownEnd: Long
        get() = sp.getLong("countdown_end", 0L)
        set(v) = sp.edit().putLong("countdown_end", v).apply()

    var countdownAction: String
        get() = sp.getString("countdown_action", "OFF") ?: "OFF"
        set(v) = sp.edit().putString("countdown_action", v).apply()

    var countdownSpeed: Int
        get() = sp.getInt("countdown_speed", 60)
        set(v) = sp.edit().putInt("countdown_speed", v).apply()

    var fanTopicBase: String
        get() = sp.getString("fan_topic_base", "") ?: ""
        set(v) = sp.edit().putString("fan_topic_base", v).apply()

    var countdownTotal: Long
        get() = sp.getLong("countdown_total", 0L)
        set(v) = sp.edit().putLong("countdown_total", v).apply()
}
