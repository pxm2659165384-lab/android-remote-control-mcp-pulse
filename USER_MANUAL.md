# Android 触觉中台用户手册

## 欢迎使用触觉中台系统

本手册将帮助您快速上手使用触觉中台功能，实现手机震动与外部智能玩具的同步控制。

---

## 目录

1. [系统要求](#系统要求)
2. [快速开始](#快速开始)
3. [功能说明](#功能说明)
4. [API使用指南](#api使用指南)
5. [Gadgetbridge心率采集](#gadgetbridge心率采集)
6. [Intiface玩具控制](#intiface玩具控制)
7. [常见问题](#常见问题)
8. [故障排查](#故障排查)

---

## 系统要求

### 硬件要求
- ✅ Android 设备（API 26+，推荐 API 33+）
- ✅ 手机支持震动功能
- ⭕ 智能手环（可选，用于心率采集）
- ⭕ 智能玩具（可选，支持 Buttplug.io 协议）

### 软件要求
- ✅ 本应用已安装
- ⭕ Gadgetbridge（可选，用于心率采集）
- ⭕ Intiface Central（可选，用于玩具控制）

### 权限要求
- ✅ 震动权限（VIBRATE）
- ⭕ 存储权限（读取 Gadgetbridge 数据库）
- ⭕ 传感器权限（BODY_SENSORS）

---

## 快速开始

### 第一步：安装应用

```bash
# 通过 ADB 安装
adb install app-debug.apk

# 或直接在设备上安装 APK 文件
```

### 第二步：启动应用

1. 在应用列表中找到"Android Remote Control MCP"
2. 点击打开应用
3. 触觉中台服务将自动启动

### 第三步：测试基础功能

打开终端或命令提示符（需要 ADB）：

```bash
# 测试服务状态
adb shell curl http://127.0.0.1:8080/status

# 测试手机震动
adb shell curl 'http://127.0.0.1:8080/vibrate?mode=模式1&target=phone'
```

如果手机震动，说明安装成功！🎉

---

## 功能说明

### 核心功能

#### 1. 震动模式（23种）

系统预设了23种不同的震动模式，每种模式都有独特的节奏和感觉：

| 模式编号 | 中文名称 | 英文名称 | 特点 |
|---------|---------|---------|------|
| 1 | 模式1 | mode_1 | 长短交替节奏 |
| 2 | 模式2 | mode_2 | 快速脉冲 |
| 3 | 模式3 | mode_3 | 短促连续 |
| ... | ... | ... | ... |
| 7 | 模式7 | mode_7 | 持续震动 |
| ... | ... | ... | ... |
| 23 | 模式23 | mode_23 | 特殊模式 |

#### 2. 强度档位（5级）

每种模式都有5个强度档位：

- **档位1**：最轻柔
- **档位2**：轻柔
- **档位3**：中等（默认）
- **档位4**：强烈
- **档位5**：最强烈

#### 3. 目标设备

可以控制震动发送到哪个设备：

- **phone**：仅手机震动
- **toy**：仅外部玩具震动
- **all**：手机和玩具同时震动（默认）

#### 4. 微随机算法

系统会对每次震动注入±15%的随机变化，避免感官适应。这意味着：
- 连续触发同一模式，每次感觉略有不同
- 保持新鲜感，不会产生麻木感
- 更加自然和真实

---

## API使用指南

### API 基础信息

- **基础URL**: `http://127.0.0.1:8080`
- **协议**: HTTP/1.1
- **数据格式**: JSON
- **字符编码**: UTF-8

### 端点详解

#### 1. 触发震动

**请求：**
```http
GET /vibrate?mode=模式1&target=all
```

**参数：**
- `mode` (必需): 模式名称
  - 中文：`模式1` 到 `模式23`
  - 英文：`mode_1` 到 `mode_23`
  - 自定义名称也可以（如果未找到会返回失败）
  
- `target` (可选): 目标设备，默认 `all`
  - `phone`: 仅手机
  - `toy`: 仅玩具
  - `all`: 全部设备

**响应示例：**
```json
{
  "success": true,
  "mode": "模式1",
  "target": "all",
  "level": 3
}
```

**使用示例：**
```bash
# 手机震动（模式1）
curl 'http://127.0.0.1:8080/vibrate?mode=模式1&target=phone'

# 玩具震动（模式5）
curl 'http://127.0.0.1:8080/vibrate?mode=mode_5&target=toy'

# 同时震动（模式10）
curl 'http://127.0.0.1:8080/vibrate?mode=模式10&target=all'
```

#### 2. 获取心率数据

**请求：**
```http
GET /biometrics                    # 获取当前值
GET /biometrics?duration=120       # 获取近120秒统计
```

**参数：**
- `duration` (可选): 统计时长（秒）
  - 不提供：返回最新单次读数
  - 提供：返回指定时间内的统计数据

**响应示例（当前值）：**
```json
{
  "success": true,
  "current": 78,
  "avg": 78,
  "max": 78,
  "stddev": 0.0,
  "sampleCount": 1
}
```

**响应示例（历史统计）：**
```json
{
  "success": true,
  "current": 82,
  "avg": 75,
  "max": 88,
  "stddev": 5.2,
  "sampleCount": 45
}
```

**使用示例：**
```bash
# 获取当前心率
curl http://127.0.0.1:8080/biometrics

# 获取近5分钟统计
curl 'http://127.0.0.1:8080/biometrics?duration=300'
```

#### 3. 系统状态检查

**请求：**
```http
GET /status
```

**响应示例：**
```json
{
  "success": true,
  "ktor_server": "running",
  "buttplug_connected": true,
  "gadgetbridge_ok": true,
  "current_level": 3,
  "android_api": 33,
  "supported_modes": 46
}
```

**字段说明：**
- `ktor_server`: 服务器状态
- `buttplug_connected`: Intiface连接状态
- `gadgetbridge_ok`: Gadgetbridge数据可用性
- `current_level`: 当前强度档位
- `android_api`: Android API版本
- `supported_modes`: 支持的模式数量

#### 4. 紧急停止

**请求：**
```http
GET /stop
```

**响应示例：**
```json
{
  "success": true,
  "message": "All haptics stopped"
}
```

**使用场景：**
- 震动太强需要立即停止
- 测试时需要中断当前震动
- 异常情况下的安全控制

#### 5. 设置强度档位

**请求：**
```http
GET /set_level?level=4
```

**参数：**
- `level` (必需): 强度档位，范围 1-5

**响应示例：**
```json
{
  "success": true,
  "level": 4
}
```

**使用示例：**
```bash
# 设置为最低强度
curl 'http://127.0.0.1:8080/set_level?level=1'

# 设置为最高强度
curl 'http://127.0.0.1:8080/set_level?level=5'
```

---

## Gadgetbridge心率采集

### 前提条件

1. ✅ 已安装 Gadgetbridge 应用
2. ✅ 手环已与 Gadgetbridge 配对连接
3. ✅ 手环正常同步数据到 Gadgetbridge

### 配置步骤

#### 步骤1：导出Gadgetbridge数据库

**方法A：使用Gadgetbridge内置功能**

1. 打开 Gadgetbridge 应用
2. 点击左上角菜单（☰）
3. 选择"设置"→"数据库管理"
4. 点击"导出数据库"
5. 选择导出路径：`/sdcard/Download/手环/`
6. 确认文件名：`Gadgetbridge.db`

**方法B：使用文件管理器（需要Root）**

1. 使用支持Root的文件管理器（如MT管理器）
2. 导航到：`/data/data/nodomain.freeyourgadget.gadgetbridge/databases/`
3. 复制 `Gadgetbridge.db` 到 `/sdcard/Download/手环/`

**方法C：使用ADB**

```bash
# 从手机复制到电脑
adb pull /data/data/nodomain.freeyourgadget.gadgetbridge/databases/Gadgetbridge.db ./

# 从电脑推送到手机
adb push Gadgetbridge.db /sdcard/Download/手环/
```

#### 步骤2：验证数据采集

```bash
# 等待30秒让系统读取数据
sleep 30

# 查询当前心率
adb shell curl http://127.0.0.1:8080/biometrics
```

如果返回心率数据，说明配置成功！

### 数据更新频率

- **Gadgetbridge同步频率**：1-5分钟（取决于手环型号）
- **本应用轮询频率**：30秒
- **数据延迟**：最多30秒 - 5分钟

### 支持的手环品牌

- ✅ 小米手环（Mi Band）系列
- ✅ 华为手环（Honor Band）系列
- ⚠️ 其他品牌可能需要额外配置

---

## Intiface玩具控制

### 前提条件

1. ✅ 已安装 Intiface Central 应用
2. ✅ 智能玩具支持 Buttplug.io 协议
3. ✅ 玩具已与手机蓝牙配对

### 配置步骤

#### 步骤1：安装Intiface Central

**Android版本：**
1. 访问 [intiface.com](https://intiface.com)
2. 下载 Android APK
3. 安装到手机

**桌面版本（Windows/Mac）：**
1. 下载桌面版 Intiface Central
2. 安装并运行
3. 确保手机和电脑在同一局域网

#### 步骤2：启动Intiface服务器

1. 打开 Intiface Central
2. 点击"Start Server"
3. 确认端口显示为 **12345**
4. 保持应用在后台运行

#### 步骤3：连接玩具

1. 在 Intiface Central 中点击"Scan for Devices"
2. 开启玩具（确保蓝牙已开启）
3. 等待设备出现在列表中
4. 点击设备旁的"Connect"按钮
5. 连接成功后设备显示为绿色

#### 步骤4：测试连接

```bash
# 检查连接状态
adb shell curl http://127.0.0.1:8080/status
# 查看 buttplug_connected 是否为 true

# 测试玩具震动
adb shell curl 'http://127.0.0.1:8080/vibrate?mode=mode_1&target=toy'
```

### 支持的设备

理论上支持所有 Buttplug.io 兼容设备，包括但不限于：
- Lovense 系列
- We-Vibe 系列
- Kiiroo 系列
- 其他支持蓝牙控制的智能玩具

### 注意事项

⚠️ **BLE安全限制**：
- 每个控制命令间隔至少 80ms
- 系统会自动限制发送频率
- 不建议长时间连续高强度使用

⚠️ **电池续航**：
- 智能玩具电量消耗较快
- 建议使用前充满电
- 注意观察电量指示

---

## 常见问题

### Q1: 手机不震动怎么办？

**检查清单：**
1. ✅ 应用是否已安装并启动？
2. ✅ 震动权限是否已授予？
3. ✅ API调用是否返回success: true？
4. ✅ Android版本是否≥8.0（API 26）？

**解决方案：**
```bash
# 查看日志
adb logcat -s HapticEngine:D

# 检查权限
adb shell pm list permissions -g | grep android.permission.VIBRATE
```

### Q2: 心率数据显示"No HR data available"？

**可能原因：**
1. ❌ 未导出 Gadgetbridge 数据库
2. ❌ 数据库文件路径不正确
3. ❌ 手环未与 Gadgetbridge 同步
4. ❌ 手环型号不支持

**解决方案：**
```bash
# 检查文件是否存在
adb shell ls -l /sdcard/Download/手环/Gadgetbridge.db

# 查看采集日志
adb logcat -s BiometricCollector:D
```

### Q3: Intiface连接失败？

**检查清单：**
1. ✅ Intiface Central是否已启动？
2. ✅ 服务器端口是否为12345？
3. ✅ 玩具是否已连接到Intiface？
4. ✅ 蓝牙是否已开启？

**解决方案：**
```bash
# 查看连接日志
adb logcat -s ButtplugWS:D

# 检查Intiface状态
# 在Intiface Central中查看服务器状态
```

### Q4: 为什么连续震动感觉不同？

这是**正常现象**！系统设计了微随机算法，每次震动会在基础模式上注入±15%的随机变化，目的是：
- 避免感官适应（麻木）
- 保持新鲜感
- 更加自然真实

### Q5: 如何调整震动强度？

使用 `/set_level` 端点：

```bash
# 设置为档位1（最轻）
curl 'http://127.0.0.1:8080/set_level?level=1'

# 设置为档位5（最强）
curl 'http://127.0.0.1:8080/set_level?level=5'
```

强度档位会立即生效，影响后续所有震动命令。

---

## 故障排查

### 服务无法启动

**症状**：API调用返回"Connection refused"

**排查步骤：**
```bash
# 1. 检查应用是否运行
adb shell ps | grep androidremotecontrolmcp

# 2. 查看服务日志
adb logcat -s HapticMiddleware:I

# 3. 重启应用
adb shell am force-stop com.danielealbano.androidremotecontrolmcp.debug
adb shell am start -n com.danielealbano.androidremotecontrolmcp.debug/.ui.MainActivity
```

### 震动延迟过高

**症状**：调用API后1-2秒才震动

**可能原因**：
- 系统负载过高
- 协程调度延迟
- 网络回环性能问题

**解决方案**：
- 关闭其他后台应用
- 重启设备
- 更新到最新版本

### 心率数据过时

**症状**：心率数据长时间不更新

**原因**：
- Gadgetbridge同步频率低（正常）
- 手环未佩戴或电量不足
- 数据库文件未及时更新

**解决方案**：
1. 在Gadgetbridge中手动同步
2. 确保手环正常佩戴
3. 重新导出数据库文件

### 玩具响应缓慢

**症状**：玩具震动延迟或断断续续

**可能原因**：
- 蓝牙信号弱
- 玩具电量低
- BLE连接不稳定

**解决方案**：
- 将手机靠近玩具
- 给玩具充电
- 重新连接玩具到Intiface

---

## 高级用法

### 与SillyTavern集成

触觉中台设计为与SillyTavern配合使用，通过MCP协议自动触发震动。

**配置步骤：**
1. 确保MCP Server正常运行
2. 在SillyTavern中配置MCP连接
3. AI消息中使用标签：`[haptic: 模式1]`
4. 系统自动解析并触发震动

**示例对话：**
```
AI: 我明白你的感受 [haptic: mode_3]
```

### 自定义脚本

可以编写脚本实现复杂的震动序列：

```bash
#!/bin/bash
# 渐进式震动序列

for level in 1 2 3 4 5; do
  curl "http://127.0.0.1:8080/set_level?level=$level"
  curl "http://127.0.0.1:8080/vibrate?mode=模式1&target=all"
  sleep 3
done
```

### 心率联动

根据心率变化动态调整震动强度：

```bash
#!/bin/bash
# 心率联动脚本

while true; do
  HR=$(curl -s http://127.0.0.1:8080/biometrics | jq '.current')
  
  if [ $HR -lt 70 ]; then
    LEVEL=2
  elif [ $HR -lt 90 ]; then
    LEVEL=3
  else
    LEVEL=4
  fi
  
  curl "http://127.0.0.1:8080/set_level?level=$LEVEL"
  sleep 30
done
```

---

## 技术支持

### 获取帮助

- 📖 查看完整文档：`HAPTIC_IMPLEMENTATION.md`
- 🔧 故障排查指南：`BUILD_TROUBLESHOOTING.md`
- 📊 API文档：本手册"API使用指南"章节
- 🐛 问题反馈：通过GitHub Issues报告

### 日志收集

遇到问题时，请收集以下日志：

```bash
# 完整日志
adb logcat -d > full.log

# 触觉中台相关日志
adb logcat -s HapticMiddleware HapticEngine ButtplugWS BiometricCollector > haptic.log
```

---

## 附录

### 完整模式列表

| 编号 | 中文名称 | 英文名称 | 特点 |
|-----|---------|---------|------|
| 1 | 模式1 | mode_1 | 长短交替 |
| 2 | 模式2 | mode_2 | 快速脉冲 |
| 3 | 模式3 | mode_3 | 短促连续 |
| 4 | 模式4 | mode_4 | 渐变节奏 |
| 5 | 模式5 | mode_5 | 波浪起伏 |
| 6 | 模式6 | mode_6 | 快速点击 |
| 7 | 模式7 | mode_7 | 持续震动 |
| 8 | 模式8 | mode_8 | 变速组合 |
| 9 | 模式9 | mode_9 | 三连击 |
| 10 | 模式10 | mode_10 | 律动循环 |
| 11 | 模式11 | mode_11 | 小跳跃 |
| 12 | 模式12 | mode_12 | 长波浪 |
| 13 | 模式13 | mode_13 | 间歇式 |
| 14 | 模式14 | mode_14 | 递增式 |
| 15 | 模式15 | mode_15 | 渐强式 |
| 16 | 模式16 | mode_16 | 深度脉冲 |
| 17 | 模式17 | mode_17 | 简短三段 |
| 18 | 模式18 | mode_18 | 长间歇 |
| 19 | 模式19 | mode_19 | 超长波 |
| 20 | 模式20 | mode_20 | 复合节奏 |
| 21 | 模式21 | mode_21 | 递减式 |
| 22 | 模式22 | mode_22 | 加速式 |
| 23 | 模式23 | mode_23 | 特殊模式 |

### 快捷命令参考

```bash
# === 基础测试 ===
adb shell curl http://127.0.0.1:8080/status
adb shell curl 'http://127.0.0.1:8080/vibrate?mode=mode_1&target=phone'

# === 强度调节 ===
adb shell curl 'http://127.0.0.1:8080/set_level?level=1'  # 最轻
adb shell curl 'http://127.0.0.1:8080/set_level?level=5'  # 最强

# === 心率查询 ===
adb shell curl http://127.0.0.1:8080/biometrics
adb shell curl 'http://127.0.0.1:8080/biometrics?duration=300'

# === 紧急停止 ===
adb shell curl http://127.0.0.1:8080/stop

# === 日志查看 ===
adb logcat -s HapticMiddleware:I HapticEngine:I ButtplugWS:I
```

---

**文档版本**: v1.0.0  
**最后更新**: 2026-07-04  
**适用版本**: Android Remote Control MCP v1.0.0+
