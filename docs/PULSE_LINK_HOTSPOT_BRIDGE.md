# Pulse Link 手机热点手柄桥接

目标链路：

```text
手机 [SillyTavern(termux) + Pulse Link app] -> 电脑 [PC bridge] -> XInput 手柄震动
```

这个路径不需要 USB ADB，也不需要 Android 无线调试。关键是让手机上的 Pulse Link app 以矩阵主控身份，把 `gamepad` 目标转发到电脑的 PC bridge。

## 网络拓扑

1. 手机开启热点。
2. 电脑连接手机热点。
3. 手柄通过 2.4G / 蓝牙连接电脑，并能被 Windows XInput 识别。
4. 手机上的 SillyTavern 插件 API 地址保持 `http://127.0.0.1:8080`，因为插件和 app 都在手机上。
5. 手机上的 Pulse Link app 会把手柄震动转发到电脑 IP 的 `8081` 端口。

## 电脑侧启动

在电脑运行：

```powershell
D:\BaiduNetdisk\AI\codex\test-3th\working\android-remote-control-mcp-pulse\scripts\start_pulselink_hotspot_bridge.cmd -NoTest
```

脚本会：

- 优先使用 PowerShell 7 的 `pwsh.exe`。
- 启动 `pulselink_pc_bridge.py`，监听 `0.0.0.0:8081`。
- 自动推断电脑在热点里的 IPv4 地址。
- 输出一条手机端需要打开或 `curl` 的 `/matrix/config` 地址。
- 如果手机访问失败，检查 Windows 防火墙是否允许 Python 或 TCP `8081` 在当前网络入站。

如果自动推断到的电脑 IP 不对，可以手动指定：

```powershell
D:\BaiduNetdisk\AI\codex\test-3th\working\android-remote-control-mcp-pulse\scripts\start_pulselink_hotspot_bridge.cmd -PcIp 192.168.43.123
```

## 手机侧注册

确保 Pulse Link app 服务已启动。然后在手机 Termux 或手机浏览器打开电脑脚本输出的地址，形如：

```text
http://127.0.0.1:8080/matrix/config?mode=MASTER&clear=true&node=<电脑热点IP>&port=8081&attenuation=1.0&label=PC-XInput-Bridge
```

注册后，手机侧插件触发 `target=gamepad` 或多选目标含 `gamepad` 时，app 会转发到电脑 bridge。

当前电脑侧自检示例：

```text
http://<电脑热点IP>:8081/status
```

如果返回 `controller_connected=true` 且 `controller_indices` 有编号，说明 PC bridge 和手柄已就绪。

## 手机侧测试

在手机 Termux 或手机浏览器打开：

```text
http://127.0.0.1:8080/vibrate?mode=mode_3&level=4&randomize=false&target=gamepad&targets=gamepad
```

如果手柄震动，说明链路已通。

## 当前实测记录

2026-07-08 当前会话已完成一次软件链路验证：

- PC bridge `/status` 返回 `controller_connected=true`、`controller_indices=[0]`、`xinput_dll=xinput1_4.dll`。
- App `/status` 返回 `ktor_server=running`、`host=0.0.0.0`、`local_ip=10.249.181.132`、`matrix_mode=MASTER`、`relay_nodes=1`、`current_default_level=3`。
- 已注册矩阵节点 `PC-XInput-Bridge` -> `10.249.181.225:8081`。
- 触发 `mode_3 level=4 target=gamepad targets=gamepad` 后，PC bridge `last_command` 更新为 `mode=mode_3`、`level=4`、`controller_index=0`。
- App logcat 记录矩阵扇出到 `10.249.181.225:8081` 并收到 200 返回。

这证明手机 App 能把 `gamepad` 震动请求转发到电脑 XInput bridge。最终人工验收仍需要用户确认手柄实际体感。

## 可选：电脑自动注册

如果电脑能直接访问手机 app 的热点网关地址，也可以传入 `-PhoneBaseUrl` 自动注册并测试：

```powershell
D:\BaiduNetdisk\AI\codex\test-3th\working\android-remote-control-mcp-pulse\scripts\start_pulselink_hotspot_bridge.cmd -PhoneBaseUrl http://192.168.43.1:8080
```

很多 Android 热点环境下，电脑未必能访问手机自身服务；这种情况不是阻塞，使用上面的“手机侧注册”即可。

## 停止

```powershell
D:\BaiduNetdisk\AI\codex\test-3th\working\android-remote-control-mcp-pulse\scripts\start_pulselink_hotspot_bridge.cmd -StopOnly
```

这会尽量调用 bridge `/stop` 并停止电脑侧 bridge 进程。手机 app 如需停止服务，可在 app 内点击停止服务。
