# 广乐 vs Folia 手机版（APK folia-sonnet）逐屏差异清单

对照方法：模拟器 emulator-5554 上并排开 `top.izuna.foliamajor` 与 `com.guangyue.music`，每屏一张图。
**参照物是 APK，不是沙箱里的 Folia 源码**——源码比 APK 新（例如源码搜索页是至上主义构图，APK 是深色胶囊），两者冲突时以 APK 为准。

状态：✅ 已对齐　🔧 本批已修　⏳ 待修　❓ 需真机/网络　✋ 有意不同（理由见 HANDOFF）

## 首页
- 🔧 未登录态（63336cd 默认落歌单页签，空态即登录面板，胶囊直开登录窗；文案改用 Folia 原文）：Folia 显示「先搜几首喜欢的歌试试看」+ 说明 + 「登录到 网易云」胶囊（后跟各平台圆标），中间灰色人像方块。广乐未登录直接显示推荐卡片。→ 拟：无任何平台账号时首屏照搬 HomeLoginPanel，推荐降为其下方/页签内。
- ✅ 页签：Folia 4 个（歌单/电台/专辑/本地），广乐 0.0.29 起同（推荐/探索已去）。
- 🔧 右下平台按钮：Folia 是平台真 logo 圆标；广乐原是「网」字圆标。11-18 照 folia-sonnet appIcons.ts / PlatformMark：认得出的平台亮本尊图标（铺满裁圆、不套色底），认不出才回落站点图标 / 首字（ProviderLogos；平台按钮、切换菜单、登录胶囊、扫码登录窗角标、插件列表同一张表）。
- ✋ 标题字重：两边都是 Bold，观感差来自中文字形。
- ✅ 顶栏布局（标题+齿轮+右侧搜索胶囊）。

## 搜索
- 🔧 6a5640a/5f72a6d 按源码做成纸色至上主义，**与 APK 不符**；已改回 APK 样式：纯深色底、圆胶囊输入框（左放大镜）、右侧独立 48dp 圆形关闭钮、来源胶囊选中白底黑字。
- 🔧 进页自动聚焦，修首字被吞。
- 🔧 11-18 结果列表行照 folia-sonnet 源码 `SearchResultRow.tsx` + `search.css`（APK 结果仍因模拟器 TLS 加载不出，❓ 真机再对一次）：平铺行 76dp、下沿发丝线（ink 9%）、左 18 内边距、间距 10；窄屏无序号与时长；方角 48 封面；歌名 15 粗体一行；第二行 12sp「歌手 / 专辑」；行尾 收藏到歌单（Star）→ 加入播放队列（Plus，新接 PlaybackController.appendTracks）。原先是圆角卡片 + 来源角标 + 两行歌名。✋ 命中 44dp（Folia 窄屏 30px）；✋ 在线结果多一颗下载；✋ 速记簿广乐没有。
- 🔧 11-18 专辑 / 歌手页签照 SearchCollectionRow：平铺行 84dp、58 方角封面、名字 15 粗体一行、「歌手 / 年份」、行尾 ↗。✋ 唱片 / 主题色影子只在悬停露出，触屏不画；✋ Folia 搜索没有歌手页签，广乐保留（头像圆形，第二行粉丝数）。
- 🔧 11-18 来源条 / 种类页签照 folia-sonnet `SearchTabs.tsx` + `search.css`（用户指定）：来源 = 纯文字页签 13sp 粗体字距 0.06em、间距 22、选中墨色 + 底下 3dp 强调色条（淡入）；种类 = 「01」等宽编号 + 18sp 900 粗大字、间距 14、选中前 11dp 实心强调色方块。原先来源是胶囊、种类是分段控件带计数。✋ 页签不再显示数量；✋ 方块 / 色条用淡入缩放代替 framer layoutId 滑动。
- ✋ 来源列表：Folia 内置 网易云/酷狗/QQ音乐/波点/飞牛/Local；广乐来自插件订阅，按插件显示。

## 设置
- ✅ 大窗外壳、帮助/选项页签、分类胶囊、分组卡片（5f72a6d）。
- 🔧 Folia 首次打开落在「帮助」页（新功能 / Ponder 帮助 / 快捷键 / 播放页控制 / 求助 / 加入社区 / 项目链接）；广乐落在「选项」。
- 🔧 11-17 已改 选项分类：Folia「视觉设置 / 通用设置 / …」，视觉页头右侧圆形月亮钮切深浅色，首项「歌词动画调整」描边卡片+箭头，其后「歌词壁纸」（设为系统动态壁纸、壁纸样式 极简/跟随、可视化浮层）。广乐分类为 外观/播放页/播放控制…，口径不同。
  → 照 Folia settingsNavModel 改为 外观 / 播放页 / 主页与歌单 / 播放控制 / 通用 / 存储与备份（b53cf62）：资料库界面、歌单卡片样式、顶部胶囊入口、记住主页卡片位置在「主页与歌单」，触屏思索按钮在「通用」。✋ Folia 的 动画与性能 / 歌词 / 快捷操作 / 连接与集成 / 桌面 / 模组 / 开发者 页广乐暂无对应项，不建空页。
- 🔧 11-18 补三页（顺序照 settingsNavModel：外观 / 播放页 / 主页与歌单 / **动画与性能** / 播放控制 / **歌词** / 通用 / **连接与集成** / 存储与备份）：
  - 动画与性能（GraphicsPage）：「性能与背景」静态模式（隐现背景停静帧、通用几何背景不画）、关闭主页动态背景；「降低动态效果」跟随系统设置。✋ 跟随系统 Folia 默认关、广乐默认开（Android「移除动画」是无障碍开关，广乐一直跟随）。✋ 各分区开关（队列拼贴 / 混音过渡 / 歌单展开转场 / Monet 背景漂移 / 界面微动效 / 设置页平滑滚动）、帧率限制、歌词渲染比例 / 抗锯齿、原生模糊、辉光修复广乐没有对应实现（Compose 原生渲染，无 WebGL 画布），不列。
  - 歌词（LyricsPage）：「时间与过滤」全局时间偏移（±2000ms，与每首偏移相加，步进 ±50 / ±10 + 归零）。🔧 「歌词过滤」手机子集（core/data LyricLineRules，照 lyricLineRules.ts）：常用预设 版权声明 / 歌词来源说明（词表原样）、包含这些文字（不分大小写、全半角）、手写正则（写错不删任何行）；只在显示时套用，改了当前歌词立刻重排。✋ 开头是 / 整行是 / 结尾是、后接冒号、仅首尾、点行删同类、听过的歌预览暂不做。✋ 歌词来源（自动最佳 / 匹配优先 / 逐字时间 / 本地优先）、AI 分词、本地格式顺序：广乐歌词全走音源插件，暂无对应。
  - 连接与集成（IntegrationPage）：音源插件管理、取链源默认音质从「播放控制」移来（Folia 外接音乐服务在这一页）。✋ Discord / OBS / 舞台模式 / 同步服务器 / 歌词 API 桌面专属，不做。
  - ✋ 快捷操作（InteractionPage）：命令面板固定、Alt 快捷键、网格滑动目标全是键盘 / 命令面板，手机无对应，不建页。
- 🔧 11-17 外观「主题 → 明暗模式」照 Folia AppearancePage 改为分段 浅色 / 深色 / 跟随系统（原先只有深色开关，跟随系统只能靠从没碰过开关）；手动选浅/深即不再跟随，文案照 followSystemThemeDesc。
- ✋ 设置内搜索已去掉（APK 无）。

## 登录
- 🔧 扫码登录窗（ee35e60）。❓ 取码/轮询/写凭据需真机。

## 隐现背景
- 🔧 四格插值去硬格 + 浓度 0.2（ee35e60）。🔧 def5e4e 整层加亚像素模糊（半径≈density/1.79×0.7px）模拟 Folia WebGL 低分辨率渲染后放大的柔边。❓ 真机观感待确认。

## 播放页 / 歌词 / 町景
- ❓ 需要能出声的歌，模拟器网络失败，待真机。
- 11-18 对照 folia-sonnet `components/visualizer/usePlayerChrome.tsx` / `VisualizerShell.tsx` + androidPhoneFit.css：
  - 🔧 左上返回键：Folia 40 圆、bg-black/20、白 60%、Chevron 20，两种明暗一样；广乐原是 48 圆 + 前景色 12% 底。改成 Folia 外观，命中保留 48dp。
  - ✋ 返回键显隐：Folia 平时隐藏、鼠标进左上 120px 热区才淡入；触屏没有悬停，常驻（同 WallBackButton 的 `hover: none` 规则）。
  - ✋ 顶栏歌名 / 歌手行、横竖屏切换键、样式键：Folia 没有顶栏（歌名在各样式自己的画面里，样式在面板里）。横竖屏键是 11-08 用户要求；歌名与样式键手机上找不到别的入口，暂留，待用户定。
  - ⏳ 返回键位置：Folia `top-6 left-6`，安卓再让「安全区 + 6px」（挖孔下沿再 6px）；广乐在顶栏 Row 里（安全区 + 4 + 居中于 56 行高 ≈ 安全区 + 8，左 pageH）。差约 20dp，等顶栏去留定了一起改。

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
- ✅ 歌单选择弹窗（②）：11-14 已做横屏紧凑（标题并一行、列表按剩余高度封顶可滚、间距收档；取证页 playlistpick）。
- ✅ 正在播放卡片（now-playing toast）位置已照做（top 安全区 + 72、left 12）。
- 11-18 墙面左上返回键核对（folia-sonnet androidPhoneFit.css + WallBackButton.css）：「挖孔下沿 + 6px」只管**播放页**那颗（`[data-folia-player-back]`，top-6 left-6 再让 安全区+6）；墙面那颗 `.lattice-back` 是 40px 圆、白 8%（日间黑 5%）、Chevron 20，手机（≤640px）`top:14px left:16px`，再让 安全区 top / left。
  - ✅ 网格歌单详情（GridView）返回：Folia `left-6 top-5` + 安全区 top，广乐 statusBars + 20 / start 24、40dp、同底色，一致。
  - ✋ bravais 墙：Folia 的缝开在墙中间，左上角露墙，才有那颗「隐藏式」返回（触屏常驻，z 在缝之下：非根层 = 缝里 ‹ 同一个返回，根层有歌 = 回到播放页）。广乐墙面的缝固定在顶部（竖屏集合层是左侧竖缝），左上角永远被缝盖住，照 Folia 的层级它会在缝下面看不见；返回由缝里的 ‹ 面包屑承担（缝内容已让 statusBars，同 `.bravais-seam-body` padding-top 安全区），不另加。
- 🔧 11-17 横屏思索「轨道」一步：高亮整条轨道时上下空位都放不下完整说明卡，原来卡盖住轨道下半；改为紧凑说明卡（章节进页头一行、说明与翻页并排，约 100dp）放进较大空位（b53cf62）。
- 🔧 11-17 「主页与歌单 → 网格卡片」照 Folia HomePage：卡片最小尺寸（默认 45%，20–110%）、卡片最小透明度（默认 40%，0–100%）滑条 + 「恢复默认衰减」，作用于歌单详情集合网格（hexCardFalloff）。✋ 预览小图（GridCardsPreview）未做，直接看歌单网格。
- 🔧 11-17 「网格卡片 → 全画幅封面 / 正方形卡片」照 Folia PolaroidCard fullBleedCover（封面铺满 + 0/30/74/93% 渐变，字在底部）与 squareGridCardBox（面积不变的正方形，间距同比）；正方形只在全画幅打开后出现。取证页 gridcollectionbleed。
