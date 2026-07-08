# Pulse Link app 与 SillyTavern 插件联合测试交接

> 2026-07-07 更新：本文件记录的是 2026-07-06 阶段交接。后续代码工作请优先阅读最新文档：
> `D:\BaiduNetdisk\AI\codex\终极版方案\工作交接\PulseLink_后续代码工作交接_2026-07-07.md`
> 以及测试报告：
> `D:\BaiduNetdisk\AI\codex\终极版方案\工作交接\PulseLink_联合测试报告_2026-07-07.md`。
> 关键变化：最小闭环已通过，插件脚本版本已更新为 `index.js?v=20260706-http-timeout`，下一阶段应从真实 AI A/B/C/D 回复流和未覆盖外设能力继续。

本文档用于在当前 Codex 对话中接手 Pulse Link 联合测试，也可作为后续新对话交接入口。目标是在当前 Windows 电脑上，用 Android Studio 调试 Android app，并联调 SillyTavern 扩展插件。

生成日期：2026-07-06

## 1. 接手结论

当前阶段不是重新安装环境，也不是重写功能，而是进入 app 与插件的联合测试。

本对话执行口径：

```text
测试均在本电脑完成。
链路：电脑上的 SillyTavern 页面 -> Pulse Link 拓展插件 -> Android Studio 里运行/调试的 app。
默认连接：ADB forward，插件 API 地址使用 http://127.0.0.1:8080。
只有用户明确切到同一局域网真机直连时，才改用 http://<phone-ip>:8080。
```

优先打开 Android Studio 调试 app：

```text
C:\Program Files\Android\Android Studio\bin\studio64.exe
```

目标 app 工程：

```text
D:\BaiduNetdisk\AI\codex\android-remote-control-mcp-pulse
```

插件源码：

```text
D:\BaiduNetdisk\AI\codex\sillytavern-pulse-link
```

SillyTavern 当前第三方扩展目录中已经存在插件副本：

```text
D:\BaiduNetdisk\AI\Tauritavern\sillytavern\sillytavern\public\scripts\extensions\third-party\sillytavern-pulse-link
```

安装 Android Studio 与配置 MCP 的另一个对话已经完成基础环境工作。后续联合测试不要再改 Android Studio 安装目录、中文语言包、MCP Server 插件、SDK 或系统 PATH，除非用户明确要求。

## 2. 当前环境事实

Android Studio：

```text
Android Studio Quail 1 | 2026.1.1 Patch 2
C:\Program Files\Android\Android Studio\bin\studio64.exe
```

SDK 路径：

```text
C:\Users\pang'hao'wen\AppData\Local\Android\Sdk
```

已确认过的关键 SDK 组件：

```text
platforms;android-36
build-tools;35.0.0
build-tools;36.0.0
platform-tools
cmdline-tools;latest
```

Android Studio MCP 状态：

```text
MCP Server 插件已加载：261.23567.174
Codex MCP server 配置名：android-studio
command: cmd
args: /c npx -y @jetbrains/mcp-proxy@latest
```

中文界面状态：

```text
Chinese (Simplified) Language Pack 已安装
selectedLocale = zh-CN
配置文件：
C:\Users\pang'hao'wen\AppData\Roaming\Google\AndroidStudio2026.1.1\options\ide.general.xml
```

注意：中文界面、MCP、SDK 配置属于另一个对话已经收口的环境内容。联合测试只使用它们，不接管配置。

## 3. 当前构建状态

app 已经生成 debug APK：

```text
D:\BaiduNetdisk\AI\codex\android-remote-control-mcp-pulse\app\build\outputs\apk\debug\app-debug.apk
```

2026-07-06 已通过的检查：

```powershell
.\gradlew.bat :app:compileDebugKotlin --console=plain --no-daemon
.\gradlew.bat :app:lintDebug --console=plain --no-daemon
.\gradlew.bat :app:assembleDebug --console=plain --no-daemon
```

已知 caveat：

```text
app/build.gradle.kts 当前 minSdk = 33。
原始 prompt 想兼容更低 API，但该仓库既有 MCP/Accessibility 能力存在 API 33 依赖。
Pulse Link 相关代码本身有 API 分支保护，但 app 整体 minSdk 暂不下调。
```

单元测试 caveat：

```text
HapticPatternLibraryTest 曾在本机 Windows/Gradle test executor 环境失败。
失败特征是 ClassNotFoundException 指向 Java 路径解析问题，不是断言失败。
接手时优先用 Android Studio 的 Gradle 面板或 Run Configuration 复测。
```

## 4. 功能实现范围

app 侧新增 Pulse Link 中间件：

```text
app/src/main/kotlin/com/danielealbano/androidremotecontrolmcp/services/pulselink/
```

核心文件：

```text
HapticMiddlewareService.kt      Ktor Netty 服务与 HTTP 路由
ProceduralHapticEngine.kt       手机震动、外部玩具、随机噪点、停止逻辑
ButtplugWebSocketClient.kt      Intiface/Buttplug WebSocket 客户端
GadgetbridgeHeartRateBridge.kt  Gadgetbridge 心率桥
BiometricCollector.kt           生物数据缓存与历史数据
LanMatrixManager.kt             局域网矩阵主从/扇出
MediaTransitionManager.kt       本地媒体跃迁
HapticPatternLibrary.kt         1..23 模式与 1..5 档位
PulseLogger.kt                  app 内日志与 Logcat
```

app UI：

```text
app/src/main/kotlin/com/danielealbano/androidremotecontrolmcp/ui/screens/PulseLinkScreen.kt
```

入口：

```text
底部导航新增 Pulse Link tab
```

插件侧当前模块：

```text
index.js
settings.html
style.css
modules/arousalMeter.js
modules/biometrics.js
modules/devTools.js
modules/hapticDispatcher.js
modules/heartMonitor.js
modules/hud.js
modules/hudUI.js
modules/lanMatrix.js
modules/lifecycleManager.js
modules/logger.js
modules/mediaTransition.js
modules/promptInjector.js
modules/rhythmMode.js
modules/state.js
```

插件 manifest：

```text
display_name: Pulse Link - 全感官交互网关
version: 2.0.0
js: index.js?v=20260706-prompt-guide
css: style.css
```

插件目录本身不是 git 仓库，修改前要注意不要假设可以在该目录里直接看 git diff。

## 5. app HTTP API 契约

服务绑定：

```text
host = 0.0.0.0
port = 8080
```

路由清单：

| 路由 | 调用方 | 必测点 |
|---|---|---|
| `GET /status` | 插件、浏览器、PowerShell | 返回 `success=true`、`ktor_server=running`、本机 IP、matrix/media 状态 |
| `GET /vibrate` | 插件、app 手动测试 | 支持 `mode`、`level`、`randomize`、`target` |
| `GET /stop` | 插件、HUD、app UI | 停止手机震动、发送玩具归零、释放媒体 Audio Focus |
| `GET /biometrics` | 插件心率模块 | 返回当前或历史生物数据；手环不可用时不能崩溃 |
| `GET /playMedia` | 插件媒体跃迁 | 唤起已选择的本地音频/视频并跳转到配置时间点 |

`/vibrate` 参数要求：

| 参数 | 要求 |
|---|---|
| `mode` | 必填；支持 `mode_1`、`mode1`、`模式1` 到 `模式23` |
| `level` | 可选；1..5；传入时优先于 app 默认强度 |
| `randomize` | 联合测试中插件必须显式传入；`true` 启用 +/-15% timing 抖动，`false` 使用原始 timing |
| `target` | 可选；`phone`、`toy`、`all`；默认目标应按插件设置显式发送 |

重要验收点：

```text
前端插件必须在每条触觉请求中显式发送 randomize。
App 端当前对缺省 randomize 的兜底是 true，但这不应该成为插件省参的理由。
```

## 6. Android Studio 调试方式

本对话优先用 Android Studio 完成 app 调试：

1. 打开 `D:\BaiduNetdisk\AI\codex\android-remote-control-mcp-pulse`。
2. 等 Gradle sync 完成。
3. 选择真实 Android 设备或可用模拟器。
4. 用 `app` debug 配置运行。
5. 在 Logcat 中过滤：

```text
PulseLink
Pulse Link
Intiface
Gadgetbridge
Haptic
Ktor
Buttplug
```

推荐断点位置：

```text
HapticMiddlewareService.configureRoutes()
ProceduralHapticEngine.trigger()
ProceduralHapticEngine.emergencyStop()
ButtplugWebSocketClient.sendWaveform()
GadgetbridgeHeartRateBridge.onReceive()
LanMatrixManager.masterFanOut()
MediaTransitionManager.play()
```

调试原则：

```text
能用 Android Studio 的 Logcat、Debugger、Gradle 面板完成的 app 问题，优先在 Android Studio 内处理。
命令行用于补充冒烟测试和复现，不替代 Studio 调试。
```

## 7. 设备与网络准备

需要让 SillyTavern 所在电脑能访问 Android Studio 里运行/调试的 app HTTP 服务。本对话默认走 ADB forward，把设备侧 `8080` 转发到电脑本机 `8080`。

如果后续改用同一 Wi-Fi 真机直连，app 的 Pulse Link tab 会显示：

```text
Ktor: running at <phone-ip>:8080
```

插件默认 API base URL 是本对话优先使用的：

```text
http://127.0.0.1:8080
```

如果明确改用手机局域网 IP 直连，插件设置里必须改成：

```text
http://<phone-ip>:8080
```

如果使用 Android 模拟器并在同一机器上测试，仍优先验证 ADB forward。只有不用 forward 时，才按实际网络拓扑改地址。

## 8. API 冒烟命令

本对话先用 `http://127.0.0.1:8080`。若后续明确切换到局域网真机直连，再把 `<base>` 替换成实际地址，例如 `http://192.168.1.23:8080`。

状态：

```powershell
Invoke-RestMethod "http://127.0.0.1:8080/status"
```

手机震动，强度 3，无随机噪点：

```powershell
Invoke-RestMethod "http://127.0.0.1:8080/vibrate?mode=mode_1&level=3&randomize=false&target=phone"
```

手机震动，强度 5，有随机噪点：

```powershell
Invoke-RestMethod "http://127.0.0.1:8080/vibrate?mode=%E6%A8%A1%E5%BC%8F1&level=5&randomize=true&target=phone"
```

停止：

```powershell
Invoke-RestMethod "http://127.0.0.1:8080/stop"
```

心率：

```powershell
Invoke-RestMethod "http://127.0.0.1:8080/biometrics"
Invoke-RestMethod "http://127.0.0.1:8080/biometrics?duration=60"
```

媒体跃迁：

```powershell
Invoke-RestMethod "http://127.0.0.1:8080/playMedia"
```

## 9. 联合测试清单

### A. Android Studio 与 app 基线

- [ ] Android Studio 打开 app 工程后 Gradle sync 成功。
- [ ] `:app:compileDebugKotlin` 成功。
- [ ] `:app:lintDebug` 成功。
- [ ] `:app:assembleDebug` 成功。
- [ ] app 能安装到目标设备。
- [ ] app 启动后底部导航存在 `Pulse Link` tab。
- [ ] Pulse Link tab 中 Start service 能启动前台服务。
- [ ] 通知栏出现 Pulse Link foreground service 通知。
- [ ] Pulse Link tab 显示 `Ktor: running at <ip>:8080`。
- [ ] Android Studio Logcat 能看到 Pulse Link service created/running 日志。

### B. HTTP API 基线

- [ ] `/status` 返回 `success=true`。
- [ ] `/status` 返回 `ktor_server=running`。
- [ ] `/status` 返回 `host=0.0.0.0`、`port=8080`。
- [ ] `/status` 返回 `local_ip`，且电脑能访问该 IP。
- [ ] `/vibrate` 缺少 `mode` 时返回 `success=false` 和错误信息。
- [ ] `/stop` 多次调用都不崩溃，返回 `success=true`。
- [ ] `/biometrics` 在没有手环时也返回结构化结果或可解释状态，不导致服务崩溃。
- [ ] `/playMedia` 在未选择媒体时返回失败原因，不导致服务崩溃。

### C. 触觉模式与强度

- [ ] `mode_1`、`mode1`、`模式1` 三种别名都能触发同一模式。
- [ ] `mode_23` 可触发。
- [ ] 未知模式返回或记录 `Unknown haptic mode`，服务继续可用。
- [ ] `level=1` 与 `level=5` 体感强度/节奏差异可辨。
- [ ] `level` 超界时 app clamp 到 1..5。
- [ ] 不传 `level` 时使用 app 默认强度。
- [ ] `target=phone` 只触发手机震动。
- [ ] `target=toy` 不触发手机震动，外部玩具不可用时不崩溃。
- [ ] `target=all` 同时走手机与 toy 分支。
- [ ] `mode_7` 连续震动能持续，直到 `/stop` 或 app UI Stop haptics。
- [ ] 连续快速发送 5 次 `/vibrate` 不造成重叠失控，上一条任务被取消或接管。

### D. randomize 契约

- [ ] 插件发出的每条 `/vibrate` 都显式包含 `randomize=true|false`。
- [ ] 插件设置关闭微观噪点后，请求为 `randomize=false`。
- [ ] 插件设置开启微观噪点后，请求为 `randomize=true`。
- [ ] app 手动测试中 Micro noise 开关能影响实际传入值。
- [ ] Android Studio 断点或日志确认 `randomize=false` 时不调用微观随机化。
- [ ] Android Studio 断点或日志确认 `randomize=true` 时走 +/-15% timing 抖动。

### E. Buttplug / Intiface

- [ ] Intiface Central 在测试设备/环境上启动。
- [ ] app 连接 `ws://127.0.0.1:12345`，Logcat 出现 `Intiface WebSocket connected` 或断线重试日志。
- [ ] 连接后发送 `RequestServerInfo`，`MessageVersion=3`。
- [ ] 连接后发送 `StartScanning`。
- [ ] `/status` 中 `buttplug_connected=true`。
- [ ] `target=toy` 时发送 `ScalarCmd`。
- [ ] timing 偶奇索引规则正确：奇数段强度 0.8，偶数段归零。
- [ ] `/stop` 后发送 `ScalarCmd` 强度 0.0。
- [ ] Intiface 未启动时 app 不崩溃，并按指数退避重连。

### F. Gadgetbridge / 生物数据

- [ ] 已安装 Gadgetbridge，手环已连接。
- [ ] app 有 `BODY_SENSORS` 等必要权限。
- [ ] Gadgetbridge 广播探针注册成功，或记录不可用原因。
- [ ] 手环有实时心率时 `/biometrics` 返回 bpm。
- [ ] `/biometrics?duration=60` 返回历史数据结构。
- [ ] Gadgetbridge 不可用时插件显示连接/生物数据 warning，但不阻塞触觉功能。
- [ ] 导出的 Gadgetbridge DB 路径可用时，SQLite fallback 能读 Xiaomi/Huawei 心率表。

### G. app Pulse Link UI

- [ ] Start service / Stop service 状态切换正确。
- [ ] Stop haptics 可立即停止手机和 toy。
- [ ] Manual haptic test 的 mode、level、target、Micro noise 都能实际影响触发。
- [ ] LAN matrix console 可以切换 Slave/Master。
- [ ] Master 模式下可添加、删除、启用、禁用节点。
- [ ] 节点 attenuation slider 会保存并影响扇出强度。
- [ ] Media transition 可选择本地音频/视频。
- [ ] Start position (ms) 保存后 Test jump 能按时间点唤起播放器。
- [ ] Runtime logs 中能看到核心操作与错误。

### H. LAN Matrix

- [ ] 主机模式下 `/status` 返回 `matrix_mode=MASTER`。
- [ ] 从机模式下 `/status` 返回 `matrix_mode=SLAVE`。
- [ ] 主机添加至少 1 个从机节点。
- [ ] ST/插件只请求主机一次 `/vibrate`。
- [ ] 主机本机触发，同时向节点转发。
- [ ] attenuation=100% 时从机 level 等于基准 level。
- [ ] attenuation=50% 时从机 level 按比例降低。
- [ ] attenuation=0% 时该节点静音或跳过。
- [ ] 单个节点超时不影响其他节点与主机本机触发。

### I. 媒体跃迁

- [ ] app UI 选择媒体后 `/status` 返回 `media_configured=true`。
- [ ] `/playMedia` 可唤起本地播放器。
- [ ] 播放器从配置的毫秒位置开始。
- [ ] `/stop` 后释放 Audio Focus。
- [ ] 插件开启媒体跃迁后，触发动作会调用 `/playMedia`。
- [ ] 插件关闭媒体跃迁后，不调用 `/playMedia`。

### J. 插件加载与设置页

- [ ] SillyTavern 扩展管理中出现 `Pulse Link - 全感官交互网关`。
- [ ] 浏览器控制台出现 `Pulse Link v2.0.0 已加载`。
- [ ] 设置页加载 `settings.html`。
- [ ] 设置页 CSS 生效，无明显布局错乱。
- [ ] `启用 Pulse Link` 开关可持久化。
- [ ] API 地址可保存为默认 `http://127.0.0.1:8080`；若明确切局域网真机直连，也可保存为 `http://<phone-ip>:8080`。
- [ ] 点击连接检查能访问 `/status`。
- [ ] 开发者调试面板可显示请求/错误日志。
- [ ] HUD 可出现、展开、切换全局/当前聊天指标。
- [ ] HUD 停止按钮发送 `/stop`。

### K. 插件触发模式

- [ ] A 流式同步：流式 token 中出现 `[haptic: 模式, 强度]` 时即时触发。
- [ ] B 点击/渲染后触发：消息渲染完成后解析标签并触发。
- [ ] C 剧场/折叠触发：折叠内容展开或指定交互时触发。
- [ ] D 精准时序：`[wait: 毫秒]` 控制节奏，`.pulse-sync-chunk` 渐显与触觉同步。
- [ ] `parseHapticTags` 清理最终正文，不把控制标签暴露给用户。
- [ ] 强度策略 fixed 会覆盖 AI 输出 level。
- [ ] 强度策略 range 会 clamp 到 min/max。
- [ ] 强度策略 free 允许 1..5。
- [ ] target 设置 `phone/toy/all` 会被写入每条请求。
- [ ] 切换聊天、关闭对话或插件禁用时发送 `/stop`。
- [ ] 浏览器切后台或切换 app 时不自动停止震动，除非触发生命周期 kill switch。

### L. 心率监控与生理滑块

- [ ] 心率监控开启后按设置间隔轮询 `/biometrics`。
- [ ] 自动模式下 bpm 超过 threshold 后触发自动接管逻辑。
- [ ] 手动确认模式下出现确认 UI，用户选择后再触发。
- [ ] cooldown 生效，避免短时间反复触发。
- [ ] long gap 后能采集 post/pre reading state。
- [ ] 生理状态滑块注入到消息气泡。
- [ ] 旧滑块被锁定，新滑块保持可编辑。
- [ ] 发送用户消息前，插件把滑块值注入 Author Note 或等价上下文。

### M. 全链路验收

- [ ] app 在 Android Studio 目标设备上启动 Pulse Link service。
- [ ] 插件 API 地址默认指向 `http://127.0.0.1:8080`（ADB forward）；局域网直连时才指向手机 IP。
- [ ] 插件连接检查通过。
- [ ] 发送一条带 `[haptic: mode_1, 3]` 的 AI 回复，手机发生震动。
- [ ] 插件请求中能看到 `mode`、`level`、`randomize`、`target`。
- [ ] Android Studio Logcat 能看到 app 收到并执行请求。
- [ ] HUD 指标累计触觉次数。
- [ ] `/stop` 从插件、HUD、app UI 三个入口都能停止。
- [ ] 断开 app 服务后插件给出清晰 warning，不刷屏、不阻断 SillyTavern 普通聊天。

## 10. 常见故障定位

| 现象 | 优先检查 |
|---|---|
| 插件无法连接 app | 本对话先查 ADB forward 是否存在、`127.0.0.1:8080/status` 是否通；若改用手机 IP，再查手机和电脑是否同网段、Windows 防火墙/手机网络权限 |
| `/status` 无响应 | app Pulse Link service 是否启动；前台服务通知是否存在；Logcat 是否有 Ktor 启动错误 |
| 手机不震动 | 设备是否支持震动；app 是否有 `VIBRATE`；`target` 是否误设为 `toy` |
| toy 不动 | Intiface 是否启动；`/status` 的 `buttplug_connected`；Logcat 中 WebSocket 连接/断线日志 |
| 心率为空 | Gadgetbridge 是否在线；手环是否在采样；权限；导出 DB 路径 |
| 插件请求没有随机参数 | 检查 `modules/hapticDispatcher.js` 与设置页 `randomizeNoise` |
| 标签暴露在聊天正文 | 检查 `parseHapticTags`、`transformRhythmMarkup` 与对应触发模式 |
| 切聊天后仍震动 | 检查 `lifecycleManager.js`、`CHAT_CHANGED` 事件和 `/stop` 是否发出 |
| 媒体不能跳转 | app 是否已选媒体；URI 权限是否持久化；播放器是否支持 seek extras |
| Android Studio MCP 不可见 | 新开 Codex 对话或重启 Codex；不要重装 Android Studio |

## 11. 本对话执行方式

本对话已经接手，下一步直接按 A、B、J、M 四组清单跑最小闭环。若后续需要开新 Codex 对话，可以把下面这段发过去：

```text
请接手 Pulse Link 联合测试。交接文档在：
D:\BaiduNetdisk\AI\codex\android-remote-control-mcp-pulse\docs\PULSE_LINK_JOINT_TEST_HANDOFF.md

当前任务不是安装 Android Studio，也不是重写功能，而是在这台 Windows 电脑上用 Android Studio 调试：
D:\BaiduNetdisk\AI\codex\android-remote-control-mcp-pulse

插件源码在：
D:\BaiduNetdisk\AI\codex\sillytavern-pulse-link

SillyTavern 第三方扩展目录已有插件副本：
D:\BaiduNetdisk\AI\Tauritavern\sillytavern\sillytavern\public\scripts\extensions\third-party\sillytavern-pulse-link

Android Studio 已安装在：
C:\Program Files\Android\Android Studio\bin\studio64.exe

请使用 Android Studio 的 Gradle、Logcat、Debugger 进行 app 调试。不要修改 Android Studio 中文语言包、MCP Server 插件、SDK 或 PATH，除非我明确要求。

测试均在本电脑完成。链路是电脑上的 SillyTavern 页面 -> Pulse Link 拓展插件 -> Android Studio 里运行/调试的 app。默认使用 ADB forward，插件 API 地址先填 http://127.0.0.1:8080。

先按文档中的 A、B、J、M 四组清单跑最小闭环，再扩展到 Intiface、Gadgetbridge、LAN Matrix 和媒体跃迁。
```

## 12. 验收优先级

最小闭环必须先过：

```text
Android Studio 打开 app -> app 运行 -> Pulse Link service 启动 -> /status 可访问 -> 插件连接检查通过 -> [haptic] 标签触发手机震动 -> /stop 停止
```

然后再测外设链路：

```text
Intiface / Buttplug
Gadgetbridge / heart rate
LAN Matrix
Media Transition
HUD metrics
Arousal meter
```

最后做压力与异常：

```text
快速连续触发
服务重启
设备断网
Intiface 未启动
Gadgetbridge 不在线
切换聊天
禁用插件
```

## 13. 2026-07-07 PC 2.4G 手柄桥接验证

本轮新增并验证了 `SillyTavern plugin -> Android App -> PC bridge -> 2.4G XInput gamepad` 路径。结论：App 仍作为震动中枢；PC 侧通过 ADB reverse 暴露一个 XInput bridge 接收 App 的震动参数，再驱动连接在本机的 2.4G 手柄。

关键文件：

```text
D:\BaiduNetdisk\AI\codex\android-remote-control-mcp-pulse\scripts\pulselink_pc_bridge.py
D:\BaiduNetdisk\AI\codex\android-remote-control-mcp-pulse\app\src\main\kotlin\com\danielealbano\androidremotecontrolmcp\services\pulselink\PulseHttpServer.kt
D:\BaiduNetdisk\AI\codex\android-remote-control-mcp-pulse\app\src\main\kotlin\com\danielealbano\androidremotecontrolmcp\services\pulselink\HapticMiddlewareService.kt
D:\BaiduNetdisk\AI\codex\android-remote-control-mcp-pulse\app\src\main\kotlin\com\danielealbano\androidremotecontrolmcp\services\pulselink\LanMatrixManager.kt
```

验证命令：

```powershell
$adb="C:\Users\pang'hao'wen\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$serial="10AC7N18PB000XE"
$pkg="com.danielealbano.androidremotecontrolmcp.debug"

python "D:\BaiduNetdisk\AI\codex\android-remote-control-mcp-pulse\scripts\pulselink_pc_bridge.py" --host 127.0.0.1 --port 8081
& $adb -s $serial forward tcp:8080 tcp:8080
& $adb -s $serial reverse tcp:8081 tcp:8081
& $adb -s $serial shell am start -n "$pkg/com.danielealbano.androidremotecontrolmcp.services.mcp.AdbServiceTrampolineActivity" --es action pulse_start --ez enable_intiface false --ez enable_gadgetbridge false

curl.exe --max-time 8 http://127.0.0.1:8080/status
curl.exe --max-time 8 "http://127.0.0.1:8080/matrix/config?mode=MASTER&clear=true&node=adb-reverse&port=8081&attenuation=1.0&label=PC-XInput-Bridge"
curl.exe --max-time 8 "http://127.0.0.1:8080/vibrate?mode=mode_3&level=4&randomize=false&target=gamepad"
curl.exe --max-time 8 http://127.0.0.1:8080/stop
curl.exe --max-time 8 http://127.0.0.1:8081/status
```

已确认结果：

- 用户实际感受到 2.4G 手柄震动。
- App log 出现 `Matrix fanout adb-reverse:8081 target=gamepad level=4` 和 `Matrix node adb-reverse responded 200`。
- `/stop` quick-return 已验证，App log 出现 `Matrix stop node adb-reverse responded 200` 与 `Emergency stop executed`。
- PC bridge `/status` 可看到 `xinput1_4.dll`、`controller_indices:[0]` 或记录的 `last_command`。

本轮还做了两个稳定性改动：

- `HapticMiddlewareService` 增加 `PARTIAL_WAKE_LOCK`，Manifest 增加 `android.permission.WAKE_LOCK`，用于 Pulse Link 前台服务防休眠。
- Pulse Link HTTP 层改为轻量 `PulseHttpServer`，避免 Android 真机上 Ktor Netty idle 后端口仍监听但不响应的问题；`/status` 中 `ktor_server` 字段暂时保留为 `"running"`，只为兼容现有插件读取逻辑。

已知注意事项：

- vivo 安装 debug APK 时可能出现风险确认页。可直接执行：`adb shell input tap 720 2800` 勾选“已了解应用的风险检测结果”，等待约 0.9 秒后 `adb shell input tap 720 2996` 点击“继续安装”。
- vivo/OriginOS 后台调度仍可能让 HTTP listener 在长时间空闲后出现延迟；实测即时 `status -> matrix/config -> vibrate -> stop` 路径可用。测试前建议保持手机解锁、执行 `svc power stayon usb`，必要时把 app 拉到前台一次。
- 插件目录仍可能被另一个优化流程实时更改，本轮没有改动插件热加载目录。

## 14. 2026-07-07 本对话收尾状态

完整工作报告：

```text
D:\BaiduNetdisk\AI\codex\终极版方案\工作交接\PulseLink_本对话完整工作报告_2026-07-07.md
```

源码收尾备份：

```text
D:\BaiduNetdisk\AI\codex\backups\android-remote-control-mcp-pulse_source_snapshot_20260707_094721.zip
D:\BaiduNetdisk\AI\codex\backups\android-remote-control-mcp-pulse_source_snapshot_20260707_094721.sha256.txt
ZipSha256=60E72AEA8F8EF921406D73806CC87BF6894AE87EA3090F541F56954A3686D399
SourceFiles=584
BackupFiles=584
```

本对话后续新增/确认：

- App 已做简体中文资源覆盖，`values-zh-rCN/strings.xml` 与默认英文资源同为 257 个 key，缺失 0。
- MCP 工具设置页已做场景化汉化，保留 `toolName`/协议 ID，显示文案采用“点按坐标”“查找界面元素”“等待界面稳定”“回复通知”等动作语义。
- 最新汉化 APK 已安装到 vivo 真机，`lastUpdateTime=2026-07-07 09:14:30`。
- 真机 UI 抽查通过：服务页、设置页、MCP 工具页底部分类均显示中文。
- `:app:assembleDebug` 通过，crash buffer 未发现当前包相关崩溃。

PC bridge 已简化为一步启动：

```powershell
D:\BaiduNetdisk\AI\codex\android-remote-control-mcp-pulse\scripts\start_pulselink_pc_bridge.cmd
```

默认流程会自动：

- 找到 adb 和已连接真机。
- 启动或复用 `pulselink_pc_bridge.py`。
- 配置 `adb forward tcp:8080 tcp:8080`。
- 配置 `adb reverse tcp:8081 tcp:8081`。
- 执行 `svc power stayon usb`。
- 通过 trampoline 启动 Pulse Link service。
- 写入 `PC-XInput-Bridge` 矩阵节点。
- 发送一次 `mode_3 level=4 target=gamepad` 测试震动。

常用变体：

```powershell
.\scripts\start_pulselink_pc_bridge.cmd -NoTest
.\scripts\start_pulselink_pc_bridge.cmd -StopOnly
.\scripts\start_pulselink_pc_bridge.cmd -KeepIntiface -KeepGadgetbridge
```

当前验证结果：

- PC bridge `/status` 显示 `xinput1_4.dll`、`controller_connected=true`、`controller_indices=[0]`。
- App `/status` 显示 `matrix_mode=MASTER`、`relay_nodes=1`。
- 一键脚本默认测试请求返回 `success=true`。

下一轮不要再手动输入多条 bridge 命令，优先使用一键脚本。若要发布前回归，还需重新跑 A/B/C/D 四类真实 AI 回复流；本收尾阶段没有完整重跑插件四模式。
