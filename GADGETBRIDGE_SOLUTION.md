# Gadgetbridge数据采集方案总结（基于orangechat源码分析）

## 核心发现

orangechat **不使用广播**，而是通过以下方式获取Gadgetbridge数据：

### 方法1：读取导出的数据库文件（推荐，无需特殊权限）
```kotlin
// 数据库路径（用户需手动导出）
val paths = listOf(
    "/sdcard/Download/手环/Gadgetbridge.db",
    "/storage/emulated/0/Download/手环/Gadgetbridge.db",
    "/sdcard/下载/手环/Gadgetbridge.db"
)
```

### 方法2：使用Shizuku复制内部数据库（需要ADB或Root）
```kotlin
// 内部数据库路径（需要Shizuku权限）
val DB_PATH = "/data/data/nodomain.freeyourgadget.gadgetbridge/databases/Gadgetbridge.db"
```

## 数据库结构

### 小米手环表：XIAOMI_ACTIVITY_SAMPLE
```sql
SELECT TIMESTAMP, HEART_RATE, STEPS, STRESS, SPO2
FROM XIAOMI_ACTIVITY_SAMPLE
WHERE HEART_RATE > 0
ORDER BY TIMESTAMP DESC
LIMIT 1
```

字段说明：
- `TIMESTAMP`: 时间戳（**秒**，不是毫秒）
- `HEART_RATE`: 心率值（>0 表示有效数据）
- `STEPS`: 步数
- `STRESS`: 压力值
- `SPO2`: 血氧饱和度

### 华为手环表：HUAWEI_ACTIVITY_SAMPLE
```sql
SELECT TIMESTAMP, HEART_RATE, STEPS, SPO
FROM HUAWEI_ACTIVITY_SAMPLE
WHERE TIMESTAMP <= OTHER_TIMESTAMP  -- 过滤占位行
  AND HEART_RATE > 0
ORDER BY TIMESTAMP DESC
LIMIT 1
```

关键差异：
- 华为表有占位行，需要用 `TIMESTAMP <= OTHER_TIMESTAMP` 过滤
- 字段名略有不同（`SPO` vs `SPO2`）
- 无 `STRESS` 字段（压力数据在单独的 `HUAWEI_STRESS_SAMPLE` 表）

### 每日汇总表：XIAOMI_DAILY_SUMMARY_SAMPLE
```sql
SELECT TIMESTAMP, STEPS, HR_RESTING, HR_MAX, HR_MIN, HR_AVG, STRESS_AVG, CALORIES, SPO2_AVG
FROM XIAOMI_DAILY_SUMMARY_SAMPLE
WHERE TIMESTAMP >= ?
ORDER BY TIMESTAMP ASC
```

## 实现建议

### 最简单方案（无需特殊权限）

1. **让用户导出Gadgetbridge数据库**
   - 在Gadgetbridge设置中选择"导出数据库"
   - 或使用文件管理器复制到：`/sdcard/Download/手环/Gadgetbridge.db`

2. **应用定期读取文件**
   ```kotlin
   val db = SQLiteDatabase.openDatabase(
       "/sdcard/Download/手环/Gadgetbridge.db",
       null,
       SQLiteDatabase.OPEN_READONLY
   )
   ```

3. **检测手环厂商**
   ```kotlin
   val cursor = db.query("DEVICE", arrayOf("MANUFACTURER"), ...)
   val manufacturer = cursor.getString(0).lowercase()
   val isHuawei = manufacturer.contains("huawei")
   ```

4. **查询心率数据**
   ```kotlin
   val table = if (isHuawei) "HUAWEI_ACTIVITY_SAMPLE" else "XIAOMI_ACTIVITY_SAMPLE"
   val where = if (isHuawei) 
       "TIMESTAMP <= OTHER_TIMESTAMP AND HEART_RATE > 0" 
       else "HEART_RATE > 0"
   ```

### 高级方案（需要Shizuku）

如果需要实时数据而不依赖用户手动导出：
1. 集成Shizuku SDK
2. 请求Shizuku权限
3. 使用 `Shizuku.newProcess()` 复制内部数据库文件
4. 查询复制后的数据库

## 配置要求

### AndroidManifest.xml
```xml
<!-- 读取外部存储（导出的数据库文件） -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />

<!-- 如果使用Shizuku方案 -->
<queries>
    <package android:name="moe.shizuku.privileged.api" />
</queries>
```

### 依赖项（Shizuku方案，可选）
```kotlin
dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
```

## 时间戳转换

**重要**：Gadgetbridge表中的TIMESTAMP单位是**秒**，需要转换为毫秒：
```kotlin
val timestampMs = cursor.getLong(0) * 1000L
```

## 用户指南

### 如何导出Gadgetbridge数据库

**方法1：使用Gadgetbridge内置功能**
1. 打开Gadgetbridge应用
2. 进入 设置 → 数据库管理 → 导出数据库
3. 选择导出位置：`/sdcard/Download/手环/`

**方法2：使用文件管理器（需要Root）**
1. 使用支持Root的文件管理器（如MT管理器）
2. 导航到：`/data/data/nodomain.freeyourgadget.gadgetbridge/databases/`
3. 复制 `Gadgetbridge.db` 到 `/sdcard/Download/手环/`

**方法3：使用ADB**
```bash
adb pull /data/data/nodomain.freeyourgadget.gadgetbridge/databases/Gadgetbridge.db ./
adb push Gadgetbridge.db /sdcard/Download/手环/
```

## BiometricCollector更新计划

基于以上发现，`BiometricCollector.kt`需要修改为：

1. **移除广播接收器代码**（Gadgetbridge不提供实时广播）
2. **改用轮询模式**：每30秒读取一次数据库文件
3. **支持两种路径**：
   - 用户导出的文件：`/sdcard/Download/手环/Gadgetbridge.db`
   - Root访问内部文件：`/data/data/nodomain.freeyourgadget.gadgetbridge/databases/Gadgetbridge.db`
4. **自动检测厂商**：读取`DEVICE`表判断是小米还是华为
5. **兼容两种表结构**：`XIAOMI_ACTIVITY_SAMPLE` vs `HUAWEI_ACTIVITY_SAMPLE`

## 性能考虑

- **轮询间隔**：30秒（Gadgetbridge通常每1-5分钟更新一次心率）
- **缓存策略**：保留最近600个样本（约10分钟）
- **数据库连接**：每次查询后立即关闭，避免文件锁定
- **错误处理**：数据库不存在或无法访问时静默失败，不影响触觉中台运行

## 测试验证

```kotlin
// 测试代码
val collector = BiometricCollector(context)
collector.start() // 启动轮询

// 等待30秒后查询
delay(30000)
val response = collector.getCurrentData()
println("Heart rate: ${response.current} bpm")

// 清理
collector.stop()
```

## 限制说明

1. **非实时**：数据延迟30秒-5分钟（取决于Gadgetbridge同步频率）
2. **需要手动导出**：用户首次使用时需要手动导出数据库文件
3. **文件访问权限**：需要用户授予存储权限
4. **手环连接状态**：手环必须与Gadgetbridge保持连接

---

**参考文件**：
- `orangechat/app/src/main/java/me/rerere/rikkahub/data/gadgetbridge/GadgetbridgeReader.kt`
- `orangechat/app/src/main/java/me/rerere/rikkahub/data/service/GadgetbridgeService.kt`

**下一步**：更新 `BiometricCollector.kt` 实现基于文件的轮询方案
