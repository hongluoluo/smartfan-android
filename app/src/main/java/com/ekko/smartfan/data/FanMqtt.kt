package com.ekko.smartfan.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.util.UUID

data class FanState(
    val connected: Boolean = false,   // 设备在线（fan32/status == online）
    val isOn: Boolean = false,
    val speed: Int = 0
)

data class LinkInfo(
    val brokerConnected: Boolean = false,
    val deviceOnline: Boolean = false,
    val topicReady: Boolean = false,
    val cmdTopic: String = "",
    val lastTx: String = "",
    val lastRx: String = "",
    val lastError: String = ""
)

/**
 * MQTT 通信层（v2.2）
 *
 * 关键设计变更：不再从实体自动主题里"猜"命令主题。
 *  - 命令：固定走 fan32/text/cmd/command，载荷 on | off | speed 60
 *  - 状态：设备把 fan/规则/倒计时打包成 retained JSON 发到 fan32/state，
 *          on_state 变化即发 + 每 20 秒兜底刷新，App 冷启动立刻拿到全量真实状态。
 */
object FanMqtt {
    const val PREFIX = "fan32"
    const val SLOT_COUNT = 6
    const val STATE_TOPIC = "$PREFIX/state"
    const val CMD_TOPIC = "$PREFIX/text/cmd/command"

    private const val LEGACY_FAN_FALLBACK = "$PREFIX/fan/fan1"

    private val _state = MutableStateFlow(FanState())
    val state: StateFlow<FanState> = _state.asStateFlow()

    private val _link = MutableStateFlow(LinkInfo())
    val link: StateFlow<LinkInfo> = _link.asStateFlow()

    private val _rules = MutableStateFlow<Map<String, String>>(emptyMap())
    val rules: StateFlow<Map<String, String>> = _rules.asStateFlow()

    private val _cdRemaining = MutableStateFlow(0L)
    val cdRemaining: StateFlow<Long> = _cdRemaining.asStateFlow()

    private val _cdAction = MutableStateFlow("OFF")
    val cdAction: StateFlow<String> = _cdAction.asStateFlow()

    private val _devTime = MutableStateFlow("--")
    val devTime: StateFlow<String> = _devTime.asStateFlow()

    @Volatile private var cmdTopic: String = CMD_TOPIC
    @Volatile private var legacyFanBase: String? = null
    @Volatile private var client: MqttAsyncClient? = null
    @Volatile private var connecting = false

    // 首次使用请在「设置」页填写 MQTT broker 信息（默认留空，避免把真实凭据写进仓库）
    var host: String = ""
    var port: Int = 1883
    var user: String = ""
    var pass: String = ""

    private val pending = ArrayDeque<Pair<String, String>>()
    private val lock = Any()

    fun loadConfig(ctx: Context) {
        val p = AppPrefs(ctx)
        host = p.host; port = p.port; user = p.user; pass = p.pass
        if (legacyFanBase == null && p.fanTopicBase.isNotBlank()) legacyFanBase = p.fanTopicBase
    }

    fun connect() {
        synchronized(lock) {
            if (connecting) return
            val c = client
            if (c != null) {
                if (c.isConnected) return
                // 已有实例但未连接：关闭后重建，避免它与自动重连并存产生重复订阅
                runCatching { c.close() }
                client = null
            }
            connecting = true
            try {
                val nc = MqttAsyncClient(
                    "tcp://$host:$port",
                    "smartfan-" + UUID.randomUUID().toString().take(8),
                    MemoryPersistence()
                )
                val opts = MqttConnectOptions().apply {
                    isAutomaticReconnect = true
                    isCleanSession = true
                    keepAliveInterval = 20
                    connectionTimeout = 6
                    userName = user
                    password = pass.toCharArray()
                }
                nc.setCallback(object : MqttCallbackExtended {
                    override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                        updateLink { it.copy(brokerConnected = true, lastError = "") }
                        subscribe(nc)
                        flushPending(nc)
                    }

                    override fun connectionLost(cause: Throwable?) {
                        updateLink { it.copy(brokerConnected = false, lastError = cause?.message ?: "连接断开") }
                        _state.value = _state.value.copy(connected = false)
                    }

                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        if (topic != null && message != null) handleMessage(topic, String(message.payload))
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                })
                client = nc
                nc.connect(opts, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        connecting = false
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        connecting = false
                        updateLink { it.copy(brokerConnected = false, lastError = exception?.message ?: "连接失败") }
                    }
                })
            } catch (e: Exception) {
                connecting = false
                updateLink { it.copy(brokerConnected = false, lastError = e.message ?: "连接异常") }
            }
        }
    }

    fun disconnect() {
        try {
            client?.disconnectForcibly(0, 500)
            client?.close()
        } catch (_: Exception) {
        }
        client = null
        updateLink { it.copy(brokerConnected = false) }
        _state.value = FanState()
    }

    private fun subscribe(c: MqttAsyncClient) {
        try {
            val topics = arrayOf(
                STATE_TOPIC,
                "$PREFIX/fan/#",
                "$PREFIX/text/+/state",
                "$PREFIX/number/+/state",
                "$PREFIX/sensor/+/state",
                "$PREFIX/text_sensor/+/state",
                "$PREFIX/status"
            )
            c.subscribe(topics, IntArray(topics.size) { 1 })
        } catch (e: Exception) {
            updateLink { it.copy(lastError = e.message ?: "订阅失败") }
        }
    }

    private fun handleMessage(topic: String, payload: String) {
        val v = payload.trim()
        updateLink { it.copy(lastRx = "$topic = ${v.take(120)}") }
        when {
            topic == STATE_TOPIC -> parseSnapshot(v)
            topic == "$PREFIX/status" -> {
                val online = v.equals("online", true)
                _state.value = _state.value.copy(connected = online)
                updateLink { it.copy(deviceOnline = online) }
            }
            topic.startsWith("$PREFIX/text/") && topic.endsWith("/state") -> {
                val key = topic.removePrefix("$PREFIX/text/").removeSuffix("/state")
                when {
                    key == "cd_action" -> _cdAction.value = v
                    key.startsWith("rule") -> {
                        val m = _rules.value.toMutableMap()
                        m[key] = v
                        _rules.value = m
                    }
                }
            }
            topic == "$PREFIX/text_sensor/dev_time/state" -> _devTime.value = v
            topic == "$PREFIX/sensor/cd_remaining/state" -> {
                _cdRemaining.value = v.toDoubleOrNull()?.toLong() ?: 0L
            }
            // 风扇状态只认 fan/ 前缀，绝不能让 number/text/sensor 的 /state 混进来
            topic.startsWith("$PREFIX/fan/") && topic.endsWith("/speed_level/state") -> {
                legacyFanBase = topic.removeSuffix("/speed_level/state")
                _state.value = _state.value.copy(speed = v.toIntOrNull() ?: return)
                updateLink { it.copy(topicReady = true) }
            }
            topic.startsWith("$PREFIX/fan/") && topic.endsWith("/state") -> {
                legacyFanBase = topic.removeSuffix("/state")
                _state.value = _state.value.copy(isOn = v.equals("ON", true))
                updateLink { it.copy(topicReady = true) }
            }
            // 其余 /state（number、button、cmd 等）与开关无关，忽略
            else -> Unit
        }
    }

    /** 解析设备 retained 快照：on / speed / rules[] / cd_action / cd_rem */
    private fun parseSnapshot(json: String) {
        try {
            val o = JSONObject(json)
            val on = o.optBoolean("on", false)
            val speed = o.optInt("speed", 0)
            _state.value = _state.value.copy(isOn = on, speed = speed)
            val arr = o.optJSONArray("rules")
            if (arr != null) {
                val m = HashMap<String, String>()
                for (i in 0 until arr.length()) m["rule${i + 1}"] = arr.optString(i, "0")
                _rules.value = m
            }
            o.optString("cd_action", "").takeIf { it.isNotEmpty() }?.let { _cdAction.value = it }
            if (o.has("cd_rem")) _cdRemaining.value = o.optLong("cd_rem", 0L)
            updateLink { it.copy(topicReady = true, cmdTopic = CMD_TOPIC, lastError = "") }
        } catch (e: Exception) {
            updateLink { it.copy(lastError = "快照解析失败: ${e.message}") }
        }
    }

    private inline fun updateLink(block: (LinkInfo) -> LinkInfo) {
        _link.value = block(_link.value)
    }

    private fun flushPending(c: MqttAsyncClient) {
        synchronized(lock) {
            while (pending.isNotEmpty()) {
                val (t, p) = pending.removeFirst()
                try {
                    c.publish(t, MqttMessage(p.toByteArray()).apply { qos = 1 })
                } catch (_: Exception) {
                }
            }
        }
    }

    fun publish(topic: String, payload: String): Boolean {
        val c = client
        if (c != null && c.isConnected) {
            return try {
                c.publish(topic, MqttMessage(payload.toByteArray()).apply { qos = 1 })
                updateLink { it.copy(lastTx = "$topic = $payload", lastError = "") }
                true
            } catch (e: Exception) {
                synchronized(lock) { pending.addLast(topic to payload) }
                updateLink { it.copy(lastError = e.message ?: "发送失败") }
                false
            }
        }
        synchronized(lock) { pending.addLast(topic to payload) }
        updateLink { it.copy(lastTx = "排队: $topic = $payload") }
        connect()
        return false
    }

    // ---------------- 风扇控制（固定命令通道） ----------------

    fun setPower(on: Boolean): Boolean = publish(CMD_TOPIC, if (on) "on" else "off")

    fun setSpeed(pct: Int): Boolean = publish(CMD_TOPIC, "speed ${pct.coerceIn(1, 100)}")

    /** 固件收到 speed 会自动置为开启 */
    fun turnOnWithSpeed(pct: Int): Boolean = setSpeed(pct)

    // ---------------- 设备端定时 ----------------

    fun setRule(slot: Int, raw: String): Boolean = publish("$PREFIX/text/rule$slot/command", raw)

    fun setCdAction(action: String): Boolean = publish("$PREFIX/text/cd_action/command", action)

    fun setCdMinutes(minutes: Int): Boolean =
        publish("$PREFIX/number/cd_minutes/command", minutes.coerceIn(1, 1440).toString())

    fun startCountdown(minutes: Int, action: String): Boolean {
        setCdAction(action)
        setCdMinutes(minutes)
        return publish("$PREFIX/button/cd_start/command", "PRESS")
    }

    fun cancelCountdown(): Boolean = publish("$PREFIX/button/cd_cancel/command", "PRESS")
}
