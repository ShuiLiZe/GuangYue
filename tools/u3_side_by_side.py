"""拼并排图：左 = 广乐 U3 搜索页手机截图，右 = Folia 基准搜索页（m412）。

照 tools/u2_side_by_side.py 改。两边都是 2px/dp，统一缩到 360dp 宽后同 dp 即同像素。

基准来源（docs/port/baseline）：
  - screenshots/search-results-m412-{light,dark}.png  ← Folia 搜索结果页（有结果的那一屏）
  - screenshots/search-empty-m412-light.png           ← Folia 零结果
  - screenshots-v2/m412-{light,dark}-search-typing.png ← 正在输入（v2 口径）
  - screenshots/search-idle-m412-light.png            ← Folia 桌面 idle 是「首页墙」，不是搜索结果页，
                                                        只用来对照顶栏搜索框那一行

用法：python tools/u3_side_by_side.py
"""
from PIL import Image, ImageDraw, ImageFont
import os

SHOTS = r"D:\workspaces\GuangYue\docs\port\baseline\screenshots"
SHOTS_V2 = r"D:\workspaces\GuangYue\docs\port\baseline\screenshots-v2"
OUT = r"D:\workspaces\GuangYue-u2\docs\ui-u3"

DP = 2.0
CARD_DP = 360
PAD = 28
HEAD_H = 84
BG = (247, 246, 245)
INK = (28, 25, 23)
INK_SOFT = (120, 113, 108)
LINE = (214, 211, 209)


def font(size=20, bold=False):
    names = ("msyhbd.ttc", "msyh.ttc") if bold else ("msyh.ttc", "Deng.ttf", "simhei.ttf")
    for name in names:
        for path in (os.path.join(r"C:\Windows\Fonts", name), name):
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                continue
    return ImageFont.load_default()


def to_dp_width(im, dp=CARD_DP):
    w = int(dp * DP)
    return im.resize((w, max(1, int(im.height * w / im.width))), Image.LANCZOS)


def ours(name):
    return to_dp_width(Image.open(os.path.join(OUT, name)).convert("RGB"))


def ours_crop(name, top_px, bottom_px):
    im = Image.open(os.path.join(OUT, name)).convert("RGB")
    return to_dp_width(im.crop((0, top_px, im.width, bottom_px)))


def baseline(name, box, v2=False):
    root = SHOTS_V2 if v2 else SHOTS
    return to_dp_width(Image.open(os.path.join(root, name)).convert("RGB").crop(box))


def compose(out_name, left_img, right_img, left_label, left_note, right_label, right_note):
    l, r = left_img, right_img
    h = max(l.height, r.height)
    gap = PAD * 2
    W = PAD + l.width + gap + r.width + PAD
    canvas = Image.new("RGB", (W, h + HEAD_H + PAD), BG)
    d = ImageDraw.Draw(canvas)
    fl, fs = font(21, bold=True), font(15)
    d.text((PAD, 14), left_label, fill=INK, font=fl)
    d.text((PAD, 44), left_note, fill=INK_SOFT, font=fs)
    rx = PAD + l.width + gap
    d.text((rx, 14), right_label, fill=INK, font=fl)
    d.text((rx, 44), right_note, fill=INK_SOFT, font=fs)
    canvas.paste(l, (PAD, HEAD_H))
    canvas.paste(r, (rx, HEAD_H))
    mid = PAD + l.width + PAD
    d.line([(mid, HEAD_H), (mid, h + HEAD_H)], fill=LINE, width=2)
    canvas.save(os.path.join(OUT, out_name))
    print(out_name, canvas.size)


# 1) 搜索结果页：整屏对照（Folia 整页只到内容区，手机版多一条底栏）
compose(
    "side-search-results-light.png",
    ours("u3-results-light.png"),
    baseline("search-results-m412-light.png", (0, 0, 824, 1830)),
    "广乐 · 搜索结果页（手机 360×800dp，浅色）",
    "来源 u3-results-light.png 整屏 · 含底栏与手势条",
    "Folia 基准 · 搜索结果页（412×915@2x）",
    "来源 screenshots/search-results-m412-light.png 整屏，缩到同 360dp",
)

# 2) 深色对照
compose(
    "side-search-results-dark.png",
    ours("u3-results-dark.png"),
    baseline("search-results-m412-dark.png", (0, 0, 824, 1830)),
    "广乐 · 搜索结果页（深色）",
    "来源 u3-results-dark.png 整屏",
    "Folia 基准 · 搜索结果页（深色）",
    "来源 screenshots/search-results-m412-dark.png 整屏",
)

# 3) 顶部（搜索框 + 音源胶囊）特写：Folia 的 header 区
compose(
    "side-search-header-light.png",
    ours_crop("u3-results-light.png", 0, 270),
    baseline("search-results-m412-light.png", (0, 0, 824, 540)),
    "广乐 · 搜索框 + 音源胶囊（48dp / 36dp）",
    "来源 u3-results-light.png 顶部 0–135dp",
    "Folia 基准 · 同区域（60dp 框 / 36dp 胶囊）",
    "来源 search-results-m412-light.png 顶部 0–270px",
)

# 4) 结果行特写：一行一卡（封面 48dp + 两行文字 + 右侧按钮）
compose(
    "side-search-row-light.png",
    ours_crop("u3-results-light.png", 320, 600),
    baseline("search-results-m412-light.png", (40, 620, 824, 900)),
    "广乐 · 结果行（卡片 68dp 起，两行标题）",
    "来源 u3-results-light.png 320–600px",
    "Folia 基准 · 结果行（76dp 行高，单行截断）",
    "来源 search-results-m412-light.png 620–900px",
)

# 5) 零结果
compose(
    "side-search-empty-light.png",
    ours("u3-empty-light.png"),
    baseline("search-empty-m412-light.png", (0, 0, 824, 1830)),
    "广乐 · 零结果（一句提示 + 可点推荐）",
    "来源 u3-empty-light.png 整屏",
    "Folia 基准 · 零结果（home.noResults 居中）",
    "来源 screenshots/search-empty-m412-light.png 整屏",
)

# 6) 输入中（v2 口径）
compose(
    "side-search-typing-light.png",
    ours("u3-loading-light.png"),
    baseline("m412-light-search-typing.png", (0, 0, 824, 1830), v2=True),
    "广乐 · 加载中（转圈 + 3 条骨架）",
    "来源 u3-loading-light.png 整屏",
    "Folia 基准 · 正在搜索（居中转圈）",
    "来源 screenshots-v2/m412-light-search-typing.png 整屏",
)
