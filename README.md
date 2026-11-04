# 智能风扇 · SmartFan（Android）

针对自建 ESP32 风扇调压控制器的原生安卓控制端。**Jetpack Compose + Kotlin**，通过 **MQTT** 直连设备，实现开关、无级调速、星期定时与倒计时。

配套固件：[esp32-fan-controller](https://github.com/hongluoluo/esp32-fan-controller)

> 本 App 不是通用 MQTT 面板，而是与该固件**约定的主题协议**配套的专用控制端（协议见下文「MQTT 协议」）。
>
> 📦 直接下载安装包：[Releases](https://github.com/hongluoluo/smartfan-android/releases)

---

## 目录

- [主要功能](#主要功能)
- [界面与交互](#界面与交互)
- [技术栈](#技术栈)
- [快速开始](#快速开始)
- [MQTT 协议](#mqtt-协议)
- [代码结构](#代码结构)
- [定时/倒计时的实现边界](#定时倒计时的实现边界)
- [版本历史](#版本历史)
- [常见问题](#常见问题)
- [许可证](#许可证)

---

## 主要功能

### 1. 控制页
- 一键开关（乐观更新，2.5 秒无设备回执自动回滚并提示）
- 0~100% 无级调速滑杆（设备侧再映射为 PWM 占空比 40%~80%）
- 快捷预设：低 30% / 中 60% / 高 80% / 全速 100%
- 风扇图标按转速旋转、环形进度随转速变化，关闭时静止并降低饱和度
- 顶部实时显示链路状态：「未连接服务器 / 正在同步设备… / 设备离线 / 运行中 / 已关闭」

### 2. 定时页
- 规则存放在**设备端 6 个槽位**，App 只负责读写（换手机、重装 App 都不丢规则）
- 每条规则：时间（**惯性滚轮选择，带逐格音效**）+ 重复星期（多选 / 每天 / 工作日 / 周末）+ 动作（开启 / 关闭 / 设定转速）+ 启用开关
- 卡片列表显示槽位号、时间、星期、动作；开关即时写设备，删除即清空槽位
- 手机不在线、App 未运行，设备照常按时执行

### 3. 倒计时页
- 时长为 30 / 60 / 120 分钟或自定义（时 + 分双滚轮），到点动作可设为开或关
- 环形渐变进度 + 呼吸光晕；「开始/取消」按钮就在圆环内
- 剩余时间读**设备端**倒计时（3 秒刷新，本地按秒插值避免跳格）
- 手机关机、断网都不影响设备到点执行

### 4. 设置页
- MQTT broker 地址 / 端口 / 用户名 / 密码，保存即重连
- **诊断区**：服务器连接、设备在线、命令主题、最近发送 / 最近接收 / 错误信息，便于排障
- 设备时间显示（判断设备是否完成 NTP 授时）

---

## 界面与交互

- 主色淡紫 `#7c5cbf`，浅色 / 深色双主题（跟随系统）
- 卡片式布局、Material3 组件、自适应启动图标
- 动画细节：
  - 风扇叶片按转速实时旋转（帧驱动，不是固定动画）
  - 环形进度用 sweep 渐变
  - 滚轮吸附对齐、逐格音效（`ToneGenerator`，不受系统触摸音开关影响）
  - 开关状态同步带 400ms 防抖，避免瞬时异常消息造成抖动

---

## 技术栈

| 项 | 版本 |
|---|---|
| 语言 | Kotlin 1.9.24 |
| UI | Jetpack Compose（BOM 2024.06.00）+ Material3 |
| 构建 | Gradle 8.10.2 + AGP 8.7.3，compileSdk/targetSdk 34，minSdk 26 |
| MQTT | Eclipse Paho `org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5` |
| 架构 | 单 Activity + Compose Navigation + `StateFlow` 单例通信层 + SharedPreferences |

---

## 快速开始

### 前置条件

- 设备已刷入配套固件，且固件里配置了 MQTT（`mqtt.topic_prefix: fan32`）
- 一个可用的 MQTT broker（Mosquitto / EMQX 均可），App 与设备在同一网络或 broker 可达

### 构建

```bash
# JDK 17 必需
export JAVA_HOME=/path/to/jdk-17

echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew assembleRelease       # 或使用本机 gradle： gradle assembleRelease
```

产物：`app/build/outputs/apk/release/app-release-unsigned.apk`，用你自己的 keystore 签名：

```bash
zipalign -p 4 app-release-unsigned.apk aligned.apk
apksigner sign --ks mykey.jks --ks-key-alias mykey --out SmartFan.apk aligned.apk
```

### 首次使用

1. 安装并打开 App
2. 右上角齿轮 → 填 broker 地址 / 端口 / 用户名 / 密码 → 保存
3. 回到控制页，等状态变为「运行中 / 已关闭」即可操作
4. 若显示「正在同步设备…」超过几秒，看设置页诊断区的「最近接收」是否有 `fan32/state`

---

## MQTT 协议

主题前缀固定 `fan32`，全部采用 **QoS 1**。

### 命令（App → 设备）

| 主题 | 载荷 | 说明 |
|---|---|---|
| `fan32/text/cmd/command` | `on` | 开 |
| | `off` | 关 |
| | `speed 60` | 设 60% 并自动开启 |

固定 ASCII 主题，**不依赖实体名净化**，因此不会出现"主题猜错、命令无人接收"。

### 状态（设备 → App，retained）

```
topic : fan32/state
payload:
{"on":true,"speed":60,"rules":["08:00|*|OFF|60","0","0","0","0","0"],"cd_action":"OFF","cd_rem":0}
```

| 字段 | 用途 |
|---|---|
| `on` / `speed` | 控制页开关与转速 |
| `rules` | 定时页 6 个槽位（`"0"` = 空槽） |
| `cd_action` / `cd_rem` | 倒计时动作与剩余秒 |

### 定时与倒计时写入

| 主题 | 载荷 |
|---|---|
| `fan32/text/rule1..6/command` | `HH:MM\|DAYS\|ACTION\|SPEED`（`0` 清空） |
| `fan32/text/cd_action/command` | `ON` / `OFF` |
| `fan32/number/cd_minutes/command` | `1`~`1440` |
| `fan32/button/cd_start/command` | `PRESS` |
| `fan32/button/cd_cancel/command` | `PRESS` |
| `fan32/sensor/cd_remaining/state` | 剩余秒（3 秒刷新） |

规则字符串字段：

| 字段 | 取值 |
|---|---|
| `HH:MM` | 24 小时制 |
| `DAYS` | `12345`（1=周一…7=周日）/ `*` 每天 / `0` 停用 |
| `ACTION` | `ON` / `OFF` / `SPEED` |
| `SPEED` | 1~100（仅 `SPEED`） |

---

## 代码结构

```
app/src/main/java/com/ekko/smartfan/
├── MainActivity.kt              单 Activity，底部导航（控制 / 定时 / 倒计时）+ 设置入口
├── data/
│   ├── FanMqtt.kt               MQTT 单例：连接、订阅、快照解析、命令下发、链路诊断
│   ├── AppPrefs.kt              SharedPreferences：broker 配置、最近转速、倒计时缓存
│   ├── TimerRule.kt             规则数据模型 + 展示文案
│   └── RuleCodec.kt             规则字符串 HH:MM|DAYS|ACTION|SPEED 与模型互转
└── ui/
    ├── HomeScreen.kt            控制页：风扇动画、环形进度、滑杆、预设、乐观开关
    ├── TimerScreen.kt           定时页：设备槽位列表 + 编辑弹窗（滚轮选时）
    ├── CountdownScreen.kt       倒计时页：设备端倒计时 + 自定义时长
    ├── SettingsScreen.kt        设置与诊断
    ├── WheelPicker.kt           通用惯性滚轮（吸附 + 音效 + 点击选中）
    ├── Common.kt                状态胶囊等小组件
    └── theme/                   颜色、排版、主题（浅色/深色）
```

关键设计：

- **单例通信层** `FanMqtt` 用 `StateFlow` 暴露 `state / rules / cdRemaining / link`，UI 只做订阅
- **乐观更新**：UI 先翻转，收到设备回显后对齐；超时回滚并提示
- **主题门控**：未收到 `fan32/state` 前不允许发命令，避免发到不存在的主题
- **单实例连接**：断开时先关闭旧客户端再重建，避免自动重连与新连接并存导致重复订阅

---

## 定时/倒计时的实现边界

这两个功能**完全在设备端执行**，App 只是配置界面：

| 场景 | 是否执行 |
|---|---|
| App 从最近任务划掉 / 后台被杀 | ✅ 设备照常执行 |
| 手机锁屏、关机、没电 | ✅ 执行（设备独立运行） |
| 手机与设备不在同一网络 | ✅ 执行（设备自己按时间触发） |
| **设备断电重启** | 规则保留；重启后需 3~10 秒完成 NTP 授时，期间不触发 |
| **设备断网** | 到点会**错过**（不做补执行，避免误触） |
| 设备未授时（`dev_time` 显示 `--unsynced--`） | ❌ 定时与倒计时都不执行 |

---

## 版本历史

| 版本 | 主要变更 |
|---|---|
| v1.0.0 | 首版：控制页 + 定时 + 倒计时（定时/倒计时在手机侧 AlarmManager 执行） |
| v1.1.0 | 控制页去掉圆环内百分比；定时弹窗改滚轮选时；选中态配色加深；倒计时自定义时长 |
| v1.2.0 | 滚轮逐格音效；倒计时时长选项改流式布局并去掉 15 分钟 |
| v1.3.0 | 「开始倒计时」按钮移入圆环内；自定义时长单独一行 |
| v1.4.0 | 修复滚轮吸附失效（初始定位被归零、取值滞后），改为松手必吸附 + 滚动中实时上报 + 点击选中 |
| v2.0.0 | 定时/倒计时**下沉到固件**，App 只做配置；移除本地 AlarmManager 与相关广播接收器 |
| v2.1.0 | 修复命令主题靠猜导致的"开关打不开"；改 QoS 1、乐观更新、命令在途防抖、连接状态真实化、诊断区 |
| v2.2.0 | 固件新增固定命令通道 `fan32/text/cmd/command` 与 retained 快照 `fan32/state`；App 改为读快照，冷启动即可显示真实状态 |
| v2.3.0 | 修复控制页间歇性开关闪烁（非风扇 `/state` 主题被误判）；连接单实例化；UI 状态 400ms 防抖 |
| v2.3.1 | 移除源码中硬编码的 broker 地址与账号密码（公开仓库前脱敏），行为不变 |
| v2.4.0 | 回前台强制重建 MQTT 连接（修息屏后"点了没反应"）；keepalive 降到 15s；设置页新增设备 IP / 运行时长 / 复位原因 |
| v2.4.1 | 修正状态主题匹配：ESPHome 的 text_sensor 实际发布在 `fan32/sensor/<name>/state`，此前设备时间与复位原因收不到 |

---

## 常见问题

**1. 打开 App 显示「已关闭」，但风扇在转**

早期版本依赖订阅实体状态，而 ESPHome 的实体状态消息**不是 retained**，冷启动收不到。v2.2.0 起改为订阅 retained 快照 `fan32/state`，请确认固件也是同一版本协议。

**2. 点了开关没反应，出现红字「设备未确认」**

App 未收到 `fan32/state`（主题未就绪）或命令没发出。到设置页看诊断区：
- 「最近接收」为空 → 检查 broker 地址/账号、固件 `topic_prefix` 是否 `fan32`
- 有 `fan32/status` 但无 `fan32/state` → 固件版本过旧，缺少快照发布

**3. 开关状态来回闪**

v2.3.0 已修复：解析时严格限定 `fan/` 前缀，`number/text/sensor` 等实体的 `/state` 不再参与开关判定。

**4. HA 里状态长时间不更新，但风扇在转**

这是**设备 ↔ HA 的 ESPHome API（6053）断了**，与 App 的 MQTT 通道无关。常见成因与固件侧对策：
- DHCP 换 IP，HA 集成仍指向旧地址 → 固件已改**静态 IP**
- 固件 `api.reboot_timeout` 默认 15 分钟无客户端就重启 → 已设 `0s`
- WiFi 省电导致长连接假死 → 已设 `power_save_mode: none`
排查顺序：设置页看「设备 IP」是否与路由器上一致 → 手机浏览器访问 `http://<设备IP>:3232`（OTA 端口）能通但 HA 仍不更新，说明是 API 侧问题，重启设备或等固件 OTA 修复。

**5. 定时不生效**

先看设置页「设备时间」：`--unsynced--` 表示设备未完成 NTP 授时（固件里 NTP 服务器要用域名，`223.5.5.5` 只是 DNS 不提供 NTP）。

---

## 与固件项目的关系

| | [esp32-fan-controller](https://github.com/hongluoluo/esp32-fan-controller)（固件） | 本项目（App） |
|---|---|---|
| 运行位置 | ESP32-WROOM-32E | 安卓手机 |
| 职责 | **真正干活**：18kHz PWM 输出、执行星期定时与倒计时、维护并广播状态 | **只做配置与展示**：界面、规则编辑、状态呈现 |
| 依赖关系 | 不依赖 App，也可只用 Home Assistant 控制 | 依赖固件提供的 MQTT 主题协议 |
| 通信路径 | `设备 ⇄ MQTT broker`（另有 HA 原生 API 通道） | `App ⇄ MQTT broker` |

一句话：**App 是遥控器，固件是执行器。** 定时与倒计时写在设备里，所以手机关机、App 未运行都不影响到点执行；App 只负责把配置写进设备、把设备状态显示出来。

两端共享的协议契约（任意一侧改动都要同步）：

| 用途 | 主题 | 载荷 |
|---|---|---|
| 命令 | `fan32/text/cmd/command` | `on` / `off` / `speed N` |
| 状态快照 | `fan32/state`（retained） | `{"on":..,"speed":..,"rules":[..6..],"cd_action":..,"cd_rem":..}` |
| 定时槽 | `fan32/text/ruleN/command` | `HH:MM\|DAYS\|ACTION\|SPEED` |
| 倒计时 | `fan32/text/cd_action/command`、`fan32/number/cd_minutes/command`、`fan32/button/cd_start(或 cd_cancel)/command` | `ON/OFF`、`1~1440`、`PRESS` |

---

## 许可证

MIT，见 [LICENSE](LICENSE)。
