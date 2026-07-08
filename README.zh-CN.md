# Android Remote Control MCP - Pulse Link 版

[English README](README.md)

本仓库是 `android-remote-control-mcp` 的 Pulse Link 版本。它保留原项目的 Android MCP Server、Accessibility 控制、截图、文件、App 管理、OAuth、远程隧道等能力，并新增 Pulse Link 触觉中间件服务，让 SillyTavern 插件可以通过 HTTP 接口驱动手机震动、XInput 手柄、Intiface/Buttplug 玩具、Gadgetbridge 心率和局域网矩阵节点。

当前源码快照：2026-07-08 fix60/fix61 工作目录版本。

完整项目说明见 [docs/PULSE_LINK_FINAL_GUIDE.md](docs/PULSE_LINK_FINAL_GUIDE.md)。手机热点下 PC 手柄桥接说明见 [docs/PULSE_LINK_HOTSPOT_BRIDGE.md](docs/PULSE_LINK_HOTSPOT_BRIDGE.md)。

## 项目定位

Pulse Link 的整体链路是：

```text
SillyTavern + Pulse Link 插件
  -> 解析 AI 回复里的 [haptic] / [wait] / [NEXT]
  -> 请求 Android App HTTP API

Pulse Link Android App
  -> 本机手机震动
  -> Android 可见手柄震动
  -> Intiface/Buttplug 玩具
  -> LAN Matrix 转发到 PC bridge
  -> Gadgetbridge 心率读取
  -> 媒体跳转

Windows PC bridge
  -> XInput 手柄震动
```

插件负责理解 SillyTavern 对话和触发时机，App 只负责执行外部硬件命令。

## 主要功能

- Pulse Link 前台服务：常驻、wake lock、keep-alive overlay、HTTP watchdog。
- HTTP API：`/status`、`/vibrate`、`/stop`、`/biometrics`、`/matrix/config`、`/gamepads/refresh`、`/playMedia`。
- 震动模式：`mode_1` 到 `mode_23`，强度 `1-5`，支持 `randomize`。
- 多目标设备：`phone`、`gamepad`、`toy`、`egg`、`fleshlight`、`all`，并支持 `targets=phone,gamepad`。
- 急停：`/stop` 会停止本机、远端矩阵节点、手柄、玩具和媒体音频焦点。
- PC XInput bridge：电脑监听 `8081`，手机 App 可作为 MASTER 转发手柄请求。
- LAN Matrix：手机热点场景下注册电脑节点并扇出请求。
- Gadgetbridge 心率桥接和 `/biometrics` 查询。
- Intiface/Buttplug WebSocket 连接循环。
- 媒体跳转 Activity 和 `/playMedia`。

原 Android MCP 能力也保留：56 个 MCP 工具、`/mcp` Streamable HTTP、OAuth 2.1、自签或自定义 HTTPS、Cloudflare/ngrok 隧道、ADB 无界面配置、文件/相机/通知/位置等工具。

## 安装与构建

克隆源码时建议带上 submodule：

```bash
git clone --recurse-submodules https://github.com/pxm2659165384-lab/android-remote-control-mcp-pulse.git
cd android-remote-control-mcp-pulse
```

如果已经普通克隆，先初始化构建隧道所需的 submodule：

```bash
git submodule update --init vendor/cloudflared vendor/ngrok-java
```

构建 Debug APK：

```powershell
.\gradlew.bat :app:assembleDebug --console=plain --no-daemon
```

只做 Kotlin 编译检查：

```powershell
.\gradlew.bat :app:compileDebugKotlin --console=plain --no-daemon
```

安装到连接的设备：

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 基础使用

1. 打开 App。
2. 在 Settings > Permissions 中启用 Accessibility Service。
3. 按需授权通知、相机、麦克风、位置等权限。
4. 回到 Server 页启动服务。
5. 在 SillyTavern 的 Pulse Link 插件中把 App API 地址设为 `http://127.0.0.1:8080`。

如果 SillyTavern 和 App 都在同一台 Android 设备上运行，`127.0.0.1` 指的是手机本机，通常不用改成局域网 IP。

## Pulse Link HTTP API

默认地址：

```text
http://127.0.0.1:8080
```

### 查询状态

```text
GET /status
```

常见字段包括：

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

### 触发震动

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

目标：

| 目标 | 行为 |
| --- | --- |
| `phone` / `local` | 本机手机震动 |
| `gamepad` / `controller` | Android 手柄震动或 LAN Matrix 远端手柄 |
| `toy` / `egg` / `fleshlight` | Intiface/Buttplug |
| `all` | 手机、玩具、手柄 |

### 急停

```text
GET /stop
```

`/stop` 是最高优先级命令。MASTER 模式下会先停止远端矩阵节点，再停止本机。

### 生物反馈

```text
GET /biometrics
GET /biometrics?duration=300
```

`duration > 0` 时返回历史窗口数据，否则返回当前数据。

### LAN Matrix 配置

```text
GET /matrix/config?mode=MASTER&clear=true&node=<电脑热点IP>&port=8081&attenuation=1.0&label=PC-XInput-Bridge
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

### 其他接口

```text
GET /gamepads/refresh
GET /playMedia
```

`/gamepads/refresh` 用于刷新 Android 侧可见手柄能力。很多 2.4G 手柄在 Android 侧不暴露 vibrator，此时建议走 PC XInput bridge。

`/playMedia` 会打开 App 侧配置的媒体。

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

## 与 SillyTavern 插件的契约

- 插件必须传 `mode`。
- 插件应传 `level`，App 只在缺省时使用默认强度。
- 插件应传 `randomize`。
- 插件可同时传 `target` 与 `targets`，App 优先使用 `targets`。
- `/stop` 不与浏览器可见性或 Android 后台生命周期绑定。

## 调试与验证

PC bridge 语法检查：

```powershell
python -m py_compile scripts\pulselink_pc_bridge.py
```

Kotlin 编译检查：

```powershell
.\gradlew.bat :app:compileDebugKotlin --console=plain --no-daemon
```

单元测试：

```powershell
.\gradlew.bat :app:testDebugUnitTest --console=plain --no-daemon
```

当前最后一轮本地验证结果：

- `python -m py_compile scripts\pulselink_pc_bridge.py` 通过。
- `.\gradlew.bat :app:compileDebugKotlin --console=plain --no-daemon` 通过。
- `:app:testDebugUnitTest` 仍受本机 Java 环境影响：`CLASSPath/CLASSPATH` 中包含 `C:\Program Files\Java\jdk-17.0.1...`，Gradle Test Executor 把 `Files\Java\jdk-17.0.1\bin;C:\Program` 当成主类并报 `ClassNotFoundException`。这是本机 Java 路径/环境变量问题，不是业务断言失败。

## 仓库注意事项

- `.gitignore` 会忽略 `.gradle/`、`.idea/`、`app/build/`、`local.properties`、`.dbip-cache/`、日志和 APK。
- `vendor/cloudflared` 和 `vendor/ngrok-java` 是 submodule 指针。
- `vendor/ngrok-java/*/target/*.jar` 是本地生成产物，不纳入 git。
- PC bridge 日志 `scripts\pulselink_pc_bridge.*.log` 不应提交。

## 相关仓库

- App 仓库：[android-remote-control-mcp-pulse](https://github.com/pxm2659165384-lab/android-remote-control-mcp-pulse)
- SillyTavern 插件仓库：[sillytavern-pulse-link](https://github.com/pxm2659165384-lab/sillytavern-pulse-link)

## 许可证

本项目沿用 MIT License，详见 [LICENSE.md](LICENSE.md)。
