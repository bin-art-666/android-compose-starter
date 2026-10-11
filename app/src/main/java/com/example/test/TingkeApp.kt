package com.example.test

import android.content.ComponentName
import android.content.Intent
import android.accessibilityservice.AccessibilityServiceInfo
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val ink = Color(0xFF233B36)
private val muted = Color(0xFF6D7C75)
private val green = Color(0xFF267360)
private val paper = Color(0xFFF7F8F3)
private val soft = Color(0xFFE7EEE3)
private data class Intervention(val name: String, val mark: String, val subtitle: String, val description: String, val parameter: String, val unit: String, val min: Int, val max: Int, val initial: Int, val color: Color, val tint: Color)
private val modes = listOf(
    Intervention("目标模式", "◎", "看见时间，自主停下", "用轻盈的进度环，提醒你与目标的距离。", "本次使用目标", "分钟", 5, 60, 15, green, soft),
    Intervention("提醒模式", "◉", "停一停，再决定", "定时暂停一下，给继续观看一个明确选择。", "提醒间隔", "分钟", 3, 30, 10, Color(0xFFAC7540), Color(0xFFF6EAD9)),
    Intervention("动作模式", "↟", "多一个动作，少一点惯性", "切换前完成长按，让每次滑动更有意识。", "长按时长", "秒", 1, 5, 2, Color(0xFF687DA2), Color(0xFFE8EDF5)),
    Intervention("慢速模式", "⋮", "放慢节奏，留一点空隙", "切换前留出短暂等待，缓和连续刷动的节奏。", "切换等待", "秒", 1, 8, 3, Color(0xFF8B7396), Color(0xFFEEE8F2))
)

@Composable fun TingkeApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("tingke", 0) }
    var page by rememberSaveable { mutableStateOf("home") }
    var mode by rememberSaveable { mutableIntStateOf(prefs.getInt("mode", 0).coerceIn(0, 3)) }
    var enabled by rememberSaveable { mutableStateOf(prefs.getBoolean("enabled", false)) }
    val values = remember { mutableStateListOf(*modes.mapIndexed { i, m -> prefs.getInt("value$i", m.initial).coerceIn(m.min, m.max) }.toTypedArray()) }
    var frequency by rememberSaveable { mutableIntStateOf(prefs.getInt("frequency", 1)) }
    var goalOpacity by rememberSaveable { mutableIntStateOf(prefs.getInt("goal_opacity",100).coerceIn(20,100)) }
    val apps = remember { mutableStateListOf(prefs.getBoolean("app0", true)) }
    var permission by remember { mutableStateOf(false) }
    var todayMs by remember { mutableLongStateOf(0) }
    var reminders by remember { mutableIntStateOf(0) }
    var ended by remember { mutableIntStateOf(0) }
    var explainPermission by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    val installed = remember { mutableStateListOf(false) }
    LaunchedEffect(context) {
        (context as androidx.activity.ComponentActivity).lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val manager = context.getSystemService(AccessibilityManager::class.java)
                permission = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
                    ComponentName(it.resolveInfo.serviceInfo.packageName, it.resolveInfo.serviceInfo.name) == ComponentName(context, TargetAccessibilityService::class.java)
                }
                todayMs = UsageTracker(prefs).today()
                reminders = UsageTracker(prefs).countToday("reminder")
                ended = UsageTracker(prefs).countToday("ended")
                enabled = prefs.getBoolean("enabled", false)
                TargetAccessibilityService.TARGETS.forEachIndexed { i, name -> installed[i] = runCatching { context.packageManager.getApplicationInfo(name, 0) }.isSuccess }
                delay(1000)
            }
        }
    }
    fun save() { prefs.edit().putInt("mode", mode).putBoolean("enabled", enabled).putInt("frequency", frequency).also { editor -> values.forEachIndexed { i, v -> editor.putInt("value$i", v) }; apps.forEachIndexed { i, v -> editor.putBoolean("app$i", v) } }.apply() }
    val current = modes[mode]
    fun activate() {
        when {
            mode in 2..3 && (!apps[0] || apps.drop(1).any { it }) -> { notice = "${current.name}目前仅支持抖音，请只选择抖音。"; page = "apps" }
            apps.indices.none { apps[it] && installed[it] } -> { notice = "请先安装并选择一个受干预应用。"; page = "apps" }
            !permission -> { page = "apps"; explainPermission = true }
            else -> { if(!enabled) prefs.edit().also { editor -> TargetAccessibilityService.TARGETS.forEach { editor.remove("session_$it"); editor.remove("pending_$it") } }.apply(); enabled = true; save(); notice = "${current.name}已开启，进入选中的应用后自动计时。"; page = "home" }
        }
    }
    BackHandler(page != "home") { page = if (page == "preview") "settings" else "home" }
    MaterialTheme(colorScheme = lightColorScheme(primary = green, background = paper, surface = Color.White, onSurface = ink, onBackground = ink)) {
        if (explainPermission) AlertDialog(onDismissRequest = { explainPermission = false }, title = { Text("开启使用干预") }, text = { Text("视界有度使用无障碍服务识别所选应用、计时并显示进度或提醒。提醒模式中，选择结束会返回桌面。动作和慢速模式读取页面结构识别抖音推荐页，拦截视频区域滑动，在长按完成或等待结束后代为执行一次切换。只保存使用时长和提醒统计，不保存页面文字、视频内容或输入信息。\n\n点击前往设置后，请找到「视界有度」并手动开启。你可随时关闭服务。") }, confirmButton = { TextButton(onClick = { explainPermission = false; context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("前往设置") } }, dismissButton = { TextButton(onClick = { explainPermission = false }) { Text("暂不开启") } })
        Scaffold(containerColor = paper, bottomBar = {
            NavigationBar(containerColor = paper) {
                listOf(Triple("home", R.drawable.ic_nav_today, "今日"), Triple("modes", R.drawable.ic_nav_intervention, "干预"), Triple("apps", R.drawable.ic_nav_apps, "应用")).forEach { (route, icon, label) -> NavigationBarItem(selected = page == route || route == "modes" && page in listOf("settings", "preview"), onClick = { page = route }, icon = { Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp)) }, label = { Text(label) }, colors = NavigationBarItemDefaults.colors(indicatorColor = soft, selectedIconColor = green, selectedTextColor = green)) }
            }
        }) { inset ->
            Column(Modifier.fillMaxSize().padding(inset).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 26.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.app_logo),contentDescription="视界有度 Logo",modifier=Modifier.size(48.dp))
                        Spacer(Modifier.width(10.dp))
                        Column { Text("视界有度",fontSize=24.sp,fontWeight=FontWeight.SemiBold,color=ink); Caption("把注意力，还给自己。") }
                    }
                    TextButton(onClick = { page = "apps" }) { Text("管理") }
                }
                if (notice.isNotEmpty()) { Caption(notice, Modifier.padding(bottom = 14.dp)) }
                when(page) {
                    "home" -> {
                        Surface(color = soft, shape = RoundedCornerShape(26.dp)) {
                            Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Caption("今日 · 使用时间"); Text(if(enabled && permission && mode <= 3) "干预已开启" else "干预未运行", fontSize = 12.sp, color = green) }
                                Box(Modifier.size(174.dp).padding(12.dp), contentAlignment = Alignment.Center) {
                                    Canvas(Modifier.fillMaxSize()) { drawCircle(Color(0xFFD3DFCC), style = Stroke(8.dp.toPx())) }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("${todayMs / 60000} 分钟", fontSize = 30.sp, color = ink); Caption("${todayMs / 1000 % 60} 秒 · ${if(mode == 1) "提醒间隔 ${values[1]}" else "本次目标 ${values[0]}"} 分钟") }
                                }
                                Primary(if(enabled && permission && mode <= 3) "暂停干预" else "开启干预") { if(enabled && permission && mode <= 3) { enabled = false; save(); notice = "干预已暂停。" } else activate() }
                            }
                        }
                        Caption(if(!permission) "开启无障碍服务后，进入目标应用自动生效。" else if(mode == 3) "抖音推荐页滑动后等待倒计时结束，自动切换一次。" else if(mode == 2) "试用：在抖音推荐页滑动，再长按切换。" else "离开目标应用、锁屏或暂停后停止计时。", Modifier.padding(vertical = 12.dp))
                        Section("当前干预", "切换模式 →") { page = "modes" }
                        ModeCard(current, true, false) { page = "settings" }
                        Row(Modifier.fillMaxWidth().padding(vertical = 22.dp)) { Column(Modifier.weight(1f)) { Caption("今日提醒"); Text("$reminders 次", fontSize = 23.sp) }; Column(Modifier.weight(1f)) { Caption("主动结束使用"); Text("$ended 次", fontSize = 23.sp) } }
                        Section("受干预应用", "管理 →") { page = "apps" }
                        CardBlock { if(apps.none { it }) Caption("尚未选择应用") else listOf("抖音").forEachIndexed { i, name -> if(apps[i]) Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(name); Caption("已加入计划") } } }
                    }
                    "modes" -> {
                        Heading("找到适合你的节奏", "四种方式，让每一次继续都更有意识。")
                        modes.forEachIndexed { i, m -> ModeCard(m, mode == i, true) { if(mode != i) enabled = false; mode = i; save(); page = "settings" } }
                    }
                    "settings" -> {
                        TextButton(onClick = { page = "modes" }) { Text("← 干预模式") }
                        Row(verticalAlignment = Alignment.CenterVertically) { Symbol(current); Column(Modifier.padding(start = 14.dp)) { Text(current.name, fontSize = 24.sp); Caption(current.subtitle) } }
                        Spacer(Modifier.height(28.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(current.parameter); Text("${values[mode]} ${current.unit}", color = green) }
                        Slider(value = values[mode].toFloat(), onValueChange = { values[mode] = it.toInt() }, onValueChangeFinished = { save() }, valueRange = current.min.toFloat()..current.max.toFloat(), steps = current.max - current.min - 1)
                        if (mode == 0) {
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("浮窗不透明度"); Text("$goalOpacity%",color=green) }
                            Slider(value=goalOpacity.toFloat(),onValueChange={ goalOpacity=it.toInt(); prefs.edit().putInt("goal_opacity",goalOpacity).apply() },valueRange=20f..100f,steps=15)
                            Caption("在抖音中按住进度环拖动，位置会自动保存。20% 最淡，100% 完全不透明。")
                        }
                        if(mode > 1) { Text(if(mode == 2) "滑动触发频率" else "触发频率", modifier = Modifier.padding(top = 16.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(1, 3, 5).forEach { n -> FilterChip(selected = frequency == n, onClick = { frequency = n; save() }, label = { Text(if(n == 1) "每次切换" else "每 $n 次") }) } } }
                        CardBlock { Text("仅在目标应用中生效"); Caption("离开后隐藏浮层并暂停，回来继续累计；暂停后重新开启，开始新一轮。") }
                        CardBlock { Text(if(mode == 0) "悬浮进度环" else if(mode == 1) "继续前等待" else "随时可暂停"); Caption(if(mode == 0) "显示本次进度，支持拖动位置和调节不透明度。" else if(mode == 1) "等待 3 秒后，再决定继续或结束。" else "保留退出入口，让干预始终可控。") }
                        Spacer(Modifier.height(26.dp)); Primary("预览干预效果") { page = "preview" }
                        TextButton(onClick = { save(); activate() }, modifier = Modifier.fillMaxWidth()) { Text(if(mode <= 3) "保存并开启${current.name}" else "保存模式设置") }
                        Caption(when(mode) { 2 -> "动作模式试用：覆盖抖音推荐视频区域，未知页面自动放行。长按完成后松手切换；执行失败时无需重新长按。"; 3 -> "慢速模式：目前支持抖音推荐页。等待结束自动切换一次；连续滑动不排队，离开页面或锁屏取消等待。"; else -> "设置立即应用于真实干预。" })
                    }
                    "apps" -> {
                        Heading("应用管理", "目前仅支持抖音，可随时开启或暂停干预。")
                        CardBlock { listOf("抖音").forEachIndexed { i, name -> Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Surface(color = ink, shape = RoundedCornerShape(13.dp)) { Box(Modifier.size(43.dp), contentAlignment = Alignment.Center) { Text("♪", color = Color.White, fontSize = 22.sp) } }; Column(Modifier.weight(1f).padding(start = 14.dp)) { Text(name); Caption(if(!installed[i]) "未安装" else if(apps[i]) "已加入干预" else "未加入干预") }; Switch(checked = apps[i], onCheckedChange = { apps[i] = it; save() }) } } }
                        Spacer(Modifier.height(24.dp)); Text("运行权限", fontWeight = FontWeight.Medium)
                        CardBlock { Text("干预浮层"); Caption("由无障碍服务提供，不需要额外悬浮窗权限。") }
                        CardBlock { Text("无障碍服务 · ${if(permission) "已开启" else "未开启"}"); Caption("识别所选应用、计时并显示进度或提醒，仅保存按日汇总的时长和提醒统计。"); TextButton(onClick = { explainPermission = true }) { Text(if(permission) "管理服务" else "开启服务") } }
                        Caption("四种模式仅支持抖音；动作和慢速模式在推荐页生效。", Modifier.padding(top = 16.dp))
                    }
                    "preview" -> { TextButton(onClick = { page = "settings" }) { Text("← ${current.name} · 效果预览") }; InterventionPreview(mode, values[mode], goalOpacity) { page = "settings" }; Caption("仅为交互演示，不会影响其他应用。", Modifier.padding(vertical = 14.dp)) }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable private fun Caption(text: String, modifier: Modifier = Modifier) { Text(text, modifier, color = muted, fontSize = 12.sp, lineHeight = 19.sp) }
@Composable private fun Heading(title: String, subtitle: String) { Text(title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold); Caption(subtitle, Modifier.padding(top = 8.dp, bottom = 20.dp)) }
@Composable private fun Primary(text: String, onClick: () -> Unit) { Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(15.dp)) { Text(text) } }
@Composable private fun Section(title: String, action: String, onClick: () -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(title, fontWeight = FontWeight.Medium); TextButton(onClick = onClick) { Text(action, fontSize = 12.sp) } } }
@Composable private fun Symbol(mode: Intervention) {
    Surface(color = mode.tint, shape = RoundedCornerShape(15.dp)) {
        Canvas(Modifier.size(46.dp).padding(11.dp)) {
            val w = size.width
            val stroke = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round)
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(mode.color, Offset(x1*w,y1*w), Offset(x2*w,y2*w), stroke.width, StrokeCap.Round)
            when(modes.indexOf(mode)) {
                0 -> { drawCircle(mode.color, w*.43f, style=stroke); drawCircle(mode.color, w*.24f, style=stroke); drawCircle(mode.color, w*.05f) }
                1 -> { val bell = Path().apply { moveTo(w*.2f,w*.74f); lineTo(w*.28f,w*.58f); lineTo(w*.28f,w*.35f); cubicTo(w*.28f,w*.05f,w*.72f,w*.05f,w*.72f,w*.35f); lineTo(w*.72f,w*.58f); lineTo(w*.8f,w*.74f); close() }; drawPath(bell,mode.color,style=stroke); line(.43f,.9f,.57f,.9f) }
                2 -> { line(.5f,.85f,.5f,.2f); line(.5f,.2f,.32f,.38f); line(.5f,.2f,.68f,.38f); drawArc(mode.color,0f,180f,false,Offset(w*.15f,w*.5f), androidx.compose.ui.geometry.Size(w*.7f,w*.4f),style=stroke) }
                else -> { line(.2f,.1f,.8f,.1f); line(.2f,.9f,.8f,.9f); line(.25f,.12f,.25f,.25f); line(.75f,.12f,.75f,.25f); line(.25f,.25f,.75f,.75f); line(.75f,.25f,.25f,.75f); line(.25f,.75f,.25f,.88f); line(.75f,.75f,.75f,.88f) }
            }
        }
    }
}
@Composable private fun CardBlock(content: @Composable ColumnScope.() -> Unit) { Surface(color = Color.White, shape = RoundedCornerShape(19.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Column(Modifier.padding(18.dp), content = content) } }
@Composable private fun ModeCard(mode: Intervention, selected: Boolean, description: Boolean, onClick: () -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(20.dp), border = if(selected) BorderStroke(1.dp, green) else null, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clickable(onClick = onClick)) {
        Column(Modifier.padding(17.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Symbol(mode); Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(mode.name, fontWeight = FontWeight.Medium); Caption(mode.subtitle) }; Text(if(selected) "已选" else "›", color = green, fontSize = 12.sp) }; if(description) Caption(mode.description, Modifier.padding(top = 12.dp)) }
    }
}

@Composable private fun InterventionPreview(index: Int, value: Int, goalOpacity: Int, onExit: () -> Unit) {
    var progress by remember(index, value) { mutableFloatStateOf(0f) }
    var holding by remember { mutableStateOf(false) }
    var dismissed by remember(index) { mutableStateOf(false) }
    val seconds = if(index == 1) 3 else value
    LaunchedEffect(index, holding, dismissed) {
        if(dismissed || index == 0) return@LaunchedEffect
        if(index == 2 && !holding) { if(progress < 1f) progress = 0f; return@LaunchedEffect }
        while(progress < 1f) { delay(50); progress = (progress + .05f / seconds).coerceAtMost(1f) }
        if (index == 3) dismissed = true
    }
    Box(Modifier.fillMaxWidth().heightIn(min = 440.dp).background(ink, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
        Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) { Text("@山间日常", color = Color.White); Text("把步子放慢，听一听风。\n视频内容为示意", color = Color(0xFFCDDCD2), fontSize = 12.sp) }
        if(index == 0) Surface(modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).graphicsLayer(alpha=goalOpacity/100f), color = paper, shape = RoundedCornerShape(24.dp)) { Row(Modifier.height(48.dp).padding(horizontal=12.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(progress = { .53f }, modifier = Modifier.size(24.dp), strokeWidth=3.dp, color = green, trackColor = Color(0xFFD3DFCC)); Text("  ${(value*.53f).toInt()}:00 / ${value}分钟", fontSize = 12.sp, color=green) } }
        else if(!dismissed) {
            Box(Modifier.matchParentSize().background(Color(0xAF152D29),RoundedCornerShape(24.dp)))
            Surface(color = paper, shape = RoundedCornerShape(24.dp), modifier = Modifier.padding(20.dp).widthIn(max=320.dp)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Symbol(modes[index]); Spacer(Modifier.height(18.dp)); Text(when(index) { 1 -> "给自己一个停顿"; 2 -> "长按，再看下一条"; else -> "下一条，稍等一下" }, fontSize = 20.sp)
                Caption(when(index) { 1 -> "本轮已观看 $value 分钟。\n现在想继续，还是休息一下？"; 2 -> "按住下方按钮，完成这次切换。\n长按完成后，松手即可切换"; else -> "留一点空隙，让注意力跟上节奏。\n等待结束后自动切换" }, Modifier.padding(vertical = 18.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp), color = modes[index].color, trackColor = modes[index].tint)
                if(index == 2) Box(Modifier.fillMaxWidth().height(52.dp).background(modes[index].color, RoundedCornerShape(15.dp)).pointerInput(value) { detectTapGestures(onPress = { holding = true; try { val released = tryAwaitRelease(); if(released && progress >= 1f) dismissed = true } finally { holding = false } }) }, contentAlignment = Alignment.Center) { Text(if(progress >= 1f) "已完成，松手切换" else if(holding) "保持长按…" else "按住 $value 秒", color = Color.White) }
                else if(index == 3) Box(Modifier.fillMaxWidth().height(52.dp).background(modes[index].color,RoundedCornerShape(15.dp)),contentAlignment=Alignment.Center) { Text("等待 ${kotlin.math.ceil(seconds*(1f-progress)).toInt()} 秒",color=Color.White) }
                else {
                    Caption(if(progress >= 1f) "现在可以做出选择" else "停一停 · ${kotlin.math.ceil(seconds*(1f-progress)).toInt()} 秒后可以选择")
                    Button(onClick={dismissed=true},enabled=progress>=1f,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(15.dp),colors=ButtonDefaults.buttonColors(containerColor=modes[index].color)) { Text(if(progress>=1f) "继续观看" else "等待 ${kotlin.math.ceil(seconds*(1f-progress)).toInt()} 秒") }
                    Button(onClick=onExit,enabled=progress>=1f,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(15.dp),colors=ButtonDefaults.buttonColors(containerColor=modes[index].tint,contentColor=ink)) { Text("结束本次使用") }
                }
                TextButton(onClick = onExit) { Text("暂停干预") }
            }
            }
        }
    }
}
