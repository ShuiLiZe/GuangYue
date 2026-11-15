package com.guangyue.feature.library.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.guangyue.core.plugin.PluginMeta
import com.guangyue.core.plugin.login.LoginSession
import com.guangyue.core.plugin.login.LoginVariableMapper
import com.guangyue.core.plugin.login.QrLogin
import com.guangyue.core.plugin.login.QrPlatform
import com.guangyue.core.plugin.login.QrState
import com.guangyue.core.plugin.login.QrTicket
import com.guangyue.core.plugin.runtime.PluginUserVariable
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 扫码登录得到的账号（头像 / 昵称 / ID），按平台存，给登录窗和右下按钮用。 */
object LoginSessions {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("login_sessions", Context.MODE_PRIVATE)

    fun get(c: Context, p: QrPlatform): LoginSession? {
        val raw = prefs(c).getString(p.id, null) ?: return null
        return runCatching {
            val o = Json.parseToJsonElement(raw).jsonObject
            fun s(k: String) = o[k]?.jsonPrimitive?.contentOrNull.orEmpty()
            LoginSession(p.id, s("uid"), s("nickname"), s("avatar"), emptyMap())
        }.getOrNull()
    }

    fun put(c: Context, s: LoginSession) {
        val o = JsonObject(mapOf("uid" to JsonPrimitive(s.uid), "nickname" to JsonPrimitive(s.nickname), "avatar" to JsonPrimitive(s.avatar)))
        prefs(c).edit().putString(s.platform, o.toString()).apply()
    }

    fun clear(c: Context, p: QrPlatform) = prefs(c).edit().remove(p.id).apply()
}

private val PLATFORM_COLOR = mapOf(
    QrPlatform.QQ to Color(0xFF1DB954),
    QrPlatform.NETEASE to Color(0xFFE02E24),
    QrPlatform.KUGOU to Color(0xFF2266EE),
)

private fun badgeText(p: QrPlatform) = when (p) { QrPlatform.QQ -> "Q"; QrPlatform.NETEASE -> "云"; QrPlatform.KUGOU -> "K" }

private fun qrBitmap(text: String, size: Int = 512): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val px = IntArray(size * size) { i -> if (m[i % size, i / size]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}

/**
 * Folia LoginScreen（手机版）：深色窗，标题「登录到 平台」+ 关闭；一排平台胶囊；中间方块是二维码（登录后是头像 + 平台角标）；
 * 下面标题 / 说明；登录后「同步数据」「退出登录」。扫码成功把凭据写进这个平台所有插件的账号。
 */
@Composable
fun QrLoginDialog(
    initial: PluginMeta?,
    plugins: List<PluginMeta>,
    declared: suspend (PluginMeta) -> List<PluginUserVariable>,
    save: (PluginMeta, Map<String, String>) -> Unit,
    /** 「同步数据」：手动重读这个平台的账号（Folia syncAccountData）。 */
    onSync: (QrPlatform) -> Unit,
    onDismiss: () -> Unit,
    /** 扫码成功（Folia accountRead reason=sign-in：登录后自动读一次账号）。session.fields 带凭据。 */
    onLoggedIn: (LoginSession) -> Unit = {},
    /** 退出登录：清掉账号凭据与缓存的歌单。 */
    onLoggedOut: (QrPlatform) -> Unit = {},
    /** 正在读账号：「同步数据」换成「同步中...」（Folia account.syncing）。 */
    syncing: Boolean = false,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var platform by remember { mutableStateOf(QrPlatform.of(initial?.platform) ?: QrPlatform.of(initial?.name) ?: QrPlatform.QQ) }
    var session by remember(platform) { mutableStateOf(LoginSessions.get(ctx, platform)) }
    var ticket by remember(platform) { mutableStateOf<QrTicket?>(null) }
    var state by remember(platform) { mutableStateOf<QrState>(QrState.Waiting) }
    var round by remember(platform) { mutableIntStateOf(0) }
    val login = remember { QrLogin() }
    val accent = PLATFORM_COLOR[platform] ?: Color.Gray

    fun pluginsOf(p: QrPlatform) = plugins.filter { (QrPlatform.of(it.platform) ?: QrPlatform.of(it.name)) == p }

    LaunchedEffect(platform, session == null, round) {
        if (session != null) return@LaunchedEffect
        ticket = null
        state = QrState.Waiting
        val t = runCatching { login.start(platform) }.getOrElse { state = QrState.Failed(it.message ?: "取二维码失败"); return@LaunchedEffect }
        ticket = t
        val until = System.currentTimeMillis() + 170_000
        while (System.currentTimeMillis() < until) {
            delay(2000)
            val s = login.poll(t)
            state = s
            when (s) {
                is QrState.Done -> {
                    for (p in pluginsOf(platform)) {
                        val values = LoginVariableMapper.map(platform, runCatching { declared(p) }.getOrDefault(emptyList()), s.session.fields)
                        if (values.isNotEmpty()) save(p, values)
                    }
                    LoginSessions.put(ctx, s.session)
                    session = LoginSessions.get(ctx, platform)
                    onLoggedIn(s.session)
                    return@LaunchedEffect
                }
                QrState.Expired, is QrState.Failed -> return@LaunchedEffect
                else -> Unit
            }
        }
        state = QrState.Expired
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .padding(horizontal = 14.dp)
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1C1C1E))
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent, accent.copy(alpha = 0.16f))))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .padding(bottom = 24.dp),
            ) {
                // 标题条
                Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Badge(platform, 26.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("登录到 ${platform.label}", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Box(Modifier.size(40.dp).clip(CircleShape).clickable { onDismiss() }, contentAlignment = Alignment.Center) {
                        Text("✕", color = Color.White.copy(alpha = 0.8f), fontSize = 17.sp)
                    }
                }
                // 平台胶囊
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    QrPlatform.entries.forEach { p ->
                        val on = p == platform
                        val s = remember(p, session) { LoginSessions.get(ctx, p) }
                        Row(
                            Modifier.clip(RoundedCornerShape(10.dp))
                                .background(if (on) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                                .clickable { platform = p }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Badge(p, 22.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(p.label, color = Color.White.copy(alpha = if (on) 0.95f else 0.7f), fontSize = 14.sp)
                            if (s != null && s.avatar.isNotBlank()) {
                                Spacer(Modifier.width(8.dp))
                                AsyncImage(s.avatar, null, Modifier.size(20.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                            }
                        }
                    }
                }
                // 方块
                Box(
                    Modifier.align(Alignment.CenterHorizontally).size(220.dp).clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.05f)).border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    val s = session
                    val t = ticket
                    when {
                        s != null -> Box {
                            if (s.avatar.isNotBlank()) AsyncImage(s.avatar, null, Modifier.size(96.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                            else Box(Modifier.size(96.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)))
                            Box(Modifier.align(Alignment.BottomEnd).offset(10.dp, 6.dp)) { Badge(platform, 42.dp, ring = true) }
                        }
                        t == null && state !is QrState.Failed -> CircularProgressIndicator(color = Color.White.copy(alpha = 0.6f), strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                        t != null -> {
                            val bmp = remember(t) {
                                t.png?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } ?: t.text?.let { qrBitmap(it) }
                            }
                            Box(contentAlignment = Alignment.Center) {
                                if (bmp != null) Image(
                                    bmp.asImageBitmap(), null,
                                    Modifier.size(176.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).padding(8.dp),
                                    filterQuality = FilterQuality.None,
                                )
                                if (state == QrState.Expired || state is QrState.Failed) {
                                    Box(
                                        Modifier.size(176.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.72f)).clickable { round++ },
                                        contentAlignment = Alignment.Center,
                                    ) { Text("刷新二维码", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
                                }
                            }
                        }
                        else -> Box(Modifier.size(176.dp).clickable { round++ }, contentAlignment = Alignment.Center) {
                            Text("刷新二维码", color = Color.White, fontSize = 15.sp)
                        }
                    }
                }
                // 文字
                val s = session
                Column(Modifier.padding(horizontal = 26.dp).padding(top = 22.dp)) {
                    val (head, note) = when {
                        s != null -> s.nickname to "${platform.label}" + (if (s.uid.isNotBlank()) " · ID: ${s.uid}" else "")
                        state == QrState.Scanned -> "扫描成功！请在手机上确认。" to platform.note
                        state == QrState.Expired -> "二维码已过期" to "点二维码刷新"
                        state is QrState.Failed -> "登录没有成功" to (state as QrState.Failed).message
                        else -> platform.title to platform.note
                    }
                    Text(head, color = Color.White, fontSize = if (s != null) 22.sp else 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Text(note, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, lineHeight = 20.sp)
                }
                if (s != null) {
                    Row(Modifier.padding(start = 22.dp, top = 28.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Pill(if (syncing) "同步中..." else "同步数据") { if (!syncing) onSync(platform) }
                        Pill("退出登录") {
                            scope.launch {
                                for (p in pluginsOf(platform)) {
                                    val vars = runCatching { declared(p) }.getOrDefault(emptyList())
                                    val keys = LoginVariableMapper.map(platform, vars, mapOf("musickey" to "x", "cookie" to "x", "uin" to "x", "MUSIC_U" to "x", "__csrf" to "x", "token" to "x", "userid" to "x")).keys
                                    if (keys.isNotEmpty()) save(p, keys.associateWith { "" })
                                }
                                LoginSessions.clear(ctx, platform)
                                onLoggedOut(platform)
                                session = null
                                round++
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(p: QrPlatform, size: androidx.compose.ui.unit.Dp, ring: Boolean = false) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .then(if (ring) Modifier.border(3.dp, Color(0xFF1C1C1E), CircleShape) else Modifier)
            .background(PLATFORM_COLOR[p] ?: Color.Gray),
        contentAlignment = Alignment.Center,
    ) {
        Text(badgeText(p), color = Color.White, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f)).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) { Text(text, color = Color.White.copy(alpha = 0.92f), fontSize = 15.sp) }
}
