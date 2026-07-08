@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("FunctionNaming", "LongMethod", "MagicNumber")

package com.danielealbano.androidremotecontrolmcp.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielealbano.androidremotecontrolmcp.services.pulselink.HapticMiddlewareService
import com.danielealbano.androidremotecontrolmcp.services.pulselink.HapticModeDefinition
import com.danielealbano.androidremotecontrolmcp.services.pulselink.HapticPatternLibrary
import com.danielealbano.androidremotecontrolmcp.services.pulselink.LanMatrixManager
import com.danielealbano.androidremotecontrolmcp.services.pulselink.MatrixMode
import com.danielealbano.androidremotecontrolmcp.services.pulselink.MediaTransitionManager
import com.danielealbano.androidremotecontrolmcp.services.pulselink.PulseLogEntry
import com.danielealbano.androidremotecontrolmcp.services.pulselink.PulseLogger
import com.danielealbano.androidremotecontrolmcp.services.pulselink.PulseServiceState
import com.danielealbano.androidremotecontrolmcp.services.pulselink.RelayNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun PulseLinkScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by HapticMiddlewareService.runtimeStatus.collectAsStateWithLifecycle()
    val logs by PulseLogger.entries.collectAsStateWithLifecycle()
    var gamepadSummary by rememberSaveable { mutableStateOf("手柄：未扫描") }
    var selectedMode by rememberSaveable { mutableStateOf("mode_1") }
    var selectedLevel by rememberSaveable { mutableStateOf(3f) }
    var selectedTarget by rememberSaveable { mutableStateOf("phone") }
    var modeFilter by rememberSaveable { mutableStateOf("all") }
    var randomize by rememberSaveable { mutableStateOf(true) }
    var keepScreenAwake by rememberSaveable { mutableStateOf(true) }
    var controlsLocked by rememberSaveable { mutableStateOf(true) }
    var unlockProgress by rememberSaveable { mutableStateOf(0f) }
    var showServiceInfo by rememberSaveable { mutableStateOf(false) }
    var serviceDetail by rememberSaveable { mutableStateOf(PulseServiceDetailUi.empty().displayText()) }
    var isTesting by rememberSaveable { mutableStateOf(false) }
    var curveProgress by rememberSaveable { mutableStateOf(0f) }
    var customName by rememberSaveable { mutableStateOf("") }
    var customCurve by rememberSaveable { mutableStateOf("") }
    var exportText by rememberSaveable { mutableStateOf("") }
    var nodeDialogOpen by rememberSaveable { mutableStateOf(false) }
    var newNodeIp by rememberSaveable { mutableStateOf("") }
    var newNodePort by rememberSaveable { mutableStateOf(HapticMiddlewareService.DEFAULT_PORT.toString()) }
    var mediaStartMs by rememberSaveable { mutableStateOf(MediaTransitionManager.config.startPositionMs.toString()) }
    val running = status.state == PulseServiceState.RUNNING
    val levelInt = selectedLevel.roundToInt().coerceIn(1, 5)
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    val pickMedia =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                MediaTransitionManager.setFile(context, it)
                mediaStartMs = MediaTransitionManager.config.startPositionMs.toString()
            }
        }
    val exportCurveLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
            uri?.let {
                runCatching {
                    context.contentResolver.openOutputStream(it)?.use { stream ->
                        stream.write(exportText.toByteArray(Charsets.UTF_8))
                    }
                }.onSuccess {
                    PulseLogger.i("自定义曲线已导出为文件")
                }.onFailure {
                    PulseLogger.w("自定义曲线文件导出失败：${it.message}")
                }
            }
        }
    val importCurveLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                runCatching {
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    }.orEmpty()
                }.onSuccess { text ->
                    customCurve = text.trim()
                    if (customName.isBlank()) {
                        customName = inferCurveLabel(text)
                    }
                    PulseLogger.i("自定义曲线文件已导入")
                }.onFailure {
                    PulseLogger.w("自定义曲线文件导入失败：${it.message}")
                }
            }
        }

    KeepScreenAwake(keepScreenAwake)

    LaunchedEffect(Unit) {
        LanMatrixManager.load(context)
        MediaTransitionManager.load(context)
        HapticPatternLibrary.loadUserModes(context)
        mediaStartMs = MediaTransitionManager.config.startPositionMs.toString()
    }

    LaunchedEffect(status.state) {
        if (status.state == PulseServiceState.RUNNING) {
            gamepadSummary = readGamepadSummary()
        } else {
            isTesting = false
        }
    }

    LaunchedEffect(isTesting, selectedMode, selectedLevel, selectedTarget, randomize) {
        if (!isTesting) {
            curveProgress = 0f
            return@LaunchedEffect
        }
        if (!running) {
            PulseLogger.w("持续测试已停止：Pulse Link 服务未运行")
            isTesting = false
            return@LaunchedEffect
        }
        while (isActive) {
            runCatching {
                sendPulseVibrate(
                    mode = selectedMode,
                    level = levelInt,
                    randomize = randomize,
                    target = selectedTarget,
                )
            }.onFailure {
                PulseLogger.w("持续震动测试请求失败：${it.message}")
                isTesting = false
                return@LaunchedEffect
            }
            val duration = HapticPatternLibrary.patternDuration(selectedMode, levelInt).coerceAtLeast(450L)
            val startedAt = System.currentTimeMillis()
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startedAt
                curveProgress = (elapsed.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                if (elapsed >= duration) break
                delay(80L)
            }
        }
    }

    LaunchedEffect(showServiceInfo, status.state) {
        if (!showServiceInfo) return@LaunchedEffect
        while (isActive) {
            serviceDetail =
                withContext(Dispatchers.IO) {
                    runCatching {
                        parseServiceDetail(readPulseEndpoint("/status")).displayText()
                    }.getOrElse {
                        if (running) "状态详情：读取失败 ${it.message}" else PulseServiceDetailUi.empty().displayText()
                    }
                }
            delay(2000L)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("脉冲联动控制台") },
            windowInsets = WindowInsets(0),
            actions = {
                IconButton(onClick = { showServiceInfo = !showServiceInfo }) {
                    Icon(Icons.Default.Info, contentDescription = "连接信息")
                }
            },
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PowerConsoleCard(
                running = running,
                controlsLocked = controlsLocked,
                localIp = status.localIp,
                port = status.port,
                onPower = {
                    if (running) {
                        HapticMiddlewareService.stop(context)
                    } else {
                        HapticMiddlewareService.start(context)
                    }
                },
                onEmergencyStop = {
                    isTesting = false
                    scope.launch { sendPulseStop() }
                },
            )
            SlideUnlockControl(
                locked = controlsLocked,
                progress = unlockProgress,
                onProgressChange = { unlockProgress = it },
                onUnlocked = {
                    controlsLocked = false
                    unlockProgress = 1f
                },
                onLock = {
                    controlsLocked = true
                    unlockProgress = 0f
                    isTesting = false
                },
            )
            AnimatedVisibility(showServiceInfo) {
                ServiceInfoPanel(
                    running = running,
                    status = if (running) "运行中" else "已停止",
                    localIp = status.localIp,
                    port = status.port,
                    error = status.error,
                    gamepadSummary = gamepadSummary,
                    serviceDetail = serviceDetail,
                    keepScreenAwake = keepScreenAwake,
                    onKeepScreenAwakeChange = { keepScreenAwake = it },
                    onRefreshGamepads = {
                        scope.launch { gamepadSummary = readGamepadSummary(refresh = true) }
                    },
                )
            }

            RouteAndTestCard(
                context = context,
                running = running,
                controlsLocked = controlsLocked,
                selectedTarget = selectedTarget,
                selectedMode = selectedMode,
                selectedLevel = levelInt,
                isTesting = isTesting,
                localIp = status.localIp,
                onTargetChange = { selectedTarget = it },
                onStartTargetTest = { target ->
                    if (!controlsLocked && running) {
                        selectedTarget = target
                        isTesting = true
                    }
                },
                onStopTesting = {
                    isTesting = false
                    scope.launch { sendPulseStop() }
                },
                onAddNode = { nodeDialogOpen = true },
            )

            HapticModeConsole(
                context = context,
                selectedMode = selectedMode,
                selectedLevel = selectedLevel,
                modeFilter = modeFilter,
                randomize = randomize,
                controlsLocked = controlsLocked,
                isTesting = isTesting,
                curveProgress = curveProgress,
                customName = customName,
                customCurve = customCurve,
                onModeChange = { selectedMode = it },
                onLevelChange = { selectedLevel = it },
                onFilterChange = { modeFilter = it },
                onRandomizeChange = { randomize = it },
                onStartTesting = { if (running && !controlsLocked) isTesting = true },
                onStopTesting = {
                    isTesting = false
                    scope.launch { sendPulseStop() }
                },
                onCustomNameChange = { customName = it },
                onCustomCurveChange = { customCurve = it },
                onSaveCustom = {
                    runCatching {
                        HapticPatternLibrary.saveCustomPattern(context, customName, customCurve)
                    }.onSuccess {
                        selectedMode = it.id
                        customName = ""
                        customCurve = ""
                        PulseLogger.i("自定义曲线已保存 ${it.label}")
                    }.onFailure {
                        PulseLogger.w("自定义曲线保存失败：${it.message}")
                    }
                },
                onCopyExport = { text ->
                    exportText = text
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText("Pulse Link curve", text))
                    PulseLogger.i("自定义曲线数字串已复制")
                },
                onFileExport = { text ->
                    exportText = text
                    exportCurveLauncher.launch("${HapticPatternLibrary.labelOf(selectedMode)}.pulse-curve.txt")
                },
                onFileImport = {
                    importCurveLauncher.launch(arrayOf("text/*", "application/json", "application/octet-stream"))
                },
            )

            MediaTransitionCard(
                displayName = MediaTransitionManager.config.displayName,
                startMs = mediaStartMs,
                controlsLocked = controlsLocked,
                onStartMsChange = {
                    mediaStartMs = it
                    MediaTransitionManager.setStartPosition(context, it.toLongOrNull() ?: 0L)
                    MediaTransitionManager.refreshCoverPreview(context)
                },
                onPickFile = { pickMedia.launch(arrayOf("video/*", "audio/*")) },
                onClear = { MediaTransitionManager.clear(context) },
                onTestPlay = {
                    runCatching { MediaTransitionManager.play(context) }
                        .onFailure { PulseLogger.e("测试媒体播放失败：${it.message}", it) }
                },
            )

            LogCard(
                logs = logs.takeLast(120),
                timeFormat = timeFormat,
            )
        }
    }

    if (nodeDialogOpen) {
        AlertDialog(
            onDismissRequest = { nodeDialogOpen = false },
            title = { Text("添加从机/PC bridge") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newNodeIp,
                        onValueChange = { newNodeIp = it },
                        label = { Text("IP 地址") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = newNodePort,
                        onValueChange = { newNodePort = it },
                        label = { Text("端口") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        LanMatrixManager.addNode(
                            context = context,
                            ipAddress = newNodeIp,
                            port = newNodePort.toIntOrNull() ?: HapticMiddlewareService.DEFAULT_PORT,
                            label = if (newNodeIp.contains("adb", ignoreCase = true)) "ADB bridge" else "PC bridge",
                        )
                        LanMatrixManager.setMode(context, MatrixMode.MASTER)
                        newNodeIp = ""
                        newNodePort = HapticMiddlewareService.DEFAULT_PORT.toString()
                        nodeDialogOpen = false
                    },
                ) {
                    Text("添加")
                }
            },
            dismissButton = {
                TextButton(onClick = { nodeDialogOpen = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun PowerConsoleCard(
    running: Boolean,
    controlsLocked: Boolean,
    localIp: String,
    port: Int,
    onPower: () -> Unit,
    onEmergencyStop: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier =
                    Modifier
                        .size(132.dp)
                        .clickable(enabled = !running || !controlsLocked, onClick = onPower),
                shape = CircleShape,
                color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 8.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = if (running) "停止服务" else "启动服务",
                        modifier = Modifier.size(58.dp),
                        tint = if (running) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = if (running) "服务运行中" else "服务未启动",
                style = MaterialTheme.typography.titleLarge,
                color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (running) "$localIp:$port" else "点击电源启动 Pulse Link",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onEmergencyStop,
                enabled = running && !controlsLocked,
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("停止所有震动")
            }
        }
    }
}

@Composable
private fun SlideUnlockControl(
    locked: Boolean,
    progress: Float,
    onProgressChange: (Float) -> Unit,
    onUnlocked: () -> Unit,
    onLock: () -> Unit,
) {
    val transition = rememberInfiniteTransition()
    val hintAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (locked) "防误触锁已开启" else "控制已解锁", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (locked) "按住滑块拖到最右侧解锁" else "测试、路由与停止按钮现在可用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (locked) hintAlpha else 1f),
                    )
                }
                if (!locked) {
                    TextButton(onClick = onLock) {
                        Text("上锁")
                    }
                }
            }
            Slider(
                value = if (locked) progress else 1f,
                onValueChange = {
                    if (locked) onProgressChange(it)
                },
                onValueChangeFinished = {
                    if (progress >= 0.92f) {
                        onUnlocked()
                    } else {
                        onProgressChange(0f)
                    }
                },
                enabled = locked,
            )
        }
    }
}

@Composable
private fun ServiceInfoPanel(
    running: Boolean,
    status: String,
    localIp: String,
    port: Int,
    error: String?,
    gamepadSummary: String,
    serviceDetail: String,
    keepScreenAwake: Boolean,
    onKeepScreenAwakeChange: (Boolean) -> Unit,
    onRefreshGamepads: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("连接信息", style = MaterialTheme.typography.titleMedium)
            Text("Ktor HTTP：$status $localIp:$port")
            Text(serviceDetail)
            Text(gamepadSummary)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("保持屏幕常亮")
                Switch(checked = keepScreenAwake, onCheckedChange = onKeepScreenAwakeChange)
            }
            OutlinedButton(onClick = onRefreshGamepads, modifier = Modifier.fillMaxWidth()) {
                Text("刷新手柄")
            }
        }
    }
}

@Composable
private fun RouteAndTestCard(
    context: Context,
    running: Boolean,
    controlsLocked: Boolean,
    selectedTarget: String,
    selectedMode: String,
    selectedLevel: Int,
    isTesting: Boolean,
    localIp: String,
    onTargetChange: (String) -> Unit,
    onStartTargetTest: (String) -> Unit,
    onStopTesting: () -> Unit,
    onAddNode: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("本机触觉与设备路由", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "主控发出同一条震动指令，从机按线路接收。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SingleChoiceSegmentedButtonRow {
                    MatrixMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = LanMatrixManager.mode == mode,
                            onClick = { LanMatrixManager.setMode(context, mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = MatrixMode.entries.size),
                            enabled = !controlsLocked,
                        ) {
                            Text(if (mode == MatrixMode.MASTER) "主控" else "从机")
                        }
                    }
                }
            }
            RouteLine(
                localIp = localIp,
                selectedTarget = selectedTarget,
                onTargetChange = onTargetChange,
                onStartTargetTest = onStartTargetTest,
                canTest = running && !controlsLocked,
            )
            if (LanMatrixManager.mode == MatrixMode.MASTER) {
                LanMatrixManager.nodes.forEach { node ->
                    NodeRow(
                        context = context,
                        node = node,
                        selectedMode = selectedMode,
                        selectedLevel = selectedLevel,
                        controlsLocked = controlsLocked,
                        onTestNode = {
                            onTargetChange("gamepad")
                            scope.launch {
                                runCatching {
                                    sendNodeVibrate(node, selectedMode, selectedLevel)
                                }.onSuccess {
                                    PulseLogger.i("从机节点测试已发送 ${node.ipAddress}:${node.port}")
                                }.onFailure {
                                    PulseLogger.w("从机节点测试失败 ${node.ipAddress}:${node.port}：${it.message}")
                                }
                            }
                        },
                    )
                }
                OutlinedButton(
                    onClick = onAddNode,
                    enabled = !controlsLocked,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("添加电脑/从机节点")
                }
            }
            if (isTesting) {
                FilledTonalButton(onClick = onStopTesting, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("停止持续测试")
                }
            }
        }
    }
}

@Composable
private fun RouteLine(
    localIp: String,
    selectedTarget: String,
    onTargetChange: (String) -> Unit,
    onStartTargetTest: (String) -> Unit,
    canTest: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RouteNode(
            title = "主控",
            subtitle = "手机 App / $localIp",
            target = "phone",
            icon = Icons.Default.PhoneAndroid,
            selected = selectedTarget == "phone",
            canTest = canTest,
            onSelect = onTargetChange,
            onTest = onStartTargetTest,
        )
        Text("↓ 通过 LAN / ADB / PC bridge 分发", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            RouteNode(
                title = "手柄",
                subtitle = "PC bridge / Android",
                target = "gamepad",
                icon = Icons.Default.SportsEsports,
                selected = selectedTarget == "gamepad",
                canTest = canTest,
                onSelect = onTargetChange,
                onTest = onStartTargetTest,
                modifier = Modifier.weight(1f),
            )
            RouteNode(
                title = "玩具",
                subtitle = "Intiface",
                target = "toy",
                icon = Icons.Default.DevicesOther,
                selected = selectedTarget == "toy",
                canTest = canTest,
                onSelect = onTargetChange,
                onTest = onStartTargetTest,
                modifier = Modifier.weight(1f),
            )
        }
        RouteNode(
            title = "全部设备",
            subtitle = "手机 + 手柄 + 玩具",
            target = "all",
            icon = Icons.Default.Vibration,
            selected = selectedTarget == "all",
            canTest = canTest,
            onSelect = onTargetChange,
            onTest = onStartTargetTest,
        )
    }
}

@Composable
private fun RouteNode(
    title: String,
    subtitle: String,
    target: String,
    icon: ImageVector,
    selected: Boolean,
    canTest: Boolean,
    onSelect: (String) -> Unit,
    onTest: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = shape,
                )
                .background(
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    shape = shape,
                )
                .clickable { onSelect(target) }
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { onTest(target) }, enabled = canTest) {
            Icon(Icons.Default.PlayArrow, contentDescription = "测试$title")
        }
    }
}

@Composable
private fun NodeRow(
    context: Context,
    node: RelayNode,
    selectedMode: String,
    selectedLevel: Int,
    controlsLocked: Boolean,
    onTestNode: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(node.label.ifBlank { "${node.ipAddress}:${node.port}" })
                Text(
                    "从机强度 ${(node.attenuation * 100).roundToInt()}% · 当前 ${HapticPatternLibrary.labelOf(selectedMode)} / $selectedLevel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = node.enabled,
                onCheckedChange = { LanMatrixManager.updateNode(context, node.copy(enabled = it)) },
                enabled = !controlsLocked,
            )
            IconButton(
                onClick = onTestNode,
                enabled = !controlsLocked,
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "测试从机")
            }
            IconButton(
                onClick = { LanMatrixManager.removeNode(context, node.id) },
                enabled = !controlsLocked,
            ) {
                Icon(Icons.Default.Delete, contentDescription = "删除节点")
            }
        }
        Slider(
            value = node.attenuation,
            onValueChange = { LanMatrixManager.updateNode(context, node.copy(attenuation = it)) },
            valueRange = 0f..1f,
            steps = 9,
            enabled = !controlsLocked,
        )
    }
}

@Composable
private fun HapticModeConsole(
    context: Context,
    selectedMode: String,
    selectedLevel: Float,
    modeFilter: String,
    randomize: Boolean,
    controlsLocked: Boolean,
    isTesting: Boolean,
    curveProgress: Float,
    customName: String,
    customCurve: String,
    onModeChange: (String) -> Unit,
    onLevelChange: (Float) -> Unit,
    onFilterChange: (String) -> Unit,
    onRandomizeChange: (Boolean) -> Unit,
    onStartTesting: () -> Unit,
    onStopTesting: () -> Unit,
    onCustomNameChange: (String) -> Unit,
    onCustomCurveChange: (String) -> Unit,
    onSaveCustom: () -> Unit,
    onCopyExport: (String) -> Unit,
    onFileExport: (String) -> Unit,
    onFileImport: () -> Unit,
) {
    val levelInt = selectedLevel.roundToInt().coerceIn(1, 5)
    val allModes = HapticPatternLibrary.modeDefinitions()
    val modes =
        when (modeFilter) {
            "favorite" -> allModes.filter { HapticPatternLibrary.isFavorite(it.id) }
            "custom" -> allModes.filter { !it.builtIn }
            "builtin" -> allModes.filter { it.builtIn && !it.deprecated }
            else -> allModes
        }
    val selectedExport = HapticPatternLibrary.exportCode(selectedMode, levelInt).orEmpty()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("震动模式", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow {
                listOf("all" to "全部", "favorite" to "收藏", "builtin" to "内置", "custom" to "自定义").forEachIndexed { index, item ->
                    SegmentedButton(
                        selected = modeFilter == item.first,
                        onClick = { onFilterChange(item.first) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 4),
                    ) {
                        Text(item.second)
                    }
                }
            }
            ModeGrid(
                modes = modes,
                selectedMode = selectedMode,
                controlsLocked = controlsLocked,
                onModeChange = onModeChange,
                onToggleFavorite = { HapticPatternLibrary.toggleFavorite(context, it) },
                onDeleteCustom = {
                    if (HapticPatternLibrary.deleteCustomPattern(context, it)) {
                        onModeChange("mode_1")
                    }
                },
            )
            Text("${HapticPatternLibrary.labelOf(selectedMode)} · 强度 $levelInt", style = MaterialTheme.typography.titleSmall)
            WaveformPreview(
                points = HapticPatternLibrary.previewPoints(selectedMode, levelInt),
                activeProgress = if (isTesting) curveProgress else 0f,
            )
            Slider(
                value = selectedLevel,
                onValueChange = onLevelChange,
                valueRange = 1f..5f,
                steps = 3,
                enabled = !controlsLocked,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = randomize,
                        onCheckedChange = onRandomizeChange,
                        enabled = !controlsLocked,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("微随机")
                }
                Button(
                    onClick = if (isTesting) onStopTesting else onStartTesting,
                    enabled = !controlsLocked,
                ) {
                    Icon(if (isTesting) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (isTesting) "停止" else "持续测试")
                }
            }
            ModeImportExportPanel(
                customName = customName,
                customCurve = customCurve,
                exportText = selectedExport,
                controlsLocked = controlsLocked,
                onCustomNameChange = onCustomNameChange,
                onCustomCurveChange = onCustomCurveChange,
                onSaveCustom = onSaveCustom,
                onCopyExport = onCopyExport,
                onFileExport = onFileExport,
                onFileImport = onFileImport,
            )
        }
    }
}

@Composable
private fun ModeGrid(
    modes: List<HapticModeDefinition>,
    selectedMode: String,
    controlsLocked: Boolean,
    onModeChange: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDeleteCustom: (String) -> Unit,
) {
    val rows = modes.chunked(2)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(292.dp)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (modes.isEmpty()) {
            Text("这里还没有模式。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { mode ->
                    ModeTile(
                        mode = mode,
                        selected = HapticPatternLibrary.idOf(selectedMode) == mode.id,
                        favorite = HapticPatternLibrary.isFavorite(mode.id),
                        controlsLocked = controlsLocked,
                        onSelect = { onModeChange(mode.id) },
                        onToggleFavorite = { onToggleFavorite(mode.id) },
                        onDelete = { onDeleteCustom(mode.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ModeTile(
    mode: HapticModeDefinition,
    selected: Boolean,
    favorite: Boolean,
    controlsLocked: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier =
            modifier
                .height(70.dp)
                .border(
                    1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape,
                )
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f) else Color.Transparent,
                    shape,
                )
                .clickable(enabled = !controlsLocked, onClick = onSelect)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(mode.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (mode.builtIn) mode.id else "自定义",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onToggleFavorite, enabled = !controlsLocked) {
            Icon(
                if (favorite) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = if (favorite) "取消收藏" else "收藏",
            )
        }
        if (!mode.builtIn) {
            IconButton(onClick = onDelete, enabled = !controlsLocked) {
                Icon(Icons.Default.Delete, contentDescription = "删除自定义模式")
            }
        }
    }
}

@Composable
private fun WaveformPreview(
    points: List<Float>,
    activeProgress: Float,
) {
    val inactive = MaterialTheme.colorScheme.outlineVariant
    val active = MaterialTheme.colorScheme.primary
    val fallback = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(92.dp)
                .background(fallback, RoundedCornerShape(10.dp))
                .padding(10.dp),
    ) {
        if (points.isEmpty()) return@Canvas
        val barWidth = size.width / points.size.coerceAtLeast(1)
        val activeIndex = ((points.size - 1) * activeProgress.coerceIn(0f, 1f)).roundToInt()
        points.forEachIndexed { index, point ->
            val height = max(5f, size.height * point.coerceIn(0f, 1f))
            drawRoundRect(
                color = if (index <= activeIndex && activeProgress > 0f) active else inactive,
                topLeft = Offset(index * barWidth + 2f, size.height - height),
                size = Size(max(3f, barWidth - 4f), height),
                cornerRadius = CornerRadius(8f, 8f),
            )
        }
    }
}

@Composable
private fun ModeImportExportPanel(
    customName: String,
    customCurve: String,
    exportText: String,
    controlsLocked: Boolean,
    onCustomNameChange: (String) -> Unit,
    onCustomCurveChange: (String) -> Unit,
    onSaveCustom: () -> Unit,
    onCopyExport: (String) -> Unit,
    onFileExport: (String) -> Unit,
    onFileImport: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = exportText,
            onValueChange = {},
            label = { Text("当前曲线数字串") },
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { onCopyExport(exportText) },
                enabled = exportText.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("复制")
            }
            OutlinedButton(
                onClick = { onFileExport(exportText) },
                enabled = exportText.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("导出文件")
            }
        }
        OutlinedButton(
            onClick = onFileImport,
            enabled = !controlsLocked,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("从文件导入曲线")
        }
        OutlinedTextField(
            value = customName,
            onValueChange = onCustomNameChange,
            label = { Text("自定义名称") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !controlsLocked,
            singleLine = true,
        )
        OutlinedTextField(
            value = customCurve,
            onValueChange = onCustomCurveChange,
            label = { Text("导入/自定义曲线数字串") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !controlsLocked,
            minLines = 2,
        )
        Button(
            onClick = onSaveCustom,
            enabled = !controlsLocked && customCurve.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("保存为自定义曲线")
        }
    }
}

@Composable
private fun MediaTransitionCard(
    displayName: String,
    startMs: String,
    controlsLocked: Boolean,
    onStartMsChange: (String) -> Unit,
    onPickFile: () -> Unit,
    onClear: () -> Unit,
    onTestPlay: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("媒体跳转", style = MaterialTheme.typography.titleMedium)
            val cover = MediaTransitionManager.coverPreview
            if (cover != null) {
                Image(
                    bitmap = cover.asImageBitmap(),
                    contentDescription = "媒体封面",
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("未导入媒体或暂无封面", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onPickFile,
                    enabled = !controlsLocked,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("导入媒体")
                }
                OutlinedButton(
                    onClick = onClear,
                    enabled = !controlsLocked && displayName.isNotBlank(),
                ) {
                    Text("清除")
                }
            }
            if (displayName.isNotBlank()) {
                Text(
                    text = displayName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            OutlinedTextField(
                value = startMs,
                onValueChange = onStartMsChange,
                label = { Text("起始位置（毫秒）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                enabled = !controlsLocked,
                singleLine = true,
            )
            Button(
                onClick = onTestPlay,
                enabled = !controlsLocked && displayName.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("测试跳转")
            }
        }
    }
}

@Composable
private fun LogCard(
    logs: List<PulseLogEntry>,
    timeFormat: SimpleDateFormat,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("运行日志", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起日志" else "展开日志",
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                if (logs.isEmpty()) {
                    Text("暂无日志", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(
                        modifier = Modifier.height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(logs) { entry ->
                            LogEntryLine(entry, timeFormat)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryLine(
    entry: PulseLogEntry,
    timeFormat: SimpleDateFormat,
) {
    val time = timeFormat.format(Date(entry.timestamp))
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "$time ${entry.level.zhLabel()}：${entry.chineseSummary}",
            style = MaterialTheme.typography.bodySmall,
            color = if (entry.level == PulseLogEntry.Level.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "原始：${entry.message}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun KeepScreenAwake(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(context, enabled) {
        val activity = context.findActivity()
        if (enabled) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private suspend fun readGamepadSummary(refresh: Boolean = false): String =
    withContext(Dispatchers.IO) {
        runCatching {
            if (refresh) readPulseEndpoint("/gamepads/refresh")
            val text = readPulseEndpoint("/status")
            parseGamepadSummary(text)
        }.getOrElse {
            "手柄：服务不可达"
        }
    }

private suspend fun sendPulseVibrate(
    mode: String,
    level: Int,
    randomize: Boolean,
    target: String,
) {
    withContext(Dispatchers.IO) {
        val query =
            "/vibrate?mode=${Uri.encode(mode)}" +
                "&level=$level" +
                "&randomize=$randomize" +
                "&target=${Uri.encode(target)}" +
                "&targets=${Uri.encode(target)}"
        readPulseEndpoint(query)
    }
}

private suspend fun sendPulseStop() {
    withContext(Dispatchers.IO) {
        runCatching { readPulseEndpoint("/stop") }
    }
}

private suspend fun sendNodeVibrate(
    node: RelayNode,
    mode: String,
    level: Int,
) {
    withContext(Dispatchers.IO) {
        val host = if (node.ipAddress.equals("adb-reverse", ignoreCase = true)) "127.0.0.1" else node.ipAddress.trim()
        val attenuatedLevel = (level * node.attenuation).roundToInt().coerceIn(1, 5)
        val url =
            URL(
                "http://$host:${node.port}/vibrate" +
                    "?mode=${Uri.encode(mode)}" +
                    "&level=$attenuatedLevel" +
                    "&randomize=false" +
                    "&target=gamepad" +
                    "&targets=gamepad",
            )
        url.openConnection().run {
            connectTimeout = 1500
            readTimeout = 3000
            getInputStream().bufferedReader().use { it.readText() }
        }
    }
}

private fun readPulseEndpoint(path: String): String {
    val url = URL("http://127.0.0.1:${HapticMiddlewareService.DEFAULT_PORT}$path")
    return url.openConnection().run {
        connectTimeout = 1500
        readTimeout = 3000
        getInputStream().bufferedReader().use { it.readText() }
    }
}

private fun parseGamepadSummary(json: String): String {
    val connected = Regex("\"gamepad_connected\"\\s*:\\s*true").containsMatchIn(json)
    val count = Regex("\"gamepad_count\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    val vibrator = Regex("\"gamepad_vibrator_available\"\\s*:\\s*true").containsMatchIn(json)
    val name = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)
    return when {
        !connected || count == 0 -> "手柄：未连接"
        vibrator -> "手柄：${name ?: "已连接 $count 个"}，震动可用"
        else -> "手柄：已连接，但 Android 未暴露震动器"
    }
}

private data class PulseServiceDetailUi(
    val intifaceConnected: Boolean,
    val gadgetbridgeOk: Boolean,
    val matrixMode: String,
    val relayNodes: Int,
    val mediaConfigured: Boolean,
    val defaultLevel: Int,
    val gamepadVibratorAvailable: Boolean,
) {
    fun displayText(): String =
        "Intiface：${if (intifaceConnected) "已连接" else "未连接/等待中"} · " +
            "Gadgetbridge：${if (gadgetbridgeOk) "可用" else "未就绪"}\n" +
            "矩阵：$matrixMode，从机 $relayNodes · 媒体：${if (mediaConfigured) "已配置" else "未配置"} · " +
            "默认强度 $defaultLevel · 手柄震动器：${if (gamepadVibratorAvailable) "可用" else "未暴露"}"

    companion object {
        fun empty(): PulseServiceDetailUi =
            PulseServiceDetailUi(
                intifaceConnected = false,
                gadgetbridgeOk = false,
                matrixMode = "未知",
                relayNodes = 0,
                mediaConfigured = false,
                defaultLevel = 3,
                gamepadVibratorAvailable = false,
            )
    }
}

private fun parseServiceDetail(json: String): PulseServiceDetailUi =
    PulseServiceDetailUi(
        intifaceConnected = jsonBool(json, "buttplug_connected"),
        gadgetbridgeOk = jsonBool(json, "gadgetbridge_ok"),
        matrixMode = jsonString(json, "matrix_mode").ifBlank { "未知" },
        relayNodes = jsonInt(json, "relay_nodes", 0),
        mediaConfigured = jsonBool(json, "media_configured"),
        defaultLevel = jsonInt(json, "current_default_level", 3).coerceIn(1, 5),
        gamepadVibratorAvailable = jsonBool(json, "gamepad_vibrator_available"),
    )

private fun jsonBool(
    json: String,
    key: String,
): Boolean = Regex("\"$key\"\\s*:\\s*true").containsMatchIn(json)

private fun jsonInt(
    json: String,
    key: String,
    fallback: Int,
): Int =
    Regex("\"$key\"\\s*:\\s*(\\d+)")
        .find(json)
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull() ?: fallback

private fun jsonString(
    json: String,
    key: String,
): String =
    Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"")
        .find(json)
        ?.groupValues
        ?.get(1)
        .orEmpty()

private fun inferCurveLabel(text: String): String {
    val label = text.substringBefore("|", missingDelimiterValue = "").trim()
    val hasName = label.isNotBlank() && label.any { !it.isDigit() && it != ',' && !it.isWhitespace() }
    return if (hasName) label.take(24) else ""
}

private fun PulseLogEntry.Level.zhLabel(): String =
    when (this) {
        PulseLogEntry.Level.DEBUG -> "调试"
        PulseLogEntry.Level.INFO -> "信息"
        PulseLogEntry.Level.WARN -> "警告"
        PulseLogEntry.Level.ERROR -> "错误"
    }
