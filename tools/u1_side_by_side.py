"""拼并排图：左 = Folia 基准截图裁切，右 = 广乐组件截图（doc only，不入库）。

U1b 重做（Hark 审查第 4 条：旧版左边裁太小、和右边整屏图尺度对不上）：
  - 基准截图 824×1830 是 @2x（412dp 宽），广乐截图 720×1600 是 @xhdpi（360dp 宽），
    两边**每 dp 的像素数都是 2**，所以只要都把目标组件的紧包围盒裁出来，
    再统一缩放到 360dp 宽（基准 ×0.874），同样的 dp 就会渲染成同样的像素，
    圆角半径、投影扩散、行高可以直接用眼睛量。
  - 右栏的 closeup-*.png 已由 FoliaGalleryScreenshotTest.shootFit 裁到内容底边，
    不再是整屏 800dp。
  - 标注全中文；左栏副标题写明来源文件与裁切框，右栏写明关键 token / 交互，便于复核。

用法：python tools/u1_side_by_side.py
"""
from PIL import Image, ImageDraw, ImageFont
import os

BASE = r"D:\workspaces\GuangYue\docs\port\baseline\screenshots-v2"  # 824×1830 @2x = 412dp
OUT = r"D:\workspaces\GuangYue-u1\docs\ui-u1"

DP = 2.0             # 两边都是 2px/dp
CARD_DP = 360        # 统一缩到 360dp 宽
PAD = 28
HEAD_H = 84          # 顶部两行标注
BG = (247, 246, 245)
INK = (28, 25, 23)
INK_SOFT = (120, 113, 108)
LINE = (214, 211, 209)


def font(size=20, bold=False):
    names = ("msyhbd.ttc", "msyh.ttc") if bold else ("msyh.ttc", "Deng.ttf", "simhei.ttf")
    for name in names:
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def to_dp_width(im, dp=CARD_DP):
    w = int(dp * DP)
    return im.resize((w, max(1, int(im.height * w / im.width))), Image.LANCZOS)


def tight(im, bg_tol=14):
    """按内容包围盒紧裁（浅色页面底），避免大片空白把两栏拉得高低不齐。"""
    rgb = im.convert("RGB")
    w, h = rgb.size
    px = rgb.load()
    base = px[1, 1]

    def differs(c):
        return (abs(c[0] - base[0]) + abs(c[1] - base[1]) + abs(c[2] - base[2])) > bg_tol

    top = next((y for y in range(h) if any(differs(px[x, y]) for x in range(0, w, 4))), 0)
    bottom = next((y for y in range(h - 1, -1, -1) if any(differs(px[x, y]) for x in range(0, w, 4))), h - 1)
    left = next((x for x in range(w) if any(differs(px[x, y]) for y in range(0, h, 4))), 0)
    right = next((x for x in range(w - 1, -1, -1) if any(differs(px[x, y]) for y in range(0, h, 4))), w - 1)
    if bottom <= top:
        return im
    return im.crop((max(0, left - 4), max(0, top - 4), min(w, right + 5), min(h, bottom + 5)))


def baseline(name, box, trim=False):
    im = Image.open(os.path.join(BASE, name)).convert("RGB").crop(box)
    if trim:
        im = tight(im)
    return to_dp_width(im)


def ours(name, trim=True):
    im = Image.open(os.path.join(OUT, name)).convert("RGB")
    if trim:
        im = tight(im)
    return to_dp_width(im)


def compose(out_name, left_name, left_box, right_img, left_label, right_label, right_note, trim):
    l = baseline(left_name, left_box, trim)
    r = right_img
    h = max(l.height, r.height)
    gap = PAD * 2
    W = PAD + l.width + gap + r.width + PAD
    canvas = Image.new("RGB", (W, h + HEAD_H + PAD), BG)
    d = ImageDraw.Draw(canvas)
    fl, fs = font(21, bold=True), font(15)
    d.text((PAD, 14), left_label, fill=INK, font=fl)
    d.text((PAD, 44), f"来源 {left_name}　裁切 {left_box}", fill=INK_SOFT, font=fs)
    rx = PAD + l.width + gap
    d.text((rx, 14), right_label, fill=INK, font=fl)
    d.text((rx, 44), right_note, fill=INK_SOFT, font=fs)
    canvas.paste(l, (PAD, HEAD_H))
    canvas.paste(r, (rx, HEAD_H))
    mid = PAD + l.width + PAD
    d.line([(mid, HEAD_H), (mid, h + HEAD_H)], fill=LINE, width=2)
    canvas.save(os.path.join(OUT, out_name))
    print(out_name, canvas.size)


# 1. 封面卡（浅色）：Folia 首页 Daily Mix 卡 vs 广乐 2 列卡片
compose(
    "side-card.png", "m412-light-home-playlists.png", (100, 600, 820, 1230),
    ours("closeup-card-light.png"),
    "Folia 基准 · 首页封面卡",
    "广乐 · 封面卡 FoliaCoverCard",
    "白纸底 94% · 16dp 柔和投影 · 封面 1dp 细亮描边",
    trim=True,
)

# 2. 封面卡（深色）
compose(
    "side-dark-card.png", "m412-dark-home-playlists.png", (100, 600, 820, 1230),
    ours("closeup-card-dark.png"),
    "Folia 基准 · 首页封面卡（深色）",
    "广乐 · 封面卡（深色）",
    "深色底 88% · 45% 黑投影 · 圆角 16dp",
    trim=True,
)

# 3. 队列行：Folia 播放列表面板 vs 广乐玻璃面板 + 选中行橙竖条
compose(
    "side-row.png", "m412-light-panel-queue.png", (60, 760, 800, 1560),
    ours("closeup-row-light.png"),
    "Folia 基准 · 播放队列（玻璃面板）",
    "广乐 · 队列面板 + 歌曲行",
    "选中「稻香」：浅底 + 左侧 3×18dp 橙竖条 + 名称加粗",
    trim=False,
)

# 4. 控件：开关 / 分段。Folia 设置面板的开关行 vs 广乐控件页特写
compose(
    "side-controls.png", "m412-light-settings-playback.png", (300, 1180, 824, 1560),
    ours("closeup-button-light.png"),
    "Folia 基准 · 设置面板开关行",
    "广乐 · 按钮 / 开关 / 分段 / 标签页",
    "禁用按钮整组降至 40% 透明（不再橙底淡字）",
    trim=True,
)

# 5. 浮层：Folia 玻璃面板 vs 广乐毛玻璃 + 底部弹层
compose(
    "side-sheet.png", "m412-light-panel-queue.png", (60, 760, 800, 1660),
    ours("gallery-light-4.png", trim=False),
    "Folia 基准 · 玻璃面板浮层",
    "广乐 · 毛玻璃面板 + 底部弹层",
    "截图为 API<31 降级态，见 report §11",
    trim=False,
)

# 6. 新增：搜索框并排（Folia 顶栏胶囊搜索 vs 广乐 FoliaSearchField）
compose(
    "side-search.png", "m412-light-search-typing.png", (0, 40, 824, 360),
    ours("gallery-light-2.png", trim=False),
    "Folia 基准 · 顶栏搜索框（胶囊）",
    "广乐 · 搜索框 FoliaSearchField（48dp 高）",
    "占位符「搜索在线歌曲...」取自 zh-CN.ts searchDatabase",
    trim=False,
)

# 7. 新增：开关 / 分段并排（深色）
compose(
    "side-dark-controls.png", "m412-dark-settings-playback.png", (300, 1180, 824, 1560),
    ours("closeup-button-dark.png"),
    "Folia 基准 · 深色设置面板（开关 / 分段）",
    "广乐 · 开关 / 分段（深色）",
    "轨道 40×24dp、旋钮 18dp、触控区 48dp",
    trim=True,
)
