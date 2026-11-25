@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)

package com.nevroz.playeraero

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

fun fmt(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }
fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

fun openUrl(ctx: Context, url: String) {
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (e: Exception) { toast(ctx, "Не удалось открыть ссылку") }
}

/** Играем внутри приложения, а треки сервисов (Spotify/SoundCloud/Яндекс) открываем в их приложении. */
fun openOrPlay(vm: PlayerVM, ctx: Context, list: List<Track>, t: Track) {
    if (t.playable) vm.playList(list, list.indexOf(t)) else openUrl(ctx, t.uri)
}

@Composable
fun rememberProgress(vm: PlayerVM): State<Pair<Long, Long>> =
    produceState(0L to 0L, vm.current?.id, vm.isPlaying) {
        while (true) { value = vm.position() to vm.duration(); delay(250) }
    }

// ───────────────────────── Корневой экран ─────────────────────────

@Composable
fun AeroApp(vm: PlayerVM, fpsList: List<Int>) {
    var tab by remember { mutableIntStateOf(0) }
    var showPlayer by remember { mutableStateOf(false) }
    AeroBackground(vm.prefs.bubbles) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.weight(1f)) {
                Crossfade(tab, animationSpec = tween(350), label = "tab") {
                    when (it) {
                        0 -> HomeScreen(vm)
                        1 -> LibraryScreen(vm)
                        else -> SettingsScreen(vm, fpsList)
                    }
                }
            }
            if (vm.current != null) MiniPlayer(vm) { showPlayer = true }
            BottomBar(tab) { tab = it }
        }
        AnimatedVisibility(
            visible = showPlayer,
            enter = slideInVertically(tween(450, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(450)),
            exit = slideOutVertically(tween(350)) { it } + fadeOut(tween(350))
        ) { PlayerScreen(vm) { showPlayer = false } }
    }
    BackHandler(showPlayer) { showPlayer = false }
}

@Composable
fun BottomBar(sel: Int, onSel: (Int) -> Unit) {
    val items = listOf(Icons.Rounded.Home to "Главная", Icons.Rounded.LibraryMusic to "Медиатека", Icons.Rounded.Settings to "Настройки")
    Row(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth()
        .glass(RoundedCornerShape(32.dp), true).padding(6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        items.forEachIndexed { i, (ic, label) ->
            val on = i == sel
            val a by animateFloatAsState(if (on) 1f else 0f, tween(300), label = "navbg")
            val sc by animateFloatAsState(if (on) 1.06f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "navsc")
            Column(Modifier.weight(1f).scale(sc).clip(RoundedCornerShape(26.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF7BE8FF).copy(alpha = a), Color(0xFF0FA6E8).copy(alpha = a))))
                .clickable { onSel(i) }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(ic, label, tint = if (on) Color.White else DeepBlue)
                Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else DeepBlue)
            }
        }
    }
}

@Composable
fun MiniPlayer(vm: PlayerVM, onOpen: () -> Unit) {
    val t = vm.current ?: return
    val prog by rememberProgress(vm)
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().glass(RoundedCornerShape(28.dp), true).clickable(onClick = onOpen)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(t, transitionSpec = {
                (slideInHorizontally(tween(350)) { it / 2 } + fadeIn(tween(350))) togetherWith
                        (slideOutHorizontally(tween(350)) { -it / 2 } + fadeOut(tween(250)))
            }, label = "miniCover") { tr -> Cover(tr, Modifier.size(52.dp), RoundedCornerShape(16.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                AText(t.title, 15, true, maxLines = 1)
                AText(t.artist, 13, color = DeepBlue.copy(0.7f), maxLines = 1)
            }
            GlossButton({ vm.prev() }, 38.dp) { Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White) }
            Spacer(Modifier.width(6.dp))
            GlossButton({ vm.toggle() }, 46.dp) {
                Icon(if (vm.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(6.dp))
            GlossButton({ vm.next() }, 38.dp) { Icon(Icons.Rounded.SkipNext, null, tint = Color.White) }
        }
        val frac = if (prog.second > 0) (prog.first.toFloat() / prog.second).coerceIn(0f, 1f) else 0f
        LinearProgressIndicator(progress = { frac },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 8.dp).height(4.dp).clip(RoundedCornerShape(50)),
            color = Aqua, trackColor = Color.White.copy(0.5f))
    }
}

// ───────────────────────── Главная + «Моя волна» ─────────────────────────

@Composable
fun HomeScreen(vm: PlayerVM) {
    val ctx = LocalContext.current
    val waveActive = vm.waveMode && vm.current != null
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item { AText("PlayerAero", 26, true, Color.White, Modifier.padding(top = 4.dp)) }
        item {
            WaveOrb(waveActive, vm.isPlaying, vm.current) {
                if (waveActive) vm.toggle() else if (!vm.startWave()) toast(ctx, "Сначала добавь треки в медиатеку")
            }
        }
        item {
            AText("Моя волна", 30, true, Color.White)
            AText(if (waveActive) "Играет бесконечный микс" else "Нажми на шар — включится микс из твоей музыки",
                14, color = Color.White.copy(0.9f), align = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))
        }
        if (vm.tracks.isEmpty()) item {
            Column(Modifier.fillMaxWidth().glass().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AText("Пока пусто", 18, true)
                AText("Открой «Медиатеку» и добавь треки из памяти или по ссылке", 14, align = TextAlign.Center)
            }
        } else {
            item { SectionTitle("Недавно добавленные") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(vm.tracks.takeLast(12).reversed(), key = { it.id }) { t ->
                        Column(Modifier.width(124.dp).clickable { openOrPlay(vm, ctx, vm.tracks, t) }) {
                            Cover(t, Modifier.size(124.dp).shadow(10.dp, RoundedCornerShape(24.dp)), RoundedCornerShape(24.dp))
                            AText(t.title, 13, true, Color.White, Modifier.padding(top = 6.dp), 1)
                            AText(t.artist, 12, color = Color.White.copy(0.85f), maxLines = 1)
                        }
                    }
                }
            }
        }
        if (vm.playlists.isNotEmpty()) {
            item { SectionTitle("Плейлисты") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(vm.playlists, key = { it.id }) { p ->
                        val list = p.trackIds.mapNotNull { id -> vm.tracks.firstOrNull { it.id == id } }
                        Column(Modifier.width(150.dp).glass().clickable { if (list.isNotEmpty()) openOrPlay(vm, ctx, list, list[0]) }.padding(16.dp)) {
                            Icon(Icons.Rounded.LibraryMusic, null, tint = SkyBlue)
                            AText(p.name, 16, true, Modifier.padding(top = 6.dp), 1)
                            AText("${list.size} трек.", 12, color = DeepBlue.copy(0.7f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(s: String) = AText(s, 18, true, Color.White, Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp))

@Composable
fun WaveOrb(active: Boolean, playing: Boolean, track: Track?, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "orb")
    val fast = active && playing
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(if (fast) 6000 else 14000, easing = LinearEasing)), label = "rot")
    val pulse by t.animateFloat(0.96f, 1.05f,
        infiniteRepeatable(tween(if (fast) 900 else 2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val ripple by t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (fast) 1800 else 3200, easing = LinearEasing)), label = "ripple")
    Box(Modifier.size(300.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2
            for (i in 0..2) {
                val p = (ripple + i / 3f) % 1f
                drawCircle(color = Color.White.copy(alpha = (1f - p) * 0.55f), radius = r * (0.5f + 0.5f * p), center = c, style = Stroke(3f))
            }
            rotate(rot, c) {
                drawCircle(brush = Brush.sweepGradient(listOf(Aqua, Color.White, Grass, Aqua), center = c),
                    radius = r * 0.62f, center = c, style = Stroke(r * 0.07f))
            }
            for (i in 0..5) {
                val a = (rot * PI / 180.0 * (if (i % 2 == 0) 1 else -1) + i * PI / 3).toFloat()
                drawBubble(Offset(c.x + cos(a) * r * 0.8f, c.y + sin(a) * r * 0.8f), (6 + i * 2).dp.toPx(), 0.9f)
            }
        }
        Box(Modifier.size(190.dp).scale(pulse)
            .shadow(26.dp, CircleShape, ambientColor = Color(0xFF0277BD), spotColor = Color(0xFF00B8D4))
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xFF9CF1FF), Color(0xFF14AEEF), Color(0xFF0366B5))))
            .border(3.dp, Brush.verticalGradient(listOf(Color.White, Color.White.copy(0.25f))), CircleShape),
            contentAlignment = Alignment.Center) {
            if (active && track != null) Cover(track, Modifier.fillMaxSize(), CircleShape)
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(72.dp))
            }
            if (active && !playing) Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(72.dp))
            Box(Modifier.align(Alignment.TopCenter).padding(top = 10.dp).fillMaxWidth(0.7f).height(70.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.verticalGradient(listOf(Color.White.copy(0.75f), Color.Transparent))))
        }
    }
}

// ───────────────────────── Медиатека ─────────────────────────

@Composable
fun LibraryScreen(vm: PlayerVM) {
    val ctx = LocalContext.current
    var sel by remember { mutableStateOf<String?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var showLink by remember { mutableStateOf(false) }
    var addTo by remember { mutableStateOf<Track?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.addLocal(uris, sel)
    }
    val pl = vm.playlists.firstOrNull { it.id == sel }
    val list = if (pl == null) vm.tracks else pl.trackIds.mapNotNull { id -> vm.tracks.firstOrNull { it.id == id } }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            AText("Медиатека", 26, true, Color.White, Modifier.weight(1f))
            GlossButton({ picker.launch(arrayOf("audio/*")) }, 44.dp) { Icon(Icons.Rounded.Add, "Из памяти", tint = Color.White) }
            Spacer(Modifier.width(8.dp))
            GlossButton({ showLink = true }, 44.dp) { Icon(Icons.Rounded.Link, "Ссылка", tint = Color.White) }
            Spacer(Modifier.width(8.dp))
            GlossButton({ if (list.isNotEmpty()) vm.playList(list, 0) }, 44.dp) { Icon(Icons.Rounded.PlayArrow, "Играть", tint = Color.White) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AeroChip("Все треки", sel == null) { sel = null }
            vm.playlists.forEach { p -> AeroChip(p.name, sel == p.id) { sel = p.id } }
            AeroChip("+ Плейлист", false) { showNew = true }
        }
        if (pl != null) Row(Modifier.padding(bottom = 8.dp)) {
            AeroChip("Удалить плейлист", false) { vm.deletePlaylist(pl.id); sel = null }
        }
        if (list.isEmpty()) {
            Column(Modifier.fillMaxWidth().glass().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AText("Здесь пока нет треков", 17, true)
                AText("Нажми «+» чтобы выбрать файлы из памяти телефона, или «🔗» чтобы добавить ссылку на SoundCloud / Spotify / Яндекс Музыку",
                    13, align = TextAlign.Center)
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
            items(list, key = { it.id }) { t ->
                var menu by remember { mutableStateOf(false) }
                Row(Modifier.animateItemPlacement().fillMaxWidth().glass(RoundedCornerShape(22.dp))
                    .clickable { openOrPlay(vm, ctx, list, t) }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Cover(t, Modifier.size(56.dp), RoundedCornerShape(16.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        AText(t.title, 15, true, maxLines = 1)
                        AText(t.artist, 13, color = DeepBlue.copy(0.7f), maxLines = 1)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(t.source.color)))
                            Spacer(Modifier.width(5.dp))
                            AText(t.source.label + if (!t.playable) " · откроется в приложении" else "", 11, color = DeepBlue.copy(0.6f), maxLines = 1)
                        }
                    }
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, null, tint = DeepBlue) }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("В плейлист") }, { menu = false; addTo = t },
                                leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null) })
                            DropdownMenuItem({ Text("Удалить") }, { menu = false; vm.deleteTrack(t.id) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null) })
                        }
                    }
                }
            }
        }
    }

    if (showNew) {
        var name by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showNew = false }, containerColor = Color.White.copy(0.94f), shape = RoundedCornerShape(28.dp),
            title = { Text("Новый плейлист") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("Название") }) },
            confirmButton = { TextButton({ vm.createPlaylist(name); showNew = false }) { Text("Создать") } },
            dismissButton = { TextButton({ showNew = false }) { Text("Отмена") } })
    }
    if (showLink) {
        var url by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showLink = false }, containerColor = Color.White.copy(0.94f), shape = RoundedCornerShape(28.dp),
            title = { Text("Добавить по ссылке") },
            text = {
                Column {
                    Text("Ссылка на трек SoundCloud, Spotify, Яндекс Музыки или прямой аудио-файл (mp3, m4a…)", fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(url, { url = it }, singleLine = true, placeholder = { Text("https://…") })
                }
            },
            confirmButton = {
                TextButton({ if (vm.addLink(url, sel)) showLink = false else toast(ctx, "Это не похоже на ссылку") }) { Text("Добавить") }
            },
            dismissButton = { TextButton({ showLink = false }) { Text("Отмена") } })
    }
    addTo?.let { tr ->
        AlertDialog(onDismissRequest = { addTo = null }, containerColor = Color.White.copy(0.94f), shape = RoundedCornerShape(28.dp),
            title = { Text("Добавить в плейлист") },
            text = {
                Column {
                    if (vm.playlists.isEmpty()) Text("Сначала создай плейлист кнопкой «+ Плейлист»")
                    vm.playlists.forEach { p ->
                        TextButton({ vm.addToPlaylist(p.id, listOf(tr.id)); addTo = null }) { Text(p.name) }
                    }
                }
            },
            confirmButton = { TextButton({ addTo = null }) { Text("Закрыть") } })
    }
}

// ───────────────────────── Настройки ─────────────────────────

@Composable
fun GlassCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().glass().padding(18.dp)) {
        AText(title, 17, true)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AText(label, 15, modifier = Modifier.weight(1f))
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Aqua, checkedThumbColor = Color.White))
    }
}

@Composable
fun SettingsScreen(vm: PlayerVM, fpsList: List<Int>) {
    val ctx = LocalContext.current
    val p = vm.prefs
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { AText("Настройки", 26, true, Color.White) }
        item {
            GlassCard("Частота кадров") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AeroChip("Авто", p.fps == 0) { vm.setPrefs(p.copy(fps = 0)) }
                    fpsList.forEach { f -> AeroChip("$f Гц", p.fps == f) { vm.setPrefs(p.copy(fps = f)) } }
                }
                AText("Применяется, если экран поддерживает такой режим. Меньше кадров — дольше работает батарея.", 12,
                    color = DeepBlue.copy(0.7f), modifier = Modifier.padding(top = 8.dp))
            }
        }
        item {
            GlassCard("Перелистывание треков") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Плавное", "Быстрое", "Без анимации").forEachIndexed { i, n ->
                        AeroChip(n, p.swipeStyle == i) { vm.setPrefs(p.copy(swipeStyle = i)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                SwitchRow("Свайп по обложке", p.swipeCover) { vm.setPrefs(p.copy(swipeCover = it)) }
            }
        }
        item {
            GlassCard("Оформление") {
                SwitchRow("Пузырьки на фоне", p.bubbles) { vm.setPrefs(p.copy(bubbles = it)) }
            }
        }
        item {
            GlassCard("Сервисы") {
                AText("Добавляй треки по ссылке в «Медиатеке». Прямые аудио-ссылки играют внутри PlayerAero, а треки сервисов открываются в их приложении.",
                    12, color = DeepBlue.copy(0.7f))
                listOf(Source.SOUNDCLOUD to "https://soundcloud.com", Source.SPOTIFY to "https://open.spotify.com",
                    Source.YANDEX to "https://music.yandex.ru").forEach { (s, url) ->
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(s.color)))
                        Spacer(Modifier.width(10.dp))
                        AText(s.label, 15, true, modifier = Modifier.weight(1f))
                        AeroChip("Открыть", false) { openUrl(ctx, url) }
                    }
                }
            }
        }
        item { DevSignature() }
    }
}

/** Пасхалка: подпись разработчика внизу настроек. Тап — взрыв пузырей. */
@Composable
fun DevSignature() {
    var taps by remember { mutableIntStateOf(0) }
    val t = rememberInfiniteTransition(label = "sig")
    val sh by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2500, easing = LinearEasing)), label = "sh")
    Column(Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 32.dp)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { taps++ },
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            if (taps > 0) key(taps) { BubbleBurst(Modifier.matchParentSize()) }
            Text("Developer by: NevroZ", style = TextStyle(
                brush = Brush.linearGradient(listOf(DeepBlue, Aqua, Grass, DeepBlue),
                    start = Offset(sh * 700f - 300f, 0f), end = Offset(sh * 700f + 300f, 0f)),
                fontSize = 16.sp, fontWeight = FontWeight.Bold))
        }
        AnimatedVisibility(taps >= 7) { AText("✨ Aero forever ✨", 13, color = Color.White, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
fun BubbleBurst(modifier: Modifier) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    Canvas(modifier) {
        val c = center
        for (i in 0 until 14) {
            val a = (i * 2 * PI / 14).toFloat()
            val d = (60 + (i % 3) * 40).dp.toPx() * p.value
            drawBubble(Offset(c.x + cos(a) * d, c.y + sin(a) * d - 24.dp.toPx() * p.value),
                (7 + (i % 3) * 5).dp.toPx() * (1f - p.value * 0.4f), 1f - p.value)
        }
    }
}

// ───────────────────────── Полноэкранный плеер ─────────────────────────

@Composable
fun PlayerScreen(vm: PlayerVM, onClose: () -> Unit) {
    AeroBackground(vm.prefs.bubbles) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlossButton(onClose, 44.dp) { Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Color.White) }
                AText(if (vm.waveMode) "Моя волна" else "Сейчас играет", 16, true, Color.White, Modifier.weight(1f), align = TextAlign.Center)
                Spacer(Modifier.size(44.dp))
            }
            Spacer(Modifier.height(20.dp))
            val q = vm.queue
            val pager = rememberPagerState(initialPage = vm.index.coerceIn(0, (q.size - 1).coerceAtLeast(0))) { q.size }
            val spec: AnimationSpec<Float> = when (vm.prefs.swipeStyle) {
                0 -> spring<Float>(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow)
                1 -> tween<Float>(220)
                else -> snap<Float>()
            }
            LaunchedEffect(vm.index, q) {
                if (vm.index in q.indices && pager.currentPage != vm.index) pager.animateScrollToPage(vm.index, animationSpec = spec)
            }
            LaunchedEffect(pager) {
                snapshotFlow { pager.settledPage }.collect { if (it != vm.index && it in vm.queue.indices) vm.seekToIndex(it) }
            }
            HorizontalPager(pager, Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 36.dp),
                pageSpacing = 16.dp, userScrollEnabled = vm.prefs.swipeCover) { page ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Cover(q.getOrNull(page), Modifier.fillMaxWidth().aspectRatio(1f)
                        .graphicsLayer {
                            val d = page - pager.currentPage - pager.currentPageOffsetFraction
                            val k = 1f - d.coerceIn(-1f, 1f).let { if (it < 0) -it else it }
                            scaleX = lerp(0.82f, 1f, k); scaleY = scaleX
                            alpha = lerp(0.5f, 1f, k)
                            rotationY = -d.coerceIn(-1f, 1f) * 25f
                            cameraDistance = 12f * density
                        }
                        .shadow(20.dp, RoundedCornerShape(32.dp), ambientColor = Color(0xFF0277BD), spotColor = Color(0xFF0277BD)),
                        RoundedCornerShape(32.dp))
                }
            }
            Spacer(Modifier.height(22.dp))
            val t = vm.current
            AnimatedContent(t, transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(200)) }, label = "title") { tr ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AText(tr?.title ?: "", 24, true, Color.White, maxLines = 1, align = TextAlign.Center)
                    AText(tr?.artist ?: "", 16, color = Color.White.copy(0.92f), maxLines = 1, align = TextAlign.Center)
                }
            }
            Spacer(Modifier.weight(1f))
            val prog by rememberProgress(vm)
            var drag by remember { mutableStateOf<Float?>(null) }
            val frac = drag ?: if (prog.second > 0) (prog.first.toFloat() / prog.second).coerceIn(0f, 1f) else 0f
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(30.dp), true).padding(horizontal = 18.dp, vertical = 10.dp)) {
                Slider(frac, { drag = it }, onValueChangeFinished = { drag?.let { vm.seekTo((it * prog.second).toLong()) }; drag = null },
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Aqua, inactiveTrackColor = Color.White.copy(0.6f)))
                Row {
                    AText(fmt((frac * prog.second).toLong()), 12)
                    Spacer(Modifier.weight(1f))
                    AText(fmt(prog.second), 12)
                }
                val inf = rememberInfiniteTransition(label = "pb")
                val breathe by inf.animateFloat(1f, if (vm.isPlaying) 1.07f else 1f,
                    infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "br")
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically) {
                    GlossButton({ vm.prev() }, 60.dp) { Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
                    GlossButton({ vm.toggle() }, 84.dp, Modifier.scale(breathe)) {
                        AnimatedContent(vm.isPlaying, transitionSpec = { (scaleIn(tween(250)) + fadeIn(tween(250))) togetherWith (scaleOut(tween(200)) + fadeOut(tween(200))) }, label = "pp") { pl ->
                            Icon(if (pl) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(48.dp))
                        }
                    }
                    GlossButton({ vm.next() }, 60.dp) { Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
