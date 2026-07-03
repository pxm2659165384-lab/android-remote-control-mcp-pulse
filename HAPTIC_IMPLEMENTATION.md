# Android触觉中台（Haptic Middleware）实现文档

## 项目概述

本项目为 android-remote-control-mcp-pulse 添加触觉中台功能，支持：
- 23种震动模式的程序化触觉引擎
- 微随机解构算法（±15%噪点）避免感官适应
- Buttplug.io 协议支持外部智能玩具
- Gadgetbridge 心率数据实时采集
- 本地 HTTP API (127.0.0.1:8080)

## 实现状态

### ✅ 已完成

#### 阶段一：权限与依赖配置
- [x] 添加 Ktor CIO 引擎依赖 (2.3.7)
- [x] 添加 Ktor WebSocket 客户端依赖
- [x] 配置 AndroidManifest.xml 权限：
  - VIBRATE（震动）
  - BODY_SENSORS（心率传感器）
  - FOREGROUND_SERVICE_SPECIAL_USE
  - Gadgetbridge 自定义权限
- [x] 注册 HapticMiddlewareService 前台服务

#### 阶段二&三：核心触觉引擎
- [x] **HapticPatternLibrary.kt**: 23种模式硬编码数据（从 vibration_profiles.json 转换）
  - 每种模式5个强度等级
  - 支持中英文模式名（"模式1" / "mode_1"）
  - 总计 115 个预设 timing 数组

- [x] **ProceduralHapticEngine.kt**: 程序化触觉引擎
  - 微随机解构算法（±15%时序抖动）
  - 协程互斥锁控制，支持强制覆盖
  - 三种目标：phone、toy、all
  - 全局强度档位管理（1-5）

- [x] **ButtplugWebSocketClient.kt**: Buttplug.io 客户端
  - 连接 Intiface Central (ws://127.0.0.1:12345)
  - 指数退避重连（1s → 16s，最多100次）
  - ScalarCmd 发送（BLE安全，最小80ms间隔）
  - NonCancellable 上下文保证玩具安全关闭

#### 阶段四：生物特征采集
- [x] **BiometricCollector.kt**: 心率数据采集器
  - 三种采集方案（广播 > ContentProvider > 数据库）
  - 环形缓冲区（600样本，约10分钟）
  - 实时统计：当前值、平均值、最大值、标准差

#### 阶段五：HTTP中台服务
- [x] **HapticMiddlewareService.kt**: 前台服务主控制器
  - Ktor HTTP 服务器 (127.0.0.1:8080)
  - 5个 API 端点（见下方）
  - 协程驱动，非阻塞架构

## API 端点规范

### 1. 触发震动
```
GET /vibrate?mode=模式1&target=all
GET /vibrate?mode=mode_5&target=toy
GET /vibrate?mode=任意名称&target=phone

参数：
  - mode: 模式名称（必需）
  - target: phone | toy | all（默认all）

响应：
{
  "success": true,
  "mode": "模式1",
  "target": "all",
  "level": 3
}
```

### 2. 获取心率数据
```
GET /biometrics                  # 当前最新值
GET /biometrics?duration=120     # 近120秒统计

响应：
{
  "success": true,
  "current": 78,
  "avg": 75,
  "max": 82,
  "stddev": 3.2,
  "sampleCount": 45
}
```

### 3. 系统状态
```
GET /status

响应：
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

### 4. 紧急停止
```
GET /stop

响应：
{
  "success": true,
  "message": "All haptics stopped"
}
```

### 5. 设置强度
```
GET /set_level?level=4

参数：
  - level: 1-5（必需）

响应：
{
  "success": true,
  "level": 4
}
```

## 技术架构

### 核心设计原则

1. **数据硬编码**：振动数据不依赖外部文件，全部硬编码在 `HapticPatternLibrary`
2. **强度档位内部化**：API 不接收 intensity 参数，由 App 内部设置控制
3. **最新意志优先**：使用 Mutex + cancelAndJoin() 确保新命令瞬间覆盖旧震动
4. **BLE 安全**：每个 ScalarCmd 间隔至少 80ms
5. **协程取消安全**：NonCancellable 上下文确保玩具必定收到关闭信号

### 数据流

```
SillyTavern [haptic: mode_1]
    ↓ HTTP POST
MCP Server (解析标签)
    ↓ GET /vibrate?mode=mode_1&target=all
Ktor Server (127.0.0.1:8080)
    ↓
ProceduralHapticEngine
    ├─→ 查询 HapticPatternLibrary
    ├─→ 应用微随机噪点（±15%）
    ├─→ 手机马达 (VibrationEffect)
    └─→ ButtplugWebSocketClient
            └─→ Intiface Central (ws://127.0.0.1:12345)
                    └─→ 外部玩具 (BLE)
```

## 文件清单

### 新增文件
```
app/src/main/kotlin/com/danielealbano/androidremotecontrolmcp/services/haptic/
├── HapticPatternLibrary.kt          # 23种模式数据库（~300行）
├── ProceduralHapticEngine.kt        # 触觉引擎（~150行）
├── ButtplugWebSocketClient.kt       # WebSocket客户端（~180行）
├── BiometricCollector.kt            # 心率采集器（~200行）
└── HapticMiddlewareService.kt       # HTTP服务主控（~180行）
```

### 修改文件
```
app/build.gradle.kts                 # 添加 Ktor CIO + WebSocket 依赖
app/src/main/AndroidManifest.xml     # 添加权限 + 注册服务
```

## 版本兼容性

| 组件 | 最低版本 | 当前项目 | 状态 |
|-----|---------|---------|------|
| Android OS | API 26 (8.0) | API 33 | ✅ 兼容 |
| Kotlin | 1.8.0+ | 已满足 | ✅ |
| Ktor | 2.3.7 | 2.3.7 | ✅ |
| Gadgetbridge | 0.70.0+ | 待验证 | ⚠️ |
| Intiface Central | 2.0.0+ | 待安装 | ⚠️ |

## 待完成事项

### 🔴 高优先级

1. **网络问题修复**
   - 当前 Gradle wrapper 无法下载（Connection timed out）
   - 建议：配置代理或使用本地 Gradle 安装
   - 文件：`gradle/wrapper/gradle-wrapper.properties` (已调整超时至60s)

2. **编译验证**
   ```bash
   cd android-remote-control-mcp-pulse
   ./gradlew assembleDebug -x test
   ```

3. **Gadgetbridge 广播 Action 确认**
   - 当前使用推测值：`nodomain.freeyourgadget.gadgetbridge.HEART_RATE`
   - 需从 orangechat 源码确认准确名称
   - 位置：`BiometricCollector.kt:49`

### 🟡 中优先级

4. **UI 集成**
   - 在主 Activity 添加启动/停止触觉中台的按钮
   - 添加强度档位设置界面（1-5）
   - 显示连接状态指示器

5. **通知优化**
   - 将 `ButtplugWebSocketClient.notifyUser()` 改为 Android Notification
   - 显示重连次数和连接状态

6. **orangechat 源码审阅**
   - 确认 Gadgetbridge ContentProvider URI
   - 确认数据库表结构（MI_BAND_ACTIVITY_SAMPLE）
   - 验证心率字段名称

### 🟢 低优先级

7. **单元测试**
   ```kotlin
   HapticPatternLibraryTest.kt     # 验证23种模式完整性
   ProceduralHapticEngineTest.kt   # 验证随机噪点算法
   BiometricCollectorTest.kt       # 验证统计计算
   ```

8. **性能优化**
   - Ktor 服务器连接池配置
   - WebSocket 消息队列优化
   - 心率数据压缩存储

9. **文档完善**
   - 添加 SillyTavern 插件配置指南
   - 添加 Intiface Central 安装教程
   - 添加故障排查文档

## 测试验收标准

### 阶段二验证
```bash
# 启动服务后测试状态
curl http://127.0.0.1:8080/status

预期响应：
{
  "success": true,
  "ktor_server": "running",
  ...
}
```

### 阶段三验证（关键）
```bash
# 连续5次触发模式1
for i in {1..5}; do
  curl "http://127.0.0.1:8080/vibrate?mode=模式1&target=phone"
  sleep 2
done

预期：每次手机的振动节奏应有微妙差异（不是完全重复的循环）
检查：日志显示每次生成的 timing 数组值略有不同
```

### 阶段四验证
```bash
# 获取心率统计（需手环在线）
curl "http://127.0.0.1:8080/biometrics?duration=60"

预期响应：
{
  "success": true,
  "current": 78,
  "avg": 75,
  ...
}
```

### 最终联调
1. 手机开启 Intiface Central（连接外部玩具）
2. 启动魔改 App
3. 戴手环确保 Gadgetbridge 运行
4. 通过 SillyTavern 插件发送包含 `[haptic: 模式3]` 标签的 AI 消息
5. 验证：手机和外部玩具同时随机震动
6. 2分钟后查询心率：`GET /biometrics?duration=120`
7. SillyTavern 的 Author's Note 中出现心率报告

## 已知问题

### 问题1：Gradle下载超时
- **现象**：`Connection timed out` 无法下载 Gradle 8.14.4
- **原因**：网络环境限制
- **解决方案**：
  1. 配置 HTTP/HTTPS 代理
  2. 手动下载 Gradle 到 `~/.gradle/wrapper/dists/`
  3. 使用系统全局 Gradle（需安装）

### 问题2：Gadgetbridge权限
- **现象**：可能无法读取 Gadgetbridge 数据
- **原因**：自定义权限需用户手动授权
- **解决方案**：在首次启动时使用 `ActivityCompat.requestPermissions()`

### 问题3：Intiface Central端口冲突
- **现象**：WebSocket 连接失败
- **原因**：Intiface Central 可能使用非标准端口
- **解决方案**：检查 Intiface 设置，调整 `ButtplugWebSocketClient.kt:68` 的端口

## 关键代码片段

### 微随机算法核心
```kotlin
private fun applyMicroRandomization(timing: LongArray): LongArray {
    return timing.map { ms ->
        val factor = 1.0 + Random.nextDouble(-0.15, 0.15)
        (ms * factor).toLong().coerceAtLeast(10)
    }.toLongArray()
}
```

### 强制覆盖机制
```kotlin
hapticMutex.withLock {
    activeHapticJob?.cancelAndJoin()  // 掐断旧震动
    activeHapticJob = engineScope.launch {
        try {
            // 新震动逻辑
        } finally {
            vibrator.cancel()  // 确保马达归零
        }
    }
}
```

### 协程取消安全
```kotlin
try {
    // 震动循环
} finally {
    withContext(NonCancellable) {
        sendScalarCmd(0.0)  // 即使被取消也必须执行
    }
}
```

## 参考资源

1. **Buttplug.io 开发文档**: https://buttplug.io/docs/dev-guide/
2. **Intiface Central 文档**: https://intiface.com/docs/intiface-central/quickstart
3. **Gadgetbridge 官方文档**: https://gadgetbridge.org/basics/
4. **Ktor 官方文档**: https://ktor.io/docs/
5. **Android VibrationEffect API**: https://developer.android.com/reference/android/os/VibrationEffect

## 贡献者注意事项

1. **不要修改 timing 数组格式**：必须是 `[停顿, 震动, 停顿, 震动, ...]` 交替格式
2. **保持 BLE 安全间隔**：每个 ScalarCmd 之间至少 80ms
3. **测试随机性**：连续触发同一模式时，timing 值应略有不同
4. **日志规范**：使用 `Log.i/w/e` 并带上 TAG 常量
5. **协程安全**：所有长时间操作必须在 `Dispatchers.IO` 或 `Dispatchers.Default`

## 下一步行动

### 立即执行
1. 解决网络/Gradle问题，完成首次编译
2. 确认 Gadgetbridge 广播 Action 名称
3. 在真机上测试基础震动功能

### 短期规划
4. 集成到主 Activity UI
5. 添加启动时权限请求逻辑
6. 测试 Buttplug.io 连接

### 长期规划
7. 优化性能和内存占用
8. 添加更多震动模式
9. 支持自定义模式编辑器

---

**最后更新**: 2026-07-03  
**版本**: v1.0.0-dev  
**状态**: 核心代码完成，待编译验证
