[CmdletBinding()]
param(
    [ValidateSet("Adb", "Lan", "Hotspot", "Auto")]
    [string]$Mode = "Adb",
    [string]$Serial = "",
    [string]$Package = "com.danielealbano.androidremotecontrolmcp.debug",
    [string]$Adb = "",
    [int]$AppPort = 8080,
    [int]$BridgePort = 8081,
    [string]$PhoneBaseUrl = "",
    [string]$PcIp = "",
    [string]$BridgeHost = "",
    [switch]$NoTest,
    [switch]$StopOnly,
    [switch]$KeepIntiface,
    [switch]$KeepGadgetbridge,
    [switch]$VisibleBridge
)

$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host "[PulseLink] $Message"
}

function Resolve-Adb {
    param([string]$Override)

    if ($Override) {
        if (Test-Path -LiteralPath $Override) {
            return (Resolve-Path -LiteralPath $Override).Path
        }
        throw "ADB path not found: $Override"
    }

    $candidates = @()
    if ($env:ANDROID_HOME) {
        $candidates += Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
    }
    if ($env:ANDROID_SDK_ROOT) {
        $candidates += Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe"
    }
    if ($env:LOCALAPPDATA) {
        $candidates += Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
    }

    foreach ($candidate in $candidates | Select-Object -Unique) {
        if ($candidate -and (Test-Path -LiteralPath $candidate)) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    $command = Get-Command adb -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    throw "adb.exe not found. Install Android platform-tools or pass -Adb <path>."
}

function Resolve-Serial {
    param(
        [string]$AdbPath,
        [string]$RequestedSerial
    )

    if ($RequestedSerial) {
        return $RequestedSerial
    }

    $devices = & $AdbPath devices | Select-Object -Skip 1 | Where-Object { $_ -match "\sdevice$" }
    $serials = @(
        $devices |
        ForEach-Object { ($_ -split "\s+")[0] } |
        Where-Object { $_ }
    )

    if (-not $serials -or $serials.Count -eq 0) {
        throw "No adb device is connected. Unlock the phone and authorize USB debugging."
    }

    if ($serials.Count -gt 1) {
        Write-Step "Multiple devices found; using $($serials[0]). Pass -Serial to choose another."
    }

    return $serials[0]
}

function Resolve-Package {
    param(
        [string]$AdbPath,
        [string]$DeviceSerial,
        [string]$PreferredPackage
    )

    $packages = @(
        $PreferredPackage,
        "com.danielealbano.androidremotecontrolmcp.debug",
        "com.danielealbano.androidremotecontrolmcp"
    ) | Select-Object -Unique

    foreach ($pkg in $packages) {
        $path = & $AdbPath -s $DeviceSerial shell pm path $pkg 2>$null
        if (($path -join "`n") -match "package:") {
            return $pkg
        }
    }

    throw "Pulse Link app package not found. Install the debug APK first."
}

function Invoke-JsonGet {
    param(
        [string]$Uri,
        [int]$TimeoutSec = 2
    )

    Invoke-RestMethod -UseBasicParsing -Uri $Uri -TimeoutSec $TimeoutSec
}

function Join-QueryString {
    param(
        [string]$BaseUrl,
        [hashtable]$Query
    )

    $pairs = @()
    foreach ($key in $Query.Keys) {
        $value = [string]$Query[$key]
        $pairs += "$([uri]::EscapeDataString($key))=$([uri]::EscapeDataString($value))"
    }
    return "${BaseUrl}?$($pairs -join '&')"
}

function Normalize-BaseUrl {
    param([string]$Url)

    if (-not $Url) {
        return ""
    }

    $normalized = $Url.Trim().TrimEnd("/")
    if ($normalized -notmatch "^https?://") {
        $normalized = "http://$normalized"
    }
    return $normalized
}

function Resolve-PreferredPcIp {
    param([string]$RequestedIp)

    if ($RequestedIp) {
        return $RequestedIp
    }

    $addresses = @()
    if (Get-Command Get-NetIPAddress -ErrorAction SilentlyContinue) {
        $addresses = @(
            Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
                Where-Object {
                    $_.IPAddress -and
                    $_.IPAddress -ne "127.0.0.1" -and
                    $_.IPAddress -notmatch "^169\.254\." -and
                    $_.IPAddress -notmatch "^0\."
                } |
                Sort-Object -Property InterfaceMetric, InterfaceIndex |
                Select-Object -ExpandProperty IPAddress
        )
    }

    if (-not $addresses -or $addresses.Count -eq 0) {
        $addresses = @(
            [System.Net.Dns]::GetHostAddresses([System.Net.Dns]::GetHostName()) |
                Where-Object {
                    $_.AddressFamily -eq [System.Net.Sockets.AddressFamily]::InterNetwork -and
                    $_.IPAddressToString -ne "127.0.0.1" -and
                    $_.IPAddressToString -notmatch "^169\.254\."
                } |
                ForEach-Object { $_.IPAddressToString }
        )
    }

    if (-not $addresses -or $addresses.Count -eq 0) {
        throw "Unable to determine a LAN IPv4 address. Pass -PcIp <PC hotspot/Wi-Fi IP>."
    }

    return $addresses[0]
}

function Resolve-PythonLauncher {
    $python = Get-Command python -ErrorAction SilentlyContinue
    if ($python) {
        return @{ File = $python.Source; PrefixArgs = @() }
    }

    $py = Get-Command py -ErrorAction SilentlyContinue
    if ($py) {
        return @{ File = $py.Source; PrefixArgs = @("-3") }
    }

    throw "Python was not found. Install Python or add it to PATH."
}

function Start-BridgeIfNeeded {
    param(
        [string]$ListenHost,
        [int]$Port,
        [switch]$Visible
    )

    $statusUrl = "http://127.0.0.1:$Port/status"
    try {
        $status = Invoke-JsonGet -Uri $statusUrl -TimeoutSec 1
        if ($ListenHost -eq "0.0.0.0" -and -not (Test-BridgeListeningOnLan -Port $Port)) {
            Write-Step "PC bridge is running on loopback only; restarting for LAN access."
            Stop-Bridge -Port $Port
            Start-Sleep -Milliseconds 500
            throw "restart_for_lan"
        }
        Write-Step "PC bridge already running on $statusUrl"
        return $status
    } catch {
        Write-Step "Starting PC bridge on $statusUrl"
    }

    $scriptPath = Join-Path $PSScriptRoot "pulselink_pc_bridge.py"
    if (-not (Test-Path -LiteralPath $scriptPath)) {
        throw "Bridge script not found: $scriptPath"
    }

    $python = Resolve-PythonLauncher
    $listenHost = if ($ListenHost) { $ListenHost } else { "127.0.0.1" }
    $outLog = Join-Path $PSScriptRoot "pulselink_pc_bridge.out.log"
    $errLog = Join-Path $PSScriptRoot "pulselink_pc_bridge.err.log"
    $pidFile = Join-Path $PSScriptRoot "pulselink_pc_bridge.pid"
    $arguments = @($python.PrefixArgs) + @("`"$scriptPath`"", "--host", $listenHost, "--port", "$Port")
    $windowStyle = if ($Visible) { "Normal" } else { "Hidden" }
    $process =
        Start-Process `
            -FilePath $python.File `
            -ArgumentList $arguments `
            -RedirectStandardOutput $outLog `
            -RedirectStandardError $errLog `
            -WindowStyle $windowStyle `
            -PassThru
    Set-Content -LiteralPath $pidFile -Value $process.Id -Encoding ASCII

    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Milliseconds 300
        try {
            $status = Invoke-JsonGet -Uri $statusUrl -TimeoutSec 1
            Write-Step "PC bridge is ready. Logs: $outLog"
            return $status
        } catch {
            if ($process.HasExited) {
                $err = ""
                if (Test-Path -LiteralPath $errLog) {
                    $err = Get-Content -LiteralPath $errLog -Raw
                }
                throw "PC bridge exited early. $err"
            }
        }
    }

    throw "PC bridge did not become ready. Check $outLog and $errLog"
}

function Test-BridgeListeningOnLan {
    param([int]$Port)

    $listeners = @(
        Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty LocalAddress
    )
    if (-not $listeners -or $listeners.Count -eq 0) {
        return $false
    }
    return $listeners -contains "0.0.0.0" -or $listeners -contains "::" -or ($listeners | Where-Object { $_ -ne "127.0.0.1" -and $_ -ne "::1" }).Count -gt 0
}

function Test-BridgeStatus {
    param([object]$BridgeStatus)

    if (-not $BridgeStatus.controller_connected) {
        Write-Step "Warning: no XInput controller detected by the PC bridge."
    } else {
        Write-Step "XInput controller indices: $($BridgeStatus.controller_indices -join ', ')"
    }
}

function Stop-Bridge {
    param([int]$Port)

    try {
        Invoke-JsonGet -Uri "http://127.0.0.1:$Port/stop" -TimeoutSec 1 | Out-Null
        Write-Step "Bridge motor stop sent."
    } catch {
        Write-Step "Bridge stop endpoint is not reachable."
    }

    $pidFile = Join-Path $PSScriptRoot "pulselink_pc_bridge.pid"
    if (Test-Path -LiteralPath $pidFile) {
        $pidText = Get-Content -LiteralPath $pidFile -Raw
        $bridgePid = 0
        if ([int]::TryParse($pidText.Trim(), [ref]$bridgePid)) {
            $process = Get-Process -Id $bridgePid -ErrorAction SilentlyContinue
            if ($process) {
                Stop-Process -Id $bridgePid -Force
                Write-Step "Bridge process stopped: $bridgePid"
            }
        }
        Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
    }

    $listeners = @(
        Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty OwningProcess -Unique
    )
    foreach ($listenerPid in $listeners) {
        $commandLine =
            Get-CimInstance Win32_Process -Filter "ProcessId=$listenerPid" -ErrorAction SilentlyContinue |
                Select-Object -ExpandProperty CommandLine -First 1
        if ($commandLine -and $commandLine -match "pulselink_pc_bridge\.py" -and $commandLine -match "--port\s+$Port") {
            Stop-Process -Id $listenerPid -Force -ErrorAction SilentlyContinue
            Write-Step "Bridge listener process stopped: $listenerPid"
        }
    }
}

$effectiveMode = $Mode
if ($effectiveMode -eq "Auto") {
    $effectiveMode = if ($PhoneBaseUrl) { "Lan" } else { "Adb" }
}
$isHotspotMode = $effectiveMode -eq "Hotspot"
if ($isHotspotMode) {
    $effectiveMode = "Lan"
}

if ($effectiveMode -eq "Lan") {
    $phoneUrl = Normalize-BaseUrl -Url $PhoneBaseUrl
    $pcLanIp = Resolve-PreferredPcIp -RequestedIp $PcIp
    $listenHost = if ($BridgeHost) { $BridgeHost } else { "0.0.0.0" }

    Write-Step "Mode: $(if ($isHotspotMode) { 'HOTSPOT' } else { 'LAN' })"
    Write-Step "PC bridge listen host: $listenHost"
    Write-Step "PC bridge LAN address for phone: $pcLanIp`:$BridgePort"
    Write-Step "If the phone cannot reach it, allow Python or TCP $BridgePort through Windows Firewall for the current network."
    if ($isHotspotMode) {
        Write-Step "Hotspot topology: phone runs SillyTavern+Pulse Link app; PC connects to phone hotspot; phone calls this PC bridge."
    }

    if ($StopOnly) {
        if ($phoneUrl) {
            try {
                Invoke-JsonGet -Uri "$phoneUrl/stop" -TimeoutSec 2 | Out-Null
                Write-Step "App /stop sent to $phoneUrl"
            } catch {
                Write-Step "App /stop is not reachable at $phoneUrl; continuing."
            }
        }
        Stop-Bridge -Port $BridgePort
        Write-Step "Stopped."
        exit 0
    }

    $bridgeStatus = Start-BridgeIfNeeded -ListenHost $listenHost -Port $BridgePort -Visible:$VisibleBridge
    Test-BridgeStatus -BridgeStatus $bridgeStatus

    if ($phoneUrl) {
        Write-Step "Registering PC bridge as LAN matrix node on phone."
        $configUrl = Join-QueryString -BaseUrl "$phoneUrl/matrix/config" -Query @{
            mode = "MASTER"
            clear = "true"
            node = $pcLanIp
            port = "$BridgePort"
            attenuation = "1.0"
            label = "PC-XInput-Bridge"
        }
        Invoke-JsonGet -Uri $configUrl -TimeoutSec 5 | Out-Null

        if (-not $NoTest) {
            Write-Step "Sending LAN test vibration to gamepad."
            $testUrl = Join-QueryString -BaseUrl "$phoneUrl/vibrate" -Query @{
                mode = "mode_3"
                level = "4"
                randomize = "false"
                target = "gamepad"
                targets = "gamepad"
            }
            $test = Invoke-JsonGet -Uri $testUrl -TimeoutSec 8
            Write-Step "Test response: $($test | ConvertTo-Json -Compress)"
        }

        Write-Step "Ready. Phone/App can reach PC bridge at http://$pcLanIp`:$BridgePort"
    } else {
        $manualConfig = Join-QueryString -BaseUrl "http://127.0.0.1:$AppPort/matrix/config" -Query @{
            mode = "MASTER"
            clear = "true"
            node = $pcLanIp
            port = "$BridgePort"
            attenuation = "1.0"
            label = "PC-XInput-Bridge"
        }
        $manualTest = "http://127.0.0.1:$AppPort/vibrate?mode=mode_3&level=4&randomize=false&target=gamepad&targets=gamepad"
        Write-Step "PhoneBaseUrl was not provided, so only the PC bridge was started."
        Write-Step "Plugin/App base URL on the phone stays: http://127.0.0.1:$AppPort"
        Write-Step "On the phone/Termux side, open or curl this once to register the PC bridge:"
        Write-Step $manualConfig
        if (-not $NoTest) {
            Write-Step "Then open or curl this to test the PC gamepad route:"
            Write-Step $manualTest
        }
    }
    exit 0
}

$adbPath = Resolve-Adb -Override $Adb
$deviceSerial = Resolve-Serial -AdbPath $adbPath -RequestedSerial $Serial
$resolvedPackage = Resolve-Package -AdbPath $adbPath -DeviceSerial $deviceSerial -PreferredPackage $Package
$baseUrl = "http://127.0.0.1:$AppPort"
$component = "$resolvedPackage/com.danielealbano.androidremotecontrolmcp.services.mcp.AdbServiceTrampolineActivity"

Write-Step "Device: $deviceSerial"
Write-Step "Package: $resolvedPackage"

if ($StopOnly) {
    try {
        Invoke-JsonGet -Uri "$baseUrl/stop" -TimeoutSec 2 | Out-Null
    } catch {
        Write-Step "App /stop is not reachable; continuing."
    }
    & $adbPath -s $deviceSerial shell am start -n $component --es action pulse_stop | Out-Null
    Stop-Bridge -Port $BridgePort
    Write-Step "Stopped."
    exit 0
}

$adbBridgeHost = if ($BridgeHost) { $BridgeHost } else { "127.0.0.1" }
$bridgeStatus = Start-BridgeIfNeeded -ListenHost $adbBridgeHost -Port $BridgePort -Visible:$VisibleBridge
Test-BridgeStatus -BridgeStatus $bridgeStatus

Write-Step "Configuring adb forward/reverse."
& $adbPath -s $deviceSerial forward "tcp:$AppPort" "tcp:$AppPort" | Out-Null
& $adbPath -s $deviceSerial reverse "tcp:$BridgePort" "tcp:$BridgePort" | Out-Null
& $adbPath -s $deviceSerial shell svc power stayon usb | Out-Null

$enableIntiface = if ($KeepIntiface) { "true" } else { "false" }
$enableGadgetbridge = if ($KeepGadgetbridge) { "true" } else { "false" }
Write-Step "Starting Pulse Link service."
& $adbPath -s $deviceSerial shell am start -n $component --es action pulse_start --ez enable_intiface $enableIntiface --ez enable_gadgetbridge $enableGadgetbridge | Out-Null

Write-Step "Waiting for App Pulse Link HTTP server."
$status = $null
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Milliseconds 500
    try {
        $status = Invoke-JsonGet -Uri "$baseUrl/status" -TimeoutSec 2
        break
    } catch {
        $status = $null
    }
}
if (-not $status) {
    throw "App Pulse Link HTTP server is not reachable at $baseUrl. Keep the phone unlocked and try again."
}

Write-Step "Registering PC bridge as matrix node."
$configUrl = "$baseUrl/matrix/config?mode=MASTER&clear=true&node=adb-reverse&port=$BridgePort&attenuation=1.0&label=PC-XInput-Bridge"
Invoke-JsonGet -Uri $configUrl -TimeoutSec 5 | Out-Null

if (-not $NoTest) {
    Write-Step "Sending test vibration to gamepad."
    $testUrl = "$baseUrl/vibrate?mode=mode_3&level=4&randomize=false&target=gamepad&targets=gamepad"
    $test = Invoke-JsonGet -Uri $testUrl -TimeoutSec 8
    Write-Step "Test response: $($test | ConvertTo-Json -Compress)"
}

Write-Step "Ready. Plugin/App can now use $baseUrl/vibrate?...&target=gamepad&targets=gamepad"
