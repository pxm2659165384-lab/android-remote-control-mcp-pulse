#!/bin/bash
# 触觉中台 API 快速测试脚本
# 前提：应用已安装并运行，触觉中台服务已启动

echo "========================================="
echo "  触觉中台 API 快速测试"
echo "========================================="
echo ""

# 检查 adb 连接
if ! command -v adb &> /dev/null; then
    echo "错误：未找到 adb 命令"
    exit 1
fi

DEVICE_COUNT=$(adb devices | grep -w "device" | wc -l)
if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo "错误：未检测到 Android 设备"
    exit 1
fi

echo "设备已连接，开始测试..."
echo ""

# 测试1：系统状态
echo "测试 1: 系统状态 (/status)"
echo "----------------------------------------"
adb shell "curl -s http://127.0.0.1:8080/status 2>/dev/null" | grep -o '"[^"]*":[^,}]*' | head -10
echo ""
echo ""

# 测试2：触发震动（模式1，手机）
echo "测试 2: 触发震动 - 模式1 (手机应该震动)"
echo "----------------------------------------"
adb shell "curl -s 'http://127.0.0.1:8080/vibrate?mode=模式1&target=phone' 2>/dev/null"
echo ""
sleep 2
echo ""

# 测试3：触发震动（模式5，所有设备）
echo "测试 3: 触发震动 - mode_5 (所有设备)"
echo "----------------------------------------"
adb shell "curl -s 'http://127.0.0.1:8080/vibrate?mode=mode_5&target=all' 2>/dev/null"
echo ""
sleep 2
echo ""

# 测试4：获取心率数据
echo "测试 4: 获取心率数据 (/biometrics)"
echo "----------------------------------------"
adb shell "curl -s http://127.0.0.1:8080/biometrics 2>/dev/null"
echo ""
echo ""

# 测试5：设置强度档位
echo "测试 5: 设置强度档位为 4"
echo "----------------------------------------"
adb shell "curl -s 'http://127.0.0.1:8080/set_level?level=4' 2>/dev/null"
echo ""
echo ""

# 测试6：验证随机性（连续3次触发同一模式）
echo "测试 6: 验证微随机算法（连续触发模式3）"
echo "----------------------------------------"
echo "手机应该震动3次，每次节奏略有不同..."
for i in 1 2 3; do
    echo "第 $i 次触发..."
    adb shell "curl -s 'http://127.0.0.1:8080/vibrate?mode=模式3&target=phone' 2>/dev/null" > /dev/null
    sleep 3
done
echo "✓ 如果每次震动感觉略有不同，说明微随机算法工作正常"
echo ""

# 测试7：紧急停止
echo "测试 7: 紧急停止 (/stop)"
echo "----------------------------------------"
adb shell "curl -s http://127.0.0.1:8080/stop 2>/dev/null"
echo ""
echo ""

# 查看日志
echo "========================================="
echo "  查看服务日志（最近20条）"
echo "========================================="
adb logcat -d -s HapticMiddleware:I HapticEngine:I ButtplugWS:I | tail -20
echo ""

echo "========================================="
echo "  测试完成！"
echo "========================================="
echo ""
echo "更多测试命令："
echo "  查看实时日志：adb logcat -s HapticMiddleware HapticEngine ButtplugWS"
echo "  测试所有模式：for i in {1..23}; do adb shell curl \"http://127.0.0.1:8080/vibrate?mode=模式\$i&target=phone\"; sleep 2; done"
echo "  获取历史心率：adb shell curl 'http://127.0.0.1:8080/biometrics?duration=300'"
echo ""
