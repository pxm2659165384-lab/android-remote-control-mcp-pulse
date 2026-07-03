# Gadgetbridge 广播 Action 确认指南

## 目的
从 orangechat 源码中确认准确的 Gadgetbridge 广播 Action 名称，替换 `BiometricCollector.kt` 中的推测值。

## 需要确认的信息

### 1. 广播 Action 名称
**当前使用的推测值**（需替换）：
```kotlin
val actionHeartRate = "nodomain.freeyourgadget.gadgetbridge.HEART_RATE"
```

**查找位置**：
- `D:\BaiduNetdisk\AI\claude\orangechat\`
- 可能的文件路径：
  - `data/gadgetbridge/GadgetbridgeService.kt`
  - `data/gadgetbridge/GadgetbridgeReceiver.kt`
  - `data/gadgetbridge/*.kt`

**查找命令**：
```bash
cd "D:\BaiduNetdisk\AI\claude\orangechat"

# 方法1：查找广播注册代码
grep -r "registerReceiver" --include="*.kt" --include="*.java" | grep -i "gadgetbridge"

# 方法2：查找 Intent 常量定义
grep -r "ACTION_.*HEART" --include="*.kt" --include="*.java"

# 方法3：查找 IntentFilter 注册
grep -r "IntentFilter" --include="*.kt" | grep -i "heart\|hr\|bpm"

# 方法4：查找 BroadcastReceiver 实现
grep -r "BroadcastReceiver" --include="*.kt" | grep -i "gadgetbridge"
```

### 2. Intent Extra 字段名
**当前使用的推测值**（需确认）：
```kotlin
val bpm = intent.getIntExtra("HR", intent.getIntExtra("HEART_RATE", -1))
```

**查找命令**：
```bash
# 查找心率数据提取代码
grep -r "getIntExtra\|getString" --include="*.kt" | grep -i "heart\|hr\|bpm"
```

### 3. ContentProvider URI（方案B）
**当前使用的推测值**（需确认）：
```kotlin
val uri = Uri.parse("content://nodomain.freeyourgadget.gadgetbridge.healthdata/heart_rate")
```

**查找命令**：
```bash
# 查找 ContentProvider 查询代码
grep -r "contentResolver.query" --include="*.kt" | grep -i "gadgetbridge"

# 查找 URI 定义
grep -r "content://" --include="*.kt" | grep -i "gadgetbridge"
```

### 4. 数据库表结构（方案C）
**当前使用的推测值**（需确认）：
```sql
SELECT TIMESTAMP, HEART_RATE FROM MI_BAND_ACTIVITY_SAMPLE
WHERE TIMESTAMP > ? ORDER BY TIMESTAMP ASC
```

**查找命令**：
```bash
# 查找数据库查询代码
grep -r "rawQuery\|execSQL" --include="*.kt" | grep -i "heart\|sample"

# 查找表名常量
grep -r "MI_BAND\|ACTIVITY_SAMPLE" --include="*.kt"
```

## 审阅步骤

### Step 1: 列出相关文件
```bash
cd "D:\BaiduNetdisk\AI\claude\orangechat"
find . -name "*.kt" -o -name "*.java" | grep -i "gadgetbridge"
```

### Step 2: 查看核心文件
优先查看的文件（如果存在）：
1. `GadgetbridgeService.kt` - 主要服务类
2. `GadgetbridgeReceiver.kt` - 广播接收器
3. `HeartRateCollector.kt` - 心率采集器
4. `BiometricDataSource.kt` - 生物特征数据源

### Step 3: 提取关键代码片段
查找以下代码模式：

#### 模式1: 广播接收器注册
```kotlin
val receiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 提取这里的逻辑
    }
}
val filter = IntentFilter("确认这个Action名称")
context.registerReceiver(receiver, filter)
```

#### 模式2: ContentProvider 查询
```kotlin
val uri = Uri.parse("content://确认这个URI")
val cursor = context.contentResolver.query(uri, ...)
cursor?.use {
    val bpm = it.getInt(it.getColumnIndexOrThrow("确认字段名"))
}
```

#### 模式3: 数据库查询
```kotlin
val db = SQLiteDatabase.openDatabase("路径", ...)
val cursor = db.rawQuery("SELECT 确认字段名 FROM 确认表名 ...", ...)
```

## 确认后的修改

### 修改位置
`app/src/main/kotlin/com/danielealbano/androidremotecontrolmcp/services/haptic/BiometricCollector.kt`

### 修改内容
```kotlin
// 第 49 行左右，替换 Action 名称
val actionHeartRate = "【从orangechat确认的准确名称】"

// 第 59 行左右，替换 Extra 字段名
val bpm = intent.getIntExtra("【确认的字段名】", -1)

// 第 150 行左右，替换 ContentProvider URI
val uri = Uri.parse("【确认的URI】")

// 第 157 行左右，替换字段名
val bpm = it.getInt(it.getColumnIndexOrThrow("【确认的字段名】"))

// 第 169 行左右，替换表名和字段名
val cursor = db.rawQuery(
    "SELECT 【确认的时间戳字段】, 【确认的心率字段】 FROM 【确认的表名】 ...",
    ...
)
```

## 常见 Gadgetbridge Action 名称参考

根据 Gadgetbridge 官方文档，可能的 Action 名称：
```
nodomain.freeyourgadget.gadgetbridge.ACTION_REALTIME_SAMPLES
nodomain.freeyourgadget.gadgetbridge.ACTION_DEVICE_CHANGED
nodomain.freeyourgadget.gadgetbridge.ACTION_NEW_ACTIVITY_DATA
```

可能的 Extra 字段名：
```
EXTRA_REALTIME_SAMPLE
EXTRA_HEART_RATE
EXTRA_TIMESTAMP
hr_value
heartRate
```

## 验证方法

### 方法1: 运行 orangechat 并抓取日志
```bash
# 安装 orangechat
adb install orangechat.apk

# 启动 Gadgetbridge 和 orangechat
# 查看日志中的广播信息
adb logcat | grep -i "heart\|intent\|broadcast"
```

### 方法2: 使用 Gadgetbridge 官方文档
https://gadgetbridge.org/basics/
查看"Integration"或"Broadcasting"章节

### 方法3: 直接查看 Gadgetbridge 源码
```bash
# 如果有 Gadgetbridge 源码
cd gadgetbridge-source
grep -r "sendBroadcast" | grep -i "heart"
```

## 完成检查清单

- [ ] 确认广播 Action 名称
- [ ] 确认 Intent Extra 字段名（心率值）
- [ ] 确认 Intent Extra 字段名（时间戳，如果有）
- [ ] 确认 ContentProvider URI（可选）
- [ ] 确认 ContentProvider 列名（可选）
- [ ] 确认数据库表名（仅Root方案需要）
- [ ] 确认数据库字段名（仅Root方案需要）
- [ ] 更新 `BiometricCollector.kt` 中的硬编码值
- [ ] 重新编译并测试

## 测试验证

修改完成后，使用以下命令测试：
```bash
# 启动应用并触发心率采集
adb shell am start -n com.danielealbano.androidremotecontrolmcp.debug/.ui.MainActivity

# 查看日志确认是否收到广播
adb logcat -s BiometricCollector:D

# 测试 API 端点
adb shell curl http://127.0.0.1:8080/biometrics

# 预期响应（有数据时）：
# {"success":true,"current":78,"avg":78,...}
```

## 如果找不到 orangechat 源码

### 备选方案1: 反编译 orangechat APK
```bash
# 使用 jadx 反编译
jadx orangechat.apk -d orangechat-decompiled
cd orangechat-decompiled
grep -r "Gadgetbridge" .
```

### 备选方案2: 使用 Gadgetbridge 官方示例
查看 Gadgetbridge GitHub 仓库的示例代码：
https://github.com/Freeyourgadget/Gadgetbridge/tree/master/app/src/main/java/nodomain/freeyourgadget/gadgetbridge

### 备选方案3: 运行时抓取广播
使用 adb 监控广播：
```bash
adb shell am broadcast --help
adb logcat -s ActivityManager:I | grep "broadcast"
```

## 注意事项

1. **权限问题**：确认 AndroidManifest.xml 中已声明：
   ```xml
   <uses-permission android:name="nodomain.freeyourgadget.gadgetbridge.permission.FETCH_HEALTH_DATA" />
   ```

2. **运行时权限**：某些 Android 版本可能需要在运行时请求权限

3. **Gadgetbridge 版本差异**：不同版本的 Gadgetbridge 可能使用不同的广播名称，建议使用最新稳定版

4. **设备兼容性**：确认 Gadgetbridge 支持用户的手环型号

---

**优先级**: 🔴 高（阻塞编译测试）  
**预计时间**: 30-60分钟  
**下一步**: 确认信息后更新 `BiometricCollector.kt`
