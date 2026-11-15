# 广乐 vs Folia 手机版（APK folia-sonnet）逐屏差异清单

对照方法：模拟器 emulator-5554 上并排开 `top.izuna.foliamajor` 与 `com.guangyue.music`，每屏一张图。
**参照物是 APK，不是沙箱里的 Folia 源码**——源码比 APK 新（例如源码搜索页是至上主义构图，APK 是深色胶囊），两者冲突时以 APK 为准。

状态：✅ 已对齐　🔧 本批已修　⏳ 待修　❓ 需真机/网络　✋ 有意不同（理由见 HANDOFF）

## 首页
- 🔧 未登录态（63336cd 默认落歌单页签，空态即登录面板，胶囊直开登录窗；文案改用 Folia 原文）：Folia 显示「先搜几首喜欢的歌试试看」+ 说明 + 「登录到 网易云」胶囊（后跟各平台圆标），中间灰色人像方块。广乐未登录直接显示推荐卡片。→ 拟：无任何平台账号时首屏照搬 HomeLoginPanel，推荐降为其下方/页签内。
- ⏳ 页签：Folia 4 个（歌单/电台/专辑/本地，小字白胶囊），广乐 5 个（歌单/推荐/探索/专辑/本地）。「推荐」「探索」是广乐自加。
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
