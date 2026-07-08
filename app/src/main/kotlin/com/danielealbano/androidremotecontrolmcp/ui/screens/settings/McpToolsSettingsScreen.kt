@file:Suppress("FunctionNaming", "LongMethod")

package com.danielealbano.androidremotecontrolmcp.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.data.model.ServerStatus
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.MainViewModel

private data class ParamEntry(
    val paramName: String,
    val displayName: String,
)

private data class ToolEntry(
    val toolName: String,
    val displayName: String,
    val params: List<ParamEntry> = emptyList(),
)

private data class ToolCategory(
    val header: String,
    val tools: List<ToolEntry>,
)

private val ALL_TOOL_CATEGORIES: List<ToolCategory> =
    listOf(
        ToolCategory(
            "屏幕读取",
            listOf(
                ToolEntry(
                    "get_screen_state",
                    "读取当前屏幕",
                    listOf(ParamEntry("include_screenshot", "同时截屏")),
                ),
            ),
        ),
        ToolCategory(
            "系统按键",
            listOf(
                ToolEntry("press_back", "返回键"),
                ToolEntry("press_home", "回到桌面"),
                ToolEntry("press_recents", "打开最近任务"),
                ToolEntry("open_notifications", "下拉通知栏"),
                ToolEntry("open_quick_settings", "打开快捷设置"),
                ToolEntry("dismiss_keyboard", "收起键盘"),
                ToolEntry("get_device_logs", "读取设备日志"),
            ),
        ),
        ToolCategory(
            "触控操作",
            listOf(
                ToolEntry("tap", "点按坐标"),
                ToolEntry("long_press", "长按坐标"),
                ToolEntry("double_tap", "双击坐标"),
                ToolEntry("swipe", "滑动屏幕"),
                ToolEntry("scroll", "滚动页面"),
            ),
        ),
        ToolCategory(
            "复杂手势",
            listOf(
                ToolEntry("pinch", "双指缩放"),
                ToolEntry("custom_gesture", "自定义手势"),
            ),
        ),
        ToolCategory(
            "界面元素",
            listOf(
                ToolEntry("find_nodes", "查找界面元素"),
                ToolEntry("click_node", "点击界面元素"),
                ToolEntry("long_click_node", "长按界面元素"),
                ToolEntry("tap_node", "点按元素位置"),
                ToolEntry("scroll_to_node", "滚动到指定元素"),
            ),
        ),
        ToolCategory(
            "文本输入",
            listOf(
                ToolEntry("type_append_text", "在末尾追加文字"),
                ToolEntry("type_insert_text", "在光标处插入文字"),
                ToolEntry("type_replace_text", "替换输入框文字"),
                ToolEntry("type_clear_text", "清空输入框文字"),
                ToolEntry("press_key", "发送键盘按键"),
            ),
        ),
        ToolCategory(
            "辅助能力",
            listOf(
                ToolEntry("get_clipboard", "读取剪贴板"),
                ToolEntry("set_clipboard", "写入剪贴板"),
                ToolEntry("wait_for_node", "等待界面元素出现"),
                ToolEntry("wait_for_idle", "等待界面稳定"),
                ToolEntry("get_node_details", "查看元素详情"),
            ),
        ),
        ToolCategory(
            "文件操作",
            listOf(
                ToolEntry("list_storage_locations", "查看已授权目录"),
                ToolEntry("list_files", "列出文件"),
                ToolEntry("read_file", "读取文件"),
                ToolEntry("write_file", "写入文件"),
                ToolEntry("append_file", "追加写入文件"),
                ToolEntry("file_replace", "查找并替换文件内容"),
                ToolEntry("download_from_url", "从链接下载文件"),
                ToolEntry("delete_file", "删除文件"),
            ),
        ),
        ToolCategory(
            "应用管理",
            listOf(
                ToolEntry("open_app", "打开应用"),
                ToolEntry("list_apps", "查看已安装应用"),
                ToolEntry("close_app", "关闭应用"),
            ),
        ),
        ToolCategory(
            "相机",
            listOf(
                ToolEntry("list_cameras", "查看可用摄像头"),
                ToolEntry("list_camera_photo_resolutions", "查看拍照分辨率"),
                ToolEntry("list_camera_video_resolutions", "查看录像分辨率"),
                ToolEntry("take_camera_photo", "拍照"),
                ToolEntry("save_camera_photo", "保存照片"),
                ToolEntry(
                    "save_camera_video",
                    "录制并保存视频",
                    listOf(ParamEntry("audio", "同时录音")),
                ),
            ),
        ),
        ToolCategory(
            "Intent 调用",
            listOf(
                ToolEntry("send_intent", "发送 Intent"),
                ToolEntry("open_uri", "打开链接或 URI"),
            ),
        ),
        ToolCategory(
            "通知",
            listOf(
                ToolEntry("notification_list", "读取通知列表"),
                ToolEntry("notification_open", "打开通知"),
                ToolEntry("notification_dismiss", "清除通知"),
                ToolEntry("notification_snooze", "稍后提醒通知"),
                ToolEntry("notification_action", "触发通知按钮"),
                ToolEntry("notification_reply", "回复通知"),
            ),
        ),
        ToolCategory(
            "位置",
            listOf(
                ToolEntry(
                    "get_location",
                    "读取当前位置",
                    listOf(ParamEntry("fresh_fix", "允许重新定位")),
                ),
            ),
        ),
        ToolCategory(
            "分享",
            listOf(
                ToolEntry("get_shared_content", "读取分享给本应用的内容"),
                ToolEntry("share_file_via_web", "通过网页分享文件"),
            ),
        ),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpToolsSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val serverStatus by viewModel.serverStatus.collectAsStateWithLifecycle()
    val perms by viewModel.toolPermissionsConfig.collectAsStateWithLifecycle()
    val isEnabled = serverStatus !is ServerStatus.Running && serverStatus !is ServerStatus.Starting

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_mcp_tools_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            },
            windowInsets = WindowInsets(0),
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Text(
                    text = stringResource(R.string.mcp_tools_restart_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            ALL_TOOL_CATEGORIES.forEach { category ->
                item(key = "header_${category.header}") {
                    Text(
                        text = category.header,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(category.tools, key = { it.toolName }) { tool ->
                    val toolEnabled = perms.isToolEnabled(tool.toolName)
                    ListItem(
                        headlineContent = { Text(tool.displayName) },
                        trailingContent = {
                            Switch(
                                checked = toolEnabled,
                                onCheckedChange = { viewModel.updateToolEnabled(tool.toolName, it) },
                                enabled = isEnabled,
                            )
                        },
                    )
                    if (toolEnabled) {
                        tool.params.forEach { param ->
                            ListItem(
                                headlineContent = { Text(param.displayName) },
                                modifier = Modifier.padding(start = 32.dp),
                                trailingContent = {
                                    Switch(
                                        checked = perms.isParamEnabled(tool.toolName, param.paramName),
                                        onCheckedChange = {
                                            viewModel.updateParamEnabled(tool.toolName, param.paramName, it)
                                        },
                                        enabled = isEnabled,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
