@echo off
REM 使用本地Gradle编译项目
REM 解决Git Bash的loopback连接问题

echo ========================================
echo   开始编译 Android 触觉中台
echo ========================================
echo.

cd /d "D:\BaiduNetdisk\AI\claude\android-remote-control-mcp-pulse"

echo [1/3] 清理旧构建...
call gradlew.bat clean --console=plain
if %errorlevel% neq 0 (
    echo [错误] 清理失败
    pause
    exit /b 1
)

echo.
echo [2/3] 编译 Debug APK (跳过测试)...
call gradlew.bat assembleDebug -x test --console=plain
if %errorlevel% neq 0 (
    echo [错误] 编译失败
    pause
    exit /b 1
)

echo.
echo [3/3] 检查输出文件...
if exist "app\build\outputs\apk\debug\app-debug.apk" (
    echo [成功] APK 已生成
    dir "app\build\outputs\apk\debug\app-debug.apk"
) else (
    echo [错误] APK 未生成
    pause
    exit /b 1
)

echo.
echo ========================================
echo   编译完成！
echo ========================================
echo.
echo APK 位置：
echo app\build\outputs\apk\debug\app-debug.apk
echo.
echo 下一步：
echo   adb install -r app\build\outputs\apk\debug\app-debug.apk
echo.
pause
