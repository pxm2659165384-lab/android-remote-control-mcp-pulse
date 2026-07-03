@echo off
REM 触觉中台快速验证脚本 (Windows)
REM 用途：编译验证 + 部署测试

setlocal enabledelayedexpansion

echo =========================================
echo   Android 触觉中台验证脚本 (Windows)
echo =========================================
echo.

REM 进入项目目录
cd /d "%~dp0"

REM 步骤1：检查依赖
echo [1/6] 检查构建依赖...
if not exist "gradlew.bat" (
    echo [错误] 未找到 gradlew.bat 脚本
    pause
    exit /b 1
)

where adb >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到 adb，请安装 Android SDK Platform-Tools
    echo 请将 adb 添加到系统 PATH，或安装 Android Studio
    pause
    exit /b 1
)
echo [成功] 依赖检查通过
echo.

REM 步骤2：清理旧构建
echo [2/6] 清理旧构建产物...
call gradlew.bat clean --console=plain
echo [成功] 清理完成
echo.

REM 步骤3：编译 Debug APK
echo [3/6] 编译 Debug APK (跳过测试)...
call gradlew.bat assembleDebug -x test --console=plain
if %errorlevel% neq 0 (
    echo [错误] 编译失败，请检查错误日志
    pause
    exit /b 1
)
echo [成功] 编译成功
echo.

REM 步骤4：检查设备连接
echo [4/6] 检查 Android 设备连接...
adb devices | findstr /r "device$" >nul
if %errorlevel% neq 0 (
    echo [错误] 未检测到 Android 设备
    echo 请连接设备或启动模拟器，然后重新运行此脚本
    pause
    exit /b 1
)
echo [成功] 检测到设备
adb devices
echo.

REM 步骤5：安装 APK
echo [5/6] 安装 APK 到设备...
set APK_PATH=app\build\outputs\apk\debug\app-debug.apk
if not exist "%APK_PATH%" (
    echo [错误] 未找到 APK 文件：%APK_PATH%
    pause
    exit /b 1
)
adb install -r "%APK_PATH%"
echo [成功] 安装完成
echo.

REM 步骤6：启动应用
echo [6/6] 启动应用...
set PACKAGE_NAME=com.danielealbano.androidremotecontrolmcp.debug
set ACTIVITY_NAME=.ui.MainActivity
adb shell am start -n "%PACKAGE_NAME%/%ACTIVITY_NAME%"
echo [成功] 应用已启动
echo.

REM 等待服务启动
echo 等待触觉中台服务启动（5秒）...
timeout /t 5 /nobreak >nul
echo.

REM 测试 API 端点
echo =========================================
echo   API 端点测试
echo =========================================
echo.

echo 测试 1: /status 端点
adb shell "curl -s http://127.0.0.1:8080/status 2>/dev/null || wget -qO- http://127.0.0.1:8080/status"
echo.
echo.

echo 测试 2: /vibrate 端点（模式1）
adb shell "curl -s 'http://127.0.0.1:8080/vibrate?mode=模式1&target=phone' 2>/dev/null"
echo.
echo [提示] 如果手机震动，说明触觉引擎工作正常
echo.

echo 测试 3: /biometrics 端点
adb shell "curl -s http://127.0.0.1:8080/biometrics 2>/dev/null"
echo.
echo.

REM 完成
echo =========================================
echo   验证完成！
echo =========================================
echo.
echo 下一步操作：
echo 1. 检查日志：adb logcat -s HapticMiddleware HapticEngine ButtplugWS
echo 2. 手动测试更多模式：
echo    adb shell curl 'http://127.0.0.1:8080/vibrate?mode=mode_2&target=all'
echo 3. 查看完整 API 文档：type HAPTIC_IMPLEMENTATION.md
echo 4. 设置强度档位：
echo    adb shell curl 'http://127.0.0.1:8080/set_level?level=4'
echo.

pause
