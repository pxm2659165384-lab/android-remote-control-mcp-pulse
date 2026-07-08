# Pulse Link App 最终说明

整理日期：2026-07-08

当前源码：`D:\BaiduNetdisk\AI\codex\test-3th\working\android-remote-control-mcp-pulse`

关联插件：`D:\BaiduNetdisk\AI\codex\test-3th\working\sillytavern-pulse-link`

## 项目定位

Pulse Link App 是基于 `android-remote-control-mcp` 魔改的 Android 端触觉中间件。原项目仍保留 MCP Server、Accessibility、截图、文件、App 管理、OAuth、隧道等能力；本项目新增一套独立的 Pulse Link 前台服务和 HTTP 网关，让 SillyTavern 插件可以通过简单 HTTP 请求驱动手机震动、PC XInput 手柄、Intiface/Buttplug 玩具、Gadgetbridge 心率和局域网矩阵节点。

系统分工如下：

```text
SillyTavern + Pulse Link 插件
  -> 解析 AI 回复中的 [haptic] / [wait] / [NEXT]
  -> 请求 Pulse Link App HTTP API

Pulse Link App
  -> 本机手机震动
  -> Intiface/Buttplug 玩具
  -> Android 可见手柄震动
  -> LAN Matrix 转发到 PC bridge
  -> Gadgetbridge 心率读取
  -> 媒体跳转

PC bridge
  -> Windows XInput 手柄震动
```

## 最新功能状态

本说明基于 2026-07-07 至 2026-07-08 的三轮开发、测试和完善记录合并而成。

已完成：

- Pulse Link foreground service：常驻、wake lock、keep-alive overlay、HTTP watchdog。
- HTTP API：`/status`、`/vibrate`、`/stop`、`/biometrics`、`/matrix/config`、`/gamepads/refresh`、`/playMedia`。
- 震动模式：`mode_1` 到 `mode_23`，强度 1-5，支持 `randomize`。
- 目标设备：`phone`、`gamepad`、`toy`、`egg`、`fleshlight`、`all`，并支持 `targets=phone,gamepad`。
- 停止优先级：`/stop` 会停止本机、远端矩阵节点、手柄、玩具和媒体音频焦点。
- PC XInput bridge：Windows 电脑监听 `8081`，手机 App 可作为 MASTER 转发 `gamepad` 请求。
- LAN Matrix：手机热点场景下 App 注册电脑节点并转发请求。
- Gadgetbridge 心率桥接和 `/biometrics` 查询。
- Intiface/Buttplug WebSocket 连接循环。
- 媒体跳转 Activity 和 `/playMedia`。
- Pulse Link 日志中文化和 App 状态响应字段。

最后一轮软件验证：

- `.\gradlew.bat :app:compileDebugKotlin --console=plain --no-daemon` 通过。
- `python -m py_compile scripts\pulselink_pc_bridge.py` 通过。
- `:app:testDebugUnitTest` 未跑通，原因是本机 Java 测试子进程环境把 `C:\Program Files\Java\jdk-17.0.1\bin;C:\Program` 拆成错误主类；这是本机 `CLASSPath`/Java 路径环境问题，不是业务断言失败。

仍需真人或真实设备最终验收：

- 长时间真实 SillyTavern 对话中的 A/B/C/D 模式体感。
- 手机、手柄、玩具多设备组合的体感一致性。
- Gadgetbridge 心率、Arousal Meter 和媒体跳转在真实角色卡中的长会话效果。

## HTTP API

默认服务地址：

```text
http://127.0.0.1:8080
```

App 服务监听 `0.0.0.0:8080`，但插件和 App 同在手机时建议让插件配置为 `127.0.0.1`。

### `/status`

查询运行状态：

```text
GET /status
```

典型字段：

- `success`
- `ktor_server`
- `host`
- `port`
- `local_ip`
- `buttplug_connected`
- `gadgetbridge_ok`
- `current_default_level`
- `matrix_mode`
- `relay_nodes`
- `media_configured`
- `gamepad_connected`
- `gamepad_vibrator_available`
- `gamepads`

### `/vibrate`

触发震动：

```text
GET /vibrate?mode=mode_3&level=4&randomize=false&target=phone&targets=phone
```

参数：

| 参数 | 说明 |
| --- | --- |
| `mode` | 必填，`mode_1` 到 `mode_23` |
| `level` | 可选，1-5；缺省时使用 App 当前默认强度 |
| `randomize` | 可选，`true/false`；缺省为 `true` |
| `target` | 旧兼容字段，单目标或 `all` |
| `targets` | 新字段，多目标 CSV，如 `phone,gamepad` |

设备目标：

| 目标 | 行为 |
| --- | --- |
| `phone` / `local` | 本机手机震动 |
| `gamepad` / `controller` | Android 手柄震动或 LAN Matrix 远端手柄 |
| `toy` / `egg` / `fleshlight` | Intiface/Buttplug |
| `all` | 手机、玩具、手柄 |

### `/stop`

急停：

```text
GET /stop
```

MASTER 模式下会先停止远端矩阵节点，再停止本机。插件 HUD 急停、聊天切换、生成停止都会走这条链路。

### `/biometrics`

读取心率：

```text
GET /biometrics
GET /biometrics?duration=300
```

`duration > 0` 时返回历史窗口数据，否则返回当前数据。

### `/matrix/config`

配置 LAN Matrix：

```text
GET /matrix/config?mode=MASTER&clear=true&node=10.249.181.225&port=8081&attenuation=1.0&label=PC-XInput-Bridge
```

常用参数：

| 参数 | 说明 |
| --- | --- |
| `mode` | `MASTER` 或其他 MatrixMode |
| `clear` | `true` 时清空旧节点 |
| `node` / `ip` | 节点 IP |
| `port` | 节点端口，PC bridge 默认 8081 |
| `attenuation` | 强度衰减 |
| `label` | 节点标签 |

### `/gamepads/refresh`

让 App 重新扫描 Android 可见手柄能力：

```text
GET /gamepads/refresh
```

注意：很多 2.4G 手柄在 Android 侧不暴露 vibrator，此时建议使用 PC XInput bridge。

### `/playMedia`

打开 App 中配置的媒体：

```text
GET /playMedia
```

## PC XInput 手柄桥接

手机热点链路：

```text
手机 [SillyTavern + 插件 + Pulse Link App]
  -> 电脑 [PC bridge 8081]
  -> XInput 手柄
```

电脑侧启动：

```powershell
scripts\start_pulselink_hotspot_bridge.cmd -NoTest
```

手机侧注册电脑节点：

```text
http://127.0.0.1:8080/matrix/config?mode=MASTER&clear=true&node=<电脑热点IP>&port=8081&attenuation=1.0&label=PC-XInput-Bridge
```

手机侧测试：

```text
http://127.0.0.1:8080/vibrate?mode=mode_3&level=4&randomize=false&target=gamepad&targets=gamepad
```

2026-07-08 软件链路记录：

- PC bridge `/status` 返回 `controller_connected=true`，`controller_indices=[0]`。
- App `/status` 返回 `matrix_mode=MASTER`，`relay_nodes=1`。
- `mode_3 level=4 target=gamepad` 后 PC bridge `last_command` 更新成功。
- App logcat 记录矩阵扇出到电脑 bridge 并收到 200。

## 与 SillyTavern 插件的接口契约

插件是唯一负责解析 AI 输出和决定触发时机的一侧。App 不理解 SillyTavern 对话上下文，只执行 HTTP 命令。

关键约定：

- 插件必须传 `mode`。
- 插件应传 `level`，App 只在缺省时使用默认强度。
- 插件应传 `randomize`。
- 插件可同时传 `target` 与 `targets`，App 优先使用 `targets`。
- `/stop` 是最高优先级命令，不与浏览器可见性或 Android 后台生命周期绑定。

## 构建与调试

基础编译：

```powershell
.\gradlew.bat :app:compileDebugKotlin --console=plain --no-daemon
```

Debug APK：

```powershell
.\gradlew.bat :app:assembleDebug --console=plain --no-daemon
```

PC bridge 语法检查：

```powershell
python -m py_compile scripts\pulselink_pc_bridge.py
```

单元测试：

```powershell
.\gradlew.bat :app:testDebugUnitTest --console=plain --no-daemon
```

如果 Windows 上出现 `ClassNotFoundException: Files\Java\jdk-17.0.1\bin;C:\Program`，先清理本机 `CLASSPATH/CLASSPath` 或换用无空格 JDK 路径后重跑。

## 仓库注意事项

本项目含上游 Android MCP 能力和 Pulse Link 魔改代码。首次克隆到新机器后，注意：

- `.gitignore` 会忽略 `.gradle/`、`.idea/`、`app/build/`、`local.properties`、`.dbip-cache/`、日志和 APK。
- `vendor/ngrok-java/*/target/*.jar` 是本地生成产物，不纳入 git；如需运行完整上游 ngrok 相关测试，需要按上游 Makefile 重新生成 vendor JAR。
- `local.properties` 不应提交。
- PC bridge 日志 `scripts\pulselink_pc_bridge.*.log` 不应提交。

## 相关文档

- `docs/PULSE_LINK_HOTSPOT_BRIDGE.md`：手机热点下 PC XInput bridge 说明。
- `docs/PULSE_LINK_JOINT_TEST_HANDOFF.md`：第二阶段联合测试交接。
- `docs/ARCHITECTURE.md`：上游 Android MCP 架构。
- `docs/MCP_TOOLS.md`：上游 MCP 工具列表。
