"""拼并排图：左 = 广乐 U2 手机外壳，右 = Folia 基准（m412）。

照 tools/u1_side_by_side.py 改。两边都是 2px/dp，统一缩到 360dp 宽后同 dp 即同像素。
用法：python tools/u2_side_by_side.py
"""
from PIL import Image, ImageDraw, ImageFont
import os

BASE = r"D:\workspaces\GuangYue\docs\port\baseline\screenshots-v2"
OUT = r"D:\workspaces\GuangYue-u2\docs\ui-u2"

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


def crop_bottom(im, dp=130):
    h = int(dp * DP)
    if im.height <= h:
        return im
    return im.crop((0, im.height - h, im.width, im.height))


def ours(name):
    return to_dp_width(Image.open(os.path.join(OUT, name)).convert("RGB"))


def ours_crop(name, top_px, bottom_px):
    im = Image.open(os.path.join(OUT, name)).convert("RGB").crop((0, top_px, 720, bottom_px))
    return to_dp_width(im)


def tight(im, tol=12):
    """按内容包围盒紧裁（去掉底部工具条下方的白边）。"""
    rgb = im.convert("RGB")
    w, h = rgb.size
    px = rgb.load()
    base = px[2, 2]

    def differs(c):
        return abs(c[0] - base[0]) + abs(c[1] - base[1]) + abs(c[2] - base[2]) > tol

    bottom = next((y for y in range(h - 1, -1, -1) if any(differs(px[x, y]) for x in range(0, w, 4))), h - 1)
    if bottom <= 0:
        return im
    return im.crop((0, 0, w, min(h, bottom + 6)))


def baseline(name, box, trim=False):
    im = Image.open(os.path.join(BASE, name)).convert("RGB").crop(box)
    if trim:
        im = tight(im)
    return to_dp_width(im)


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


compose(
    "side-mini-light.png",
    ours_crop("u2-mini-light.png", 1268, 1372),
    baseline("m412-light-mini-bar.png", (0, 1484, 824, 1830), trim=True),
    "广乐 · 迷你条（56dp，浅色）",
    "来源 u2-mini-light.png 裁 1268–1372px",
    "Folia 基准 · 桌面迷你条（89dp）",
    "来源 m412-light-mini-bar.png 裁 1484–1830px · 同 360dp",
)

compose(
    "side-mini-dark.png",
    ours_crop("u2-mini-dark.png", 1268, 1372),
    baseline("m412-dark-mini-bar.png", (0, 1484, 824, 1830), trim=True),
    "广乐 · 迷你条（56dp，深色）",
    "来源 u2-mini-dark.png 裁 1268–1372px",
    "Folia 基准 · 桌面迷你条（深色）",
    "来源 m412-dark-mini-bar.png 裁 1484–1830px",
)

compose(
    "side-shell-light.png",
    crop_bottom(ours("u2-mini-light.png"), 190),
    crop_bottom(baseline("m412-light-mini-bar.png", (0, 0, 824, 1830)), 190),
    "广乐 · 迷你条 + 底部三 Tab（手机外壳）",
    "来源 u2-mini-light.png 底部 190dp · 手势条 24dp",
    "Folia 基准 · 同高度切一条（桌面态）",
    "来源 m412-light-mini-bar.png 底部 190dp",
)

compose(
    "side-home-light.png",
    ours("u2-home-light.png"),
    baseline("m412-light-home-playlists.png", (0, 0, 824, 1830)),
    "广乐 · 首页 Tab（浅色）",
    "来源 u2-home-light.png 整屏 360×800dp",
    "Folia 基准 · 桌面首页",
    "来源 m412-light-home-playlists.png 整屏",
)

compose(
    "side-nav.png",
    crop_bottom(ours("u2-mini-light.png"), 110),
    baseline("m412-light-home-playlists.png", (0, 1620, 824, 1830)),
    "广乐 · 底栏特写（选中态 raise 胶囊）",
    "来源 u2-mini-light.png 底部 110dp",
    "Folia 基准 · 桌面侧栏/工具条区域",
    "来源 m412-light-home-playlists.png 底部",
)