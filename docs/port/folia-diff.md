# 广乐 vs Folia 手机版（APK folia-sonnet）逐屏差异清单

对照方法：模拟器 emulator-5554 上并排开 `top.izuna.foliamajor` 与 `com.guangyue.music`，每屏一张图。
**参照物是 APK，不是沙箱里的 Folia 源码**——源码比 APK 新（例如源码搜索页是至上主义构图，APK 是深色胶囊），两者冲突时以 APK 为准。

状态：✅ 已对齐　🔧 本批已修　⏳ 待修　❓ 需真机/网络　✋ 有意不同（理由见 HANDOFF）

## 首页
- 🔧 未登录态（63336cd 默认落歌单页签，空态即登录面板，胶囊直开登录窗；文案改用 Folia 原文）：Folia 显示「先搜几首喜欢的歌试试看」+ 说明 + 「登录到 网易云」胶囊（后跟各平台圆标），中间灰色人像方块。广乐未登录直接显示推荐卡片。→ 拟：无任何平台账号时首屏照搬 HomeLoginPanel，推荐降为其下方/页签内。
- ✅ 页签：Folia 4 个（歌单/电台/专辑/本地），广乐 0.0.29 起同（推荐/探索已去）。
- ⏳ 右下平台按钮：Folia 是平台真 logo 圆标；广乐是「网」字圆标（源码 PlatformMark 也是字标，APK 用 logo）。
- ✋ 标题字重：两边都是 Bold，观感差来自中文字形。
- ✅ 顶栏布局（标题+齿轮+右侧搜索胶囊）。

## 搜索
- 🔧 6a5640a/5f72a6d 按源码做成纸色至上主义，**与 APK 不符**；已改回 APK 样式：纯深色底、圆胶囊输入框（左放大镜）、右侧独立 48dp 圆形关闭钮、来源胶囊选中白底黑字。
- 🔧 进页自动聚焦，修首字被吞。
- ❓ 结果列表行样式：模拟器 TLS 失败，APK 结果未能加载，待真机/网络恢复后对照。
- ✋ 来源列表：Folia 内置 网易云/酷狗/QQ音乐/波点/飞牛/Local；广乐来自插件订阅，按插件显示。

## 设置
- ✅ 大窗外壳、帮助/选项页签、分类胶囊、分组卡片（5f72a6d）。
- 🔧 Folia 首次打开落在「帮助」页（新功能 / Ponder 帮助 / 快捷键 / 播放页控制 / 求助 / 加入社区 / 项目链接）；广乐落在「选项」。
- ⏳ 选项分类：Folia「视觉设置 / 通用设置 / …」，视觉页头右侧圆形月亮钮切深浅色，首项「歌词动画调整」描边卡片+箭头，其后「歌词壁纸」（设为系统动态壁纸、壁纸样式 极简/跟随、可视化浮层）。广乐分类为 外观/播放页/播放控制…，口径不同。
- ✋ 设置内搜索已去掉（APK 无）。

## 登录
- 🔧 扫码登录窗（ee35e60）。❓ 取码/轮询/写凭据需真机。

## 隐现背景
- 🔧 四格插值去硬格 + 浓度 0.2（ee35e60）。🔧 def5e4e 整层加亚像素模糊（半径≈density/1.79×0.7px）模拟 Folia WebGL 低分辨率渲染后放大的柔边。❓ 真机观感待确认。

## 播放页 / 歌词 / 町景
- ❓ 需要能出声的歌，模拟器网络失败，待真机。

## 2026-11-15 20:25 真机反馈：登录后不同步（最高优先）
- 现状：QQ 扫码 0.0.30 真机登录成功，但「同步数据」只调 `pluginVm.selectSource` + 吐司（NavHost.kt:299），**没有任何读取用户歌单的代码**；歌单页签只有本地歌单。
- Folia 做法：`components/login/syncAccountData.ts` → `account.syncAccount(providerId)`；各平台自带直连客户端 `services/onlineMusic/<平台>/`（qq/netease/kugou/qishui/bodian/fnmusic/appleMusic/spotify/ytm），登录后自动读账号（accountRead.ts，reason=sign-in）。
- 要做：
  1. 新建 core 账号读取层（Kotlin 直连，不走插件）：QQ 用 musicu.fcg `GetPlaylistByUin`（自建+收藏，「我喜欢」= dirId 201，见 qq/qqNormalize.ts:314-360），网易云 `/api/user/playlist`，酷狗对应接口。
  2. 扫码成功即自动同步（Folia reason=sign-in），「同步数据」手动重读并按 synced/ran-out/failed 提示（文案见 zh-CN status.dataSynced/loginExpired/syncFailed）。
  3. 歌单页签显示该平台账号歌单（我喜欢置顶），点开取曲目。
  4. 平台：先核对模拟器 Folia 手机版登录窗列出的平台，补齐能直连的（汽水、波点等），我们目前只有 QQ/网易云/酷狗三家。
- 🔧 2026-11-15 QQ 音乐已接：core/data `account/QqAccount.kt`（纯函数：GetPlaylistByUin + CgiGetPlaylistFavInfo 读歌单、uniform_get_Dissinfo 读曲目，1000/104401 = 登录过期）+ `AccountLibrary`（凭据 uin/musickey、files/account 缓存）。扫码成功自动读、「同步数据」手动重读，提示照 Folia 文案；首页歌单页签先列账号歌单（我喜欢置顶），点开进集合层可播。❓ 待真机验证接口与取链。
- 🔧 2026-11-15 网易云已接：eapi 加密抽到 core/plugin `login/NeteaseCrypto.kt`（扫码登录与账号读取共用）；core/data `account/NeteaseAccount.kt`（纯函数）：`/api/nuser/account/get` 拿 uid（code 200 无 profile 或 301/401/403 = 登录过期，照 Folia neteaseFailure），`/api/user/playlist` 读自建 + 收藏（「我喜欢的音乐」= specialType 5 且创建者是自己，置顶；收藏来的别人的「喜欢」不算），`/api/v6/playlist/detail` 取 trackIds + `/api/v3/song/detail` 每 400 首补全（Folia playlist/track/all 两步）。凭据 MUSIC_U / __csrf 存本机，0.0.30 只写进插件变量的回头迁移；缓存 files/account/netease_*.json，退出清掉。首页歌单页签按当前音源取舍（Folia withActiveProvider：当前平台有账号歌单只列它的，否则 QQ + 网易云都列），后接本地歌单。❓ 待真机验证接口（eapi 风控）与取链。
- 🔧 2026-11-15 酷狗已接：core/data `account/KugouAccount.kt`（纯函数）照 KuGouMusicApi `user_playlist.js` / `playlist_track_all.js` / `login_token.js` 与 util/helper.js 的安卓签名（盐 OIlwieks…，appid 1005 / clientver 20489，mid = calculateMid(md5(guid))）直连 gateway.kugou.com：POST `/v7/get_all_list`（x-router cloudlist.service.kugou.com，type 2，一页 100，source 2 / 带 musiclib_id 的专辑略过，「我喜欢」= listid 2 或同名，置顶；type 0 自建、1 收藏），GET `/pubsongs/v2/get_other_list_file_nofilt`（global_collection_id、begin_idx、一页 100）；error_code 152 / HTTP 401·403 = 登录失效（Folia kugouTransport），别的码算同步失败。扫码拿的是网页端（appid 1005 / srcappid 2919）token：KuGouMusicApi 就拿它直接签网关；Folia 手机版说会遇到 20017，那时照 login_token.js 用 `/v5/login_by_token`（AES p3 + 裸 RSA pk，secu_params 解出新 token）换安卓 token 再读一次，换到的存 `kg_app_token`（只认从当前扫码 token 换来的）。曲目 rawJson 带 hash / album_id / album_audio_id（插件与洛雪 kg 取链），platform 取已装酷狗插件的，没有写「酷狗」（LxPlatforms 顺手认中文平台名）。凭据 userid / token 存本机，0.0.30 只写进插件变量的回头迁移；缓存 files/account/kugou_*.json，退出清掉。首页歌单页签当前音源酷狗只列酷狗，不是三家时按 QQ、网易云、酷狗依次全列。自建歌单没封面时 Folia 会再拿第一首歌的封面，这里没做（显示占位色块）。❓ 待真机验证：网页 token 能否直接签网关、login_by_token 兜底、取链。


## 2026-11-17 晚：对照 folia-sonnet `src/styles/androidPhoneFit.css`（dev 分支）与 Folia ponder 源码
- 🔧 首页右上灯泡（Folia PagePonderTouchButton）：48dp 圆、落地/换页签露 4 秒后收起，右上 96dp 热区叫出；设置 → 外观「触屏思索按钮」。✋ Folia 的思索是合成舞台 + 光标动画，手机上改为真实首页挖洞高亮 + 说明卡（章节与文案照 grid-page scenes，键盘那章换成「平台与播放」）。
- 🔧 播放页胶囊竖屏展开态（Portrait phone fit）：宽 min(100vw-40, 22rem)，两行（左 32 播放键 · 歌名 12sp + 两侧箭头 · 右两槽 15 图标 / 进度条跨满）。✋ 触控区保持 40–44dp（Folia 32px / 槽 3px 内边距在手指下太小）。
- 🔧 播放页面板手柄竖屏（panel-toggle）：不再和胶囊挤一行，44dp 贴右缘、浮在胶囊上方（Folia bottom 120px）。横屏仍在胶囊右侧。
- 🔧 歌单详情信息面板横屏（① 侧切入面板）：贴 10/10、宽 min(320, 46vw)、内边距 14，封面压成 64dp 横幅，按钮 11sp、间距 4。竖屏不变。
- 🔧 本地搜索支持拼音（全拼 / 首字母）。Folia 本地搜索本身不做拼音（只命令窗 / 设置搜索用 pinyin-pro）；用户要求，按同思路加。
- ⏳ UI 尺度：sonnet 手机竖屏根字号 86%（横屏 76%，用户可调滑条）。广乐按 dp 原样，整体偏大一档的观感可能来自这里；要做需全局 density 缩放 + 设置滑条，单独排期。
- ⏳ 歌单选择弹窗（②）：横屏面板定高、标题钉住、列表交回外层滚。未核对广乐的歌单选择窗。
- ⏳ 正在播放卡片（now-playing toast）位置已照做（top 安全区 + 72、left 12）✅；Lattice / bravais 墙面左上返回键「挖孔下沿 + 6px」未核对。
