# PowerShell 编译脚本
# 解决Git Bash的loopback连接问题

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Android 触觉中台编译脚本 (PowerShell)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$ProjectDir = "D:\BaiduNetdisk\AI\claude\android-remote-control-mcp-pulse"

# 切换到项目目录
Set-Location $ProjectDir
Write-Host "[1/3] 切换到项目目录: $ProjectDir" -ForegroundColor Yellow
Write-Host ""

# 清理旧构建
Write-Host "[2/3] 清理旧构建..." -ForegroundColor Yellow
& .\gradlew.bat clean --console=plain
if ($LASTEXITCODE -ne 0) {
    Write-Host "[错误] 清理失败" -ForegroundColor Red
    Read-Host "按Enter键退出"
    exit 1
}
Write-Host ""

# 编译Debug APK
Write-Host "[3/3] 编译 Debug APK (跳过测试)..." -ForegroundColor Yellow
& .\gradlew.bat assembleDebug -x test --console=plain
if ($LASTEXITCODE -ne 0) {
    Write-Host "[错误] 编译失败" -ForegroundColor Red
    Read-Host "按Enter键退出"
    exit 1
}
Write-Host ""

# 检查输出文件
$ApkPath = "app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $ApkPath) {
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "  编译成功！" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "APK 位置：$ApkPath" -ForegroundColor Cyan
    $ApkSize = (Get-Item $ApkPath).Length / 1MB
    Write-Host "APK 大小：$([Math]::Round($ApkSize, 2)) MB" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "下一步：" -ForegroundColor Yellow
    Write-Host "  adb install -r $ApkPath" -ForegroundColor White
    Write-Host ""
} else {
    Write-Host "[错误] APK 未生成" -ForegroundColor Red
}

Read-Host "按Enter键退出"
