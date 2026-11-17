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
- 🔧 进页自动聚焦，修首字被吞。11-18：已有结果时（返回本页 / 横屏）不再自动弹键盘，免得盖住结果。
- 🔧 11-18 结果列表行照 folia-sonnet 源码 `SearchResultRow.tsx` + `search.css`（APK 结果仍因模拟器 TLS 加载不出，❓ 真机再对一次）：平铺行 76dp、下沿发丝线（ink 9%）、左 18 内边距、间距 10；窄屏无序号与时长；方角 48 封面；歌名 15 粗体一行；第二行 12sp「歌手 / 专辑」；行尾 收藏到歌单（Star）→ 加入播放队列（Plus，新接 PlaybackController.appendTracks）。原先是圆角卡片 + 来源角标 + 两行歌名。✋ 命中 44dp（Folia 窄屏 30px）；✋ 在线结果多一颗下载；✋ 速记簿广乐没有。
- 🔧 11-18 专辑 / 歌手页签照 SearchCollectionRow：平铺行 84dp、58 方角封面、名字 15 粗体一行、「歌手 / 年份」、行尾 ↗。✋ 唱片 / 主题色影子只在悬停露出，触屏不画；✋ Folia 搜索没有歌手页签，广乐保留（头像圆形，第二行粉丝数）。
- 🔧 11-18 来源条 / 种类页签照 folia-sonnet `SearchTabs.tsx` + `search.css`（用户指定）：来源 = 纯文字页签 13sp 粗体字距 0.06em、间距 22、选中墨色 + 底下 3dp 强调色条（淡入）；种类 = 「01」等宽编号 + 18sp 900 粗大字、间距 14、选中前 11dp 实心强调色方块。原先来源是胶囊、种类是分段控件带计数。✋ 页签不再显示数量；✋ 方块 / 色条用淡入缩放代替 framer layoutId 滑动。
- 🔧 11-18 状态行照 `.search-meta`：页签下一行「来源名　共 N 条」（等宽 11sp、字距 0.1em、淡墨，N 还能翻页时带 +），后接 1dp 发丝线到右缘（SearchMetaTest）。
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
  - 🔧 11-18 返回键位置已对齐：移出顶栏，按 Folia 绝对定位（圆顶 = 安全区 + 6 + 24dp，左 24dp，命中 48 向外让 4）；顶栏原位留 48dp 空位，歌名 / 横竖屏键 / 样式键不动（去留待用户定）。原记：Folia `top-6 left-6`，安卓再让「安全区 + 6px」（挖孔下沿再 6px）；广乐在顶栏 Row 里（安全区 + 4 + 居中于 56 行高 ≈ 安全区 + 8，左 pageH）。差约 20dp，等顶栏去留定了一起改。

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

## 2026-11-18 播放页面板「播放队列」页签照 Folia panelTab/QueueTab.tsx
- 🔧 页签名保持「播放列表」（Folia zh-CN panel.playlist）；头一行「播放队列 + 数目」（36dp，13 半粗 t2 / 数目 t3）；行 48dp、圆角 8、左 12 右 6，正在播的行 fill 底 + 左缘 3dp 强调色竖条，歌名 13/19 不改色，歌手 12/17 t3；去掉序号列与音浪图标。空队列 = ListMusic 28 +「播放列表为空」。✋ Folia 行操作（下一首播放 / 移到队尾 / 删除）靠悬停露出，触屏常驻删除（Trash2，命中 40dp）。
- 🔧 行操作照 Folia 补「下一首播放」（ListPlus）「移到队尾」（ListEnd），触屏常驻（命中 34×40dp）；PlaybackController.moveTo / moveToNext / moveToEnd。
- 🐞 修：点队列里的歌原走 playNow → setMediaItem 把播放器列表压成一首，与队列对不上（下一首失效、删行错位）；改为 playAt(index) 只换当前。

## 2026-11-18 早：播放列表头行、网格歌手页、歌单详情曲目侧栏
- 🔧 播放页面板「播放列表」头行照 QueueTab：「保存为歌单」（起名 → 新建歌单按队列顺序写入）+「打乱队列」（正在播挪队首、其余洗牌，不打断当前曲；不是随机模式）。✋ Folia 只给全本地队列开「保存为歌单」，广乐歌单按 key 存，在线曲也能存。打开 / 换歌时把正在播的前一行滚到顶。
- 🔧 搜索横屏空页不再自动弹键盘。
- 🔧 网格歌手页照 `suites/grid/artist/ArtistGridView.tsx`：资料库界面 = 网格时歌手页不再落墙面；头像圆卡 200 + 简介卡 360×200 居中一行（buildArtistGridCoords 对拍），热门歌曲上半、专辑下半，开场对准简介卡；水印「时下流行 / POPULAR」「歌手专辑 / ALBUMS」；返回键旁「热门歌曲加入队列」胶囊；标题副标题「艺术家」；右下列表键列专辑（点行居中后 320ms 进专辑）；点居中简介卡开 BIOGRAPHY 全文浮层。简介按能放下的整行数排（Folia overflow-hidden 会把行拦腰切）。✋「全部歌曲」胶囊未做：插件歌手作品已翻页合并，没有单独的全部歌曲层。
- 🔧 歌单详情曲目侧栏照 sonnet `GridViewTracksPanel` / `SidePanelList` / `TrackListItem`：右上浮出（右 20、宽 min(276, 屏宽 − 42)、圆角 21、内边距 20，按 86% 根字号折算）；头行 标题 + 定位（本页有正在播时点亮强调色）+ 关闭；行高 60、焦点行（= 居中那张卡）10% 底、封面 34 圆角 4、歌名 12 半粗、歌手 10 / 60%（只取第一位）；点行 = 播放这首（原先只是居中），行尾「加入播放队列」触屏常驻（Folia 悬停才露）；打开时先跳到焦点前 8 行再平滑滚到居中。✋ 名称排序 / 全部加入队列两颗是广乐多的。
- 🔧 右下列表键上方补「在此定位」（GridLocateButton）：34 圆、60% 不透明、窄屏半让出屏，正在播的歌在本页时出现、十字强调色，按下居中那张卡并放一圈涟漪，侧栏开着也滚到那行。✋ 正在播的歌不在本页时 Folia 也画、按下跳去那首歌所在的页；广乐没有这条路，不画。
- 🔧 播放页面板「控制」页签照 `panelTab/ControlsTab.tsx`：原先只有「播放模式」三段 + 歌词样式一行。现为 音量行（VolumeRow：静音键 Volume2/1/X · 4dp 轨强调色填充 + 16dp 白钮滑杆 · 百分比）→ 三块（QuickTile 64dp 圆角 10，亮起 = raise 底 + 图标强调色）：循环（名字即状态 关闭循环 / 列表循环 / 单曲循环，点一下 关→列表→单曲，新增列表循环）、随机播放、音频效果器（打开系统均衡器，带箭头）→ 分组线 → 歌词样式取景器（ModeStepperRow：‹ 48×30 字形框「歌词样式」/ 当前名 ▾ ›，两侧箭头直接换样式，点中间开样式面板）。✋ 第二块 Folia 是「智能过渡」，广乐没有，换成随机播放；✋ 样式选择 Folia 就地展开小图网格，广乐沿用样式面板；✋ 每种样式自己的字形未搬（统一画三道横线）；✋ 背景类型、播放页透明背景、主题来源 / 主题名两行暂无对应。音量不持久化。
- 🔧 设置各页行样式照 Folia `modal/settings/kit`（Group / Row / kitTheme）：窗底 --st-win（#1a1a1d / #f3f3f4），分组卡 --st-card（#232327 / 白）圆角 12 + 1px 线；组标题 13 半粗 t2、去掉图标与字距；组间 28（首组 20）；行最低 56、内边距 16 / 10，标签 14 常规（原 15 半粗）、说明 12 / 18 t2；行间发丝线从左 16 起。
- 🔧 11-18 墙面「在此定位」照 `suites/bravais/BravaisStageChrome.tsx` above = `GridLocateButton`：正在播的那块在本层墙上时右下出现（34 圆 60% 不透明、半让出屏），按下展开那块（locate-playing → showSlot）+ 涟漪。✋ Folia 叠在工具键上方，广乐墙面没有工具键，单独放右下；歌不在本层时不画（广乐没有定位器跨页跳转）。
- 🔧 11-18 歌词样式面板的样式列表照 VisPlayground 样式架 `kit/PictureGrid`（窄窗 4 列）：16:10 画框（--st-pv 底 + 1px 线、圆角 8）+ 名字 12/18，选中 = 框外 3 一圈 2dp 强调色环、名字半粗；头行「歌词样式 N」。✋ 框里画各样式 entry.tsx 的线性字形（modeGlyphs），不是实时小预览（GL 样式一屏起 16 个太重）；没有「随机」项（广乐无每首随机样式）。
- ✋ 11-18 歌单详情信息面板的收藏星（订阅歌单 / 专辑）与创建者行：广乐没有在线歌单订阅接口与创建者数据，未做；专辑曲目序号（albumTrackLabel）需本地曲库存音轨号 / 碟号（TrackEntity 无此列，要迁库），未做。
