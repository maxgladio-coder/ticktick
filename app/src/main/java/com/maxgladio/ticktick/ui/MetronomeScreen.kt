package com.maxgladio.ticktick.ui

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.maxgladio.ticktick.core.ClickSound
import com.maxgladio.ticktick.core.MetronomeConfig
import com.maxgladio.ticktick.core.PlaybackState
import com.maxgladio.ticktick.core.TapTempoEstimator
import com.maxgladio.ticktick.update.UpdateState
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun MetronomeScreen(
    config: MetronomeConfig,
    playback: PlaybackState,
    estimate: TapTempoEstimator.Estimate,
    lastTap: Long,
    onConfig: (MetronomeConfig) -> Unit,
    onToggle: () -> Unit,
    onTap: () -> Unit,
    onResetTap: () -> Unit,
    updateState: UpdateState = UpdateState.Idle,
    onCheckUpdate: () -> Unit = {},
    onOpenUpdate: (String) -> Unit = {},
) {
    var settings by rememberSaveable { mutableStateOf(false) }
    var editTempo by rememberSaveable { mutableStateOf(false) }
    var meter by rememberSaveable { mutableStateOf(false) }
    var subdivision by rememberSaveable { mutableStateOf(false) }
    val view = LocalView.current
    val fontScale = LocalDensity.current.fontScale
    val latestQuickTap by rememberUpdatedState(onTap)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(playback.serial) {
        if (config.haptics && playback.running && playback.beat >= 0 && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    Scaffold(
        containerColor = Paper,
        bottomBar = {
            Surface(color = Paper, shadowElevation = 8.dp) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding(), contentAlignment = Alignment.Center) {
                    BoxWithConstraints(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                        val compact = maxWidth < 300.dp || fontScale > 1.2f
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = onToggle, modifier = Modifier.weight(1f).height(64.dp).semantics {
                                contentDescription = if (playback.running) "停止节拍" else "开始节拍"
                            }, contentPadding = PaddingValues(horizontal = 12.dp),
                                shape = RoundedCornerShape(22.dp), colors = ButtonDefaults.buttonColors(containerColor = Olive)) {
                                TransportIcon(playback.running)
                                Spacer(Modifier.width(10.dp))
                                Text(if (playback.running) { if (compact) "停止" else "停止节拍" } else { if (compact) "开始" else "开始节拍" },
                                    fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                            Surface(color = Lime, shape = RoundedCornerShape(22.dp), modifier = Modifier.width(96.dp).height(64.dp)
                                .pointerInput(Unit) { detectTapGestures(onPress = { latestQuickTap(); tryAwaitRelease() }) }
                                .semantics(mergeDescendants = true) {
                                    role = Role.Button
                                    contentDescription = "快捷敲击测速"
                                    onClick(label = "敲击") { latestQuickTap(); true }
                                }) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text("TAP", fontSize = 17.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                                    Text(estimate.bpm?.let { "${it.roundToInt()} BPM" } ?: "敲击测速", fontSize = 10.sp, color = Olive, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(top = 10.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.size(40.dp).background(Olive, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                        Logo(Modifier.size(25.dp))
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text("律动", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("T I C K T I C K", fontSize = 9.sp, color = Muted, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = { settings = true }, modifier = Modifier.border(1.dp, Line, CircleShape)) {
                        SettingsIcon()
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(Modifier.size(6.dp).background(if (playback.running) Olive else Muted, CircleShape))
                        Text(if (playback.running) "跟随节拍，保持专注" else "找到你的节奏", color = Muted, fontSize = 12.sp)
                    }
                    TempoDial(config.bpm, playback.running, playback.beat, playback.serial, onClick = { editTempo = true })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        StepButton("−5", "降低 5 BPM", enabled = config.bpm > 30, modifier = Modifier.weight(1f)) { onConfig(config.copy(bpm = config.bpm - 5)) }
                        StepButton("−", "降低 1 BPM", enabled = config.bpm > 30, modifier = Modifier.weight(1f), prominent = true) { onConfig(config.copy(bpm = config.bpm - 1)) }
                        StepButton("+", "提高 1 BPM", enabled = config.bpm < 300, modifier = Modifier.weight(1f), prominent = true) { onConfig(config.copy(bpm = config.bpm + 1)) }
                        StepButton("+5", "提高 5 BPM", enabled = config.bpm < 300, modifier = Modifier.weight(1f)) { onConfig(config.copy(bpm = config.bpm + 5)) }
                    }
                    Slider(value = config.bpm.toFloat(), onValueChange = { onConfig(config.copy(bpm = it.roundToInt())) },
                        valueRange = 30f..300f, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "速度，BPM" })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("30", fontSize = 10.sp, color = Muted)
                        Text("滑动调速 · 点数字输入", fontSize = 10.sp, color = Muted)
                        Text("300", fontSize = 10.sp, color = Muted)
                    }
                }

                Surface(shape = RoundedCornerShape(24.dp), color = Card) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.Center) {
                            Text("节奏", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.align(Alignment.CenterVertically))
                            TextButton(onClick = { meter = true }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                                Text("${config.beatsPerBar}/${config.beatUnit} 拍  ⌄", fontWeight = FontWeight.SemiBold)
                            }
                            TextButton(onClick = { subdivision = true }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                                Text(if (config.subdivision == 1) "无细分  ⌄" else "${config.subdivision} 等分  ⌄")
                            }
                        }
                        BeatLights(config, playback)
                        Text("${if (config.beatUnit == 4) "四分" else "八分"}音符 = 1 拍" + if (config.accent) " · 首拍重音" else " · 均匀节拍",
                            fontSize = 11.sp, color = Muted)
                    }
                }

                TapCard(estimate, lastTap, onTap, onResetTap, onApply = { bpm -> onConfig(config.copy(bpm = bpm)) })

                playback.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                Text("让练习，回到节奏本身。", color = Muted, fontSize = 11.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (editTempo) TempoDialog(config.bpm, onDismiss = { editTempo = false }) {
        onConfig(config.copy(bpm = it)); editTempo = false
    }
    if (settings) SettingsSheet(config, onConfig, updateState, onCheckUpdate, onOpenUpdate) { settings = false }
    if (meter) MeterDialog(config, onConfig) { meter = false }
    if (subdivision) AlertDialog(
        onDismissRequest = { subdivision = false }, title = { Text("细分节拍") },
        text = {
            Column {
                listOf("每拍一下", "二等分 · 八分节奏", "三等分 · 三连音", "四等分 · 十六分节奏").forEachIndexed { index, label ->
                    Row(Modifier.fillMaxWidth().clickable { onConfig(config.copy(subdivision = index + 1)); subdivision = false }
                        .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = config.subdivision == index + 1,
                            onClick = { onConfig(config.copy(subdivision = index + 1)); subdivision = false })
                        Text(label)
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { subdivision = false }) { Text("完成") } },
    )
}

@Composable
private fun TempoDial(bpm: Int, running: Boolean, beat: Int, serial: Long, onClick: () -> Unit) {
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(serial, running) {
        if (running && beat >= 0) { pulse.snapTo(1f); pulse.animateTo(0f, tween(180)) }
        else pulse.snapTo(0f)
    }
    Box(Modifier.fillMaxWidth().height(218.dp).clickable(role = Role.Button, onClickLabel = "输入速度", onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = "$bpm BPM，点击输入速度" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(216.dp)) {
            val radius = size.minDimension / 2 - 10.dp.toPx()
            for (i in 0..48) {
                val angle = (135.0 + i * 270.0 / 48) * PI / 180
                val highlighted = i <= (bpm - 30) / 270f * 48
                val length = if (i % 4 == 0) 10.dp.toPx() else 5.dp.toPx()
                drawLine(if (highlighted) Olive else Line,
                    Offset(center.x + cos(angle).toFloat() * radius, center.y + sin(angle).toFloat() * radius),
                    Offset(center.x + cos(angle).toFloat() * (radius - length), center.y + sin(angle).toFloat() * (radius - length)),
                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
            if (pulse.value > 0f) drawCircle(Lime.copy(alpha = pulse.value * 0.4f), radius = radius - 20.dp.toPx())
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("B E A T S  /  M I N", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text("$bpm", fontSize = 76.sp, lineHeight = 88.sp, fontWeight = FontWeight.Light,
                fontFamily = FontFamily.SansSerif, letterSpacing = (-3).sp, color = Ink)
            Text(tempoName(bpm), color = Olive, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

private fun tempoName(bpm: Int) = when {
    bpm < 60 -> "Largo · 广板"
    bpm < 76 -> "Adagio · 柔板"
    bpm < 108 -> "Andante · 行板"
    bpm < 120 -> "Moderato · 中板"
    bpm < 168 -> "Allegro · 快板"
    bpm < 200 -> "Presto · 急板"
    else -> "Prestissimo · 最急板"
}

@Composable
private fun StepButton(label: String, description: String, enabled: Boolean, modifier: Modifier, prominent: Boolean = false, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = modifier.height(48.dp).semantics { contentDescription = description },
        shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = if (prominent) Line else Card, contentColor = Ink)) {
        Text(label, fontSize = if (prominent) 24.sp else 15.sp)
    }
}

@Composable
private fun BeatLights(config: MetronomeConfig, playback: PlaybackState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (0 until config.beatsPerBar).chunked(6).forEach { indices ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                indices.forEach { index ->
                    val selected = playback.running && playback.beat == index
                    val background by animateColorAsState(if (selected) Olive else if (index == 0 && config.accent) Lime else Paper,
                        animationSpec = tween(65), label = "beat")
                    Box(Modifier.weight(1f).height(37.dp).background(background, RoundedCornerShape(10.dp))
                        .semantics { contentDescription = "第 ${index + 1} 拍"; this.selected = selected }, contentAlignment = Alignment.Center) {
                        Text("${index + 1}", color = if (selected) Color.White else Olive, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TapCard(estimate: TapTempoEstimator.Estimate, lastTap: Long, onTap: () -> Unit, onReset: () -> Unit, onApply: (Int) -> Unit) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var applied by remember { mutableStateOf(false) }
    val tapScale = remember { Animatable(1f) }
    val latestTap by rememberUpdatedState(onTap)
    LaunchedEffect(lastTap) {
        applied = false
        if (lastTap > 0) { tapScale.snapTo(0.98f); tapScale.animateTo(1f, tween(120)) }
    }
    LaunchedEffect(lastTap) {
        now = SystemClock.elapsedRealtime()
        while (lastTap > 0 && now - lastTap <= TapTempoEstimator.RESET_GAP_MS) {
            delay(250); now = SystemClock.elapsedRealtime()
        }
    }
    val idle = lastTap > 0 && now - lastTap > TapTempoEstimator.RESET_GAP_MS
    Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFFECF0E3)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("敲击测速", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("跟着音乐，轻敲下方区域", fontSize = 11.sp, color = Muted)
                }
                TextButton(onClick = onReset, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("重置", fontSize = 12.sp) }
            }
            Surface(color = Olive, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().height(96.dp)
                .scale(tapScale.value).pointerInput(Unit) {
                    detectTapGestures(onPress = { latestTap(); tryAwaitRelease() })
                }.semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = "敲击测速，跟随节奏连续敲击至少四次"
                    onClick(label = "敲击") { latestTap(); true }
                }) {
                Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("TAP", color = Lime, fontSize = 26.sp, fontWeight = FontWeight.Medium, letterSpacing = 3.sp)
                        Text("轻触，捕捉节奏", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                    }
                    TapWaves()
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(estimate.bpm?.roundToInt()?.toString() ?: "—",
                            fontSize = 27.sp, fontWeight = FontWeight.Medium, color = Ink)
                        Text("BPM", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 5.dp))
                    }
                    Text(when {
                        lastTap == 0L -> "至少敲击 4 次开始估算"
                        idle -> "已暂停 · 再敲开始新的测速"
                        estimate.changedTempo -> "正在跟随新的速度"
                        estimate.bpm == null -> "继续敲击 · 已记录 ${estimate.intervals + 1} 次"
                        else -> "最近 ${estimate.intervals + 1} 次 · ${if (estimate.stability >= 75) "稳定" else "滚动测速中"}"
                    }, fontSize = 10.sp, color = Muted)
                }
                FilledTonalButton(onClick = { estimate.bpm?.let { onApply(it.roundToInt()); applied = true } },
                    enabled = estimate.bpm != null, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 14.dp)) {
                    Text(if (applied) "已应用 ✓" else "应用速度", fontSize = 12.sp)
                }
            }
            if (estimate.recentBpm.isNotEmpty()) {
                TapHistory(estimate)
            }
            Text("持续敲击，滚动计算最近 12 秒的速度", fontSize = 10.sp, color = Muted)
        }
    }
}

@Composable
private fun TapHistory(estimate: TapTempoEstimator.Estimate) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Canvas(Modifier.fillMaxWidth().height(24.dp).semantics { contentDescription = "敲击稳定度 ${estimate.stability}，满分 100" }) {
            val reference = estimate.bpm ?: estimate.recentBpm.average()
            val step = size.width / 16
            estimate.recentBpm.forEachIndexed { index, value ->
                val height = (0.5f + ((value / reference - 1) * 1.8).toFloat()).coerceIn(0.12f, 1f) * size.height
                drawLine(Olive.copy(alpha = 0.25f + 0.75f * (index + 1) / estimate.recentBpm.size),
                    Offset(step * index + step / 2, size.height), Offset(step * index + step / 2, size.height - height),
                    strokeWidth = step * 0.48f, cap = StrokeCap.Round)
            }
        }
        Text("稳定度 ${estimate.stability}%", fontSize = 10.sp, color = Muted)
    }
}

@Composable
private fun TempoDialog(bpm: Int, onDismiss: () -> Unit, onApply: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf("$bpm") }
    val value = text.toIntOrNull()
    val valid = value != null && value in 30..300
    AlertDialog(onDismissRequest = onDismiss, title = { Text("设置速度") }, text = {
        OutlinedTextField(value = text, onValueChange = { if (it.length <= 3 && it.all(Char::isDigit)) text = it },
            label = { Text("BPM") }, supportingText = { Text("输入 30–300 之间的整数") },
            isError = text.isNotEmpty() && !valid, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (valid) onApply(value!!) }))
    }, confirmButton = { TextButton(onClick = { onApply(value!!) }, enabled = valid) { Text("应用") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun MeterDialog(config: MetronomeConfig, onConfig: (MetronomeConfig) -> Unit, onDismiss: () -> Unit) {
    var beats by rememberSaveable { mutableIntStateOf(config.beatsPerBar) }
    var unit by rememberSaveable { mutableIntStateOf(config.beatUnit) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("拍号") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("每小节拍数", fontSize = 13.sp, color = Muted)
            (1..12).chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { value ->
                        FilterChip(selected = beats == value, onClick = { beats = value }, label = { Text("$value") },
                            modifier = Modifier.weight(1f))
                    }
                }
            }
            Text("以什么音符为一拍", fontSize = 13.sp, color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(selected = unit == 4, onClick = { unit = 4 }, label = { Text("四分音符") })
                FilterChip(selected = unit == 8, onClick = { unit = 8 }, label = { Text("八分音符") })
            }
            Text("BPM 始终表示每分钟的主拍数；6/8 为每小节 6 个八分音符主拍。", fontSize = 12.sp, color = Muted)
        }
    }, confirmButton = { TextButton(onClick = { onConfig(config.copy(beatsPerBar = beats, beatUnit = unit)); onDismiss() }) { Text("应用") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(config: MetronomeConfig, onConfig: (MetronomeConfig) -> Unit,
    updateState: UpdateState, onCheckUpdate: () -> Unit, onOpenUpdate: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Paper,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("声音与反馈", fontSize = 23.sp, fontWeight = FontWeight.Bold)
            SettingToggle("首拍重音", "用更清晰的声音标记每小节开始", config.accent) { onConfig(config.copy(accent = it)) }
            SettingToggle("节拍触感", "在应用前台时提供轻微触感", config.haptics) { onConfig(config.copy(haptics = it)) }
            HorizontalDivider(color = Line)
            Text("音色", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ClickSound.entries.forEach { sound ->
                    FilterChip(selected = config.sound == sound, onClick = { onConfig(config.copy(sound = sound)) }, label = { Text(sound.label) })
                }
            }
            Row {
                Text("节拍音量", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text("${(config.volume * 100).roundToInt()}%", color = Muted)
            }
            Slider(value = config.volume, onValueChange = { onConfig(config.copy(volume = it)) },
                modifier = Modifier.semantics { contentDescription = "节拍音量" })
            Text("手机音量键可调节媒体音量。播放时支持锁屏和后台运行，通知栏可停止。", fontSize = 12.sp, color = Muted)
            HorizontalDivider(color = Line)
            Text("关于敲击测速", fontWeight = FontWeight.SemiBold)
            Text("每次敲击都用最近 12 秒内最多 40 次敲击（39 个间隔）重新测速，旧数据自动移出，持续敲击不会停止。建议先敲击 8–12 次；稳定度表示敲击一致性。停顿超过 4 秒后，再次敲击会重新计算。",
                fontSize = 12.sp, lineHeight = 20.sp, color = Muted)
            HorizontalDivider(color = Line)
            UpdateCard(updateState, onCheckUpdate, onOpenUpdate)
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("完成") }
        }
    }
}

@Composable
private fun SettingToggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 11.sp, color = Muted)
        }
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
private fun TransportIcon(running: Boolean) {
    Canvas(Modifier.size(20.dp)) {
        if (running) drawRoundRect(Color.White, topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(size.width - 4.dp.toPx(), size.height - 4.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        else drawPath(Path().apply { moveTo(size.width * 0.2f, 0f); lineTo(size.width, size.height / 2); lineTo(size.width * 0.2f, size.height); close() }, Color.White)
    }
}

@Composable
private fun Logo(modifier: Modifier) {
    Canvas(modifier) {
        drawPath(Path().apply { moveTo(size.width * .38f, size.height * .1f); lineTo(size.width * .6f, size.height * .1f)
            lineTo(size.width * .86f, size.height * .9f); lineTo(size.width * .15f, size.height * .9f); close() }, Paper)
        drawLine(Olive, Offset(size.width * .48f, size.height * .76f), Offset(size.width * .81f, size.height * .13f), 2.dp.toPx(), StrokeCap.Round)
        drawCircle(Lime, 2.5.dp.toPx(), Offset(size.width * .81f, size.height * .13f))
    }
}

@Composable
private fun SettingsIcon() {
    Canvas(Modifier.size(22.dp).semantics { contentDescription = "声音与反馈设置" }) {
        listOf(.22f, .5f, .78f).forEachIndexed { index, x ->
            drawLine(Ink, Offset(size.width * x, 0f), Offset(size.width * x, size.height), 1.5.dp.toPx(), StrokeCap.Round)
            drawCircle(Paper, 3.dp.toPx(), Offset(size.width * x, size.height * if (index == 1) .68f else .32f))
            drawCircle(Ink, 2.dp.toPx(), Offset(size.width * x, size.height * if (index == 1) .68f else .32f))
        }
    }
}

@Composable
private fun TapWaves() {
    Canvas(Modifier.size(width = 70.dp, height = 34.dp)) {
        listOf(.25f, .55f, .9f, .6f, .35f, .7f, .4f).forEachIndexed { index, factor ->
            val x = (index + .5f) * size.width / 7
            drawLine(Lime, Offset(x, size.height * (1 - factor) / 2), Offset(x, size.height * (1 + factor) / 2),
                3.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, locale = "zh")
@Composable
private fun MetronomePreview() {
    TickTickTheme {
        MetronomeScreen(MetronomeConfig(), PlaybackState(), TapTempoEstimator.Estimate(), 0L, {}, {}, {}, {})
    }
}
