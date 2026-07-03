#!/bin/bash
# 触觉中台快速验证脚本
# 用途：编译验证 + 部署测试

set -e  # 遇到错误立即退出

echo "========================================="
echo "  Android 触觉中台验证脚本"
echo "========================================="
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 项目根目录
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

# 步骤1：检查依赖
echo -e "${YELLOW}[1/6] 检查构建依赖...${NC}"
if [ ! -f "gradlew" ]; then
    echo -e "${RED}错误：未找到 gradlew 脚本${NC}"
    exit 1
fi

if ! command -v adb &> /dev/null; then
    echo -e "${RED}错误：未找到 adb，请安装 Android SDK Platform-Tools${NC}"
    exit 1
fi
echo -e "${GREEN}✓ 依赖检查通过${NC}"
echo ""

# 步骤2：清理旧构建
echo -e "${YELLOW}[2/6] 清理旧构建产物...${NC}"
./gradlew clean --console=plain
echo -e "${GREEN}✓ 清理完成${NC}"
echo ""

# 步骤3：编译 Debug APK
echo -e "${YELLOW}[3/6] 编译 Debug APK（跳过测试）...${NC}"
./gradlew assembleDebug -x test --console=plain
if [ $? -ne 0 ]; then
    echo -e "${RED}✗ 编译失败，请检查错误日志${NC}"
    exit 1
fi
echo -e "${GREEN}✓ 编译成功${NC}"
echo ""

# 步骤4：检查设备连接
echo -e "${YELLOW}[4/6] 检查 Android 设备连接...${NC}"
DEVICE_COUNT=$(adb devices | grep -w "device" | wc -l)
if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo -e "${RED}✗ 未检测到 Android 设备${NC}"
    echo "请连接设备或启动模拟器，然后重新运行此脚本"
    exit 1
fi
echo -e "${GREEN}✓ 检测到 $DEVICE_COUNT 台设备${NC}"
adb devices
echo ""

# 步骤5：安装 APK
echo -e "${YELLOW}[5/6] 安装 APK 到设备...${NC}"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}✗ 未找到 APK 文件：$APK_PATH${NC}"
    exit 1
fi
adb install -r "$APK_PATH"
echo -e "${GREEN}✓ 安装完成${NC}"
echo ""

# 步骤6：启动应用
echo -e "${YELLOW}[6/6] 启动应用...${NC}"
PACKAGE_NAME="com.danielealbano.androidremotecontrolmcp.debug"
ACTIVITY_NAME=".ui.MainActivity"
adb shell am start -n "${PACKAGE_NAME}/${ACTIVITY_NAME}"
echo -e "${GREEN}✓ 应用已启动${NC}"
echo ""

# 等待服务启动
echo -e "${YELLOW}等待触觉中台服务启动（5秒）...${NC}"
sleep 5

# 测试 API 端点
echo ""
echo "========================================="
echo "  API 端点测试"
echo "========================================="
echo ""

# 使用 adb shell 在设备内部测试（避免端口转发）
echo -e "${YELLOW}测试 1: /status 端点${NC}"
adb shell "curl -s http://127.0.0.1:8080/status" | head -20
echo ""

echo -e "${YELLOW}测试 2: /vibrate 端点（模式1）${NC}"
adb shell "curl -s 'http://127.0.0.1:8080/vibrate?mode=模式1&target=phone'"
echo ""
echo -e "${GREEN}✓ 如果手机震动，说明触觉引擎工作正常${NC}"
echo ""

echo -e "${YELLOW}测试 3: /biometrics 端点${NC}"
adb shell "curl -s http://127.0.0.1:8080/biometrics"
echo ""

# 完成
echo ""
echo "========================================="
echo -e "${GREEN}  验证完成！${NC}"
echo "========================================="
echo ""
echo "下一步操作："
echo "1. 检查日志：adb logcat -s HapticMiddleware HapticEngine ButtplugWS"
echo "2. 手动测试更多模式：adb shell curl 'http://127.0.0.1:8080/vibrate?mode=mode_2&target=all'"
echo "3. 查看完整 API 文档：cat HAPTIC_IMPLEMENTATION.md"
echo ""
