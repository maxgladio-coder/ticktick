package com.maxgladio.ticktick.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxgladio.ticktick.update.installedVersion
import com.maxgladio.ticktick.update.GitHubUpdates
import com.maxgladio.ticktick.update.UpdateState

@Composable
fun UpdateCard(state: UpdateState, onCheck: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val version = remember(context) { installedVersion(context) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("应用更新", fontWeight = FontWeight.SemiBold)
        Text("当前版本 $version", fontSize = 12.sp, color = Muted)
        when (state) {
            UpdateState.Idle -> Text("从 GitHub 检查正式版本；仅在点击时联网。", fontSize = 12.sp, color = Muted)
            UpdateState.Checking -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("正在检查更新…", fontSize = 12.sp)
            }
            UpdateState.Current -> Text("已是最新版本", color = Olive)
            is UpdateState.Error -> Text(state.message, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            is UpdateState.Available -> {
                Text("发现新版本 ${state.release.tag}", fontWeight = FontWeight.Bold, color = Olive)
                Text(state.release.notes.ifBlank { "此版本未提供更新说明。" }, fontSize = 12.sp, lineHeight = 20.sp)
                if (state.release.apkUrl != null) {
                    Button(onClick = { onOpen(state.release.apkUrl) }, modifier = Modifier.fillMaxWidth()) { Text("下载新版 APK") }
                    Text("浏览器下载完成后，打开 APK 确认安装。", fontSize = 12.sp, color = Muted)
                } else Text("此版本暂无唯一可下载的 APK，请到发布页面查看。", fontSize = 12.sp, color = Muted)
            }
        }
        OutlinedButton(onClick = onCheck, enabled = state != UpdateState.Checking, modifier = Modifier.fillMaxWidth()) {
            Text(if (state == UpdateState.Idle) "检查更新" else "重新检查")
        }
        TextButton(onClick = { onOpen((state as? UpdateState.Available)?.release?.pageUrl ?: GitHubUpdates.RELEASES_URL) }) {
            Text("查看 GitHub 发布页面")
        }
    }
}
