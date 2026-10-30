"""拼并排图：左 = Folia 基准截图裁切，右 = 广乐 U1 组件截图（doc only，不入库）。
用法：python tools/u1_side_by_side.py
"""
from PIL import Image, ImageDraw, ImageFont
import os

BASE = r"D:\workspaces\GuangYue\docs\port\baseline\screenshots-v2"
OUT = r"D:\workspaces\GuangYue-u1\docs\ui-u1"
SCALE = 0.62          # 基准 824x1830 @2x → 与 720 宽的广乐截图同量级
PAD = 24
LABEL_H = 44


def font(size=20):
    for name in ("msyh.ttc", "Deng.ttf", "simhei.ttf", "arial.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def left_crop(name, box):
    im = Image.open(os.path.join(BASE, name)).convert("RGB")
    im = im.crop(box)
    return im.resize((int(im.width * SCALE), int(im.height * SCALE)), Image.LANCZOS)


def right(name):
    return Image.open(os.path.join(OUT, name)).convert("RGB")


def compose(out_name, left_name, left_box, right_img, left_label, right_label):
    l = left_crop(left_name, left_box)
    r = right_img
    h = max(l.height, r.height)
    W = PAD + l.width + PAD + r.width + PAD
    canvas = Image.new("RGB", (W, h + LABEL_H + PAD), (250, 250, 249))
    d = ImageDraw.Draw(canvas)
    f = font(20)
    d.text((PAD, 12), left_label, fill=(28, 25, 23), font=f)
    right_x = PAD + l.width + PAD * 2
    d.text((right_x, 12), right_label, fill=(28, 25, 23), font=f)
    canvas.paste(l, (PAD, LABEL_H))
    canvas.paste(r, (right_x, LABEL_H))
    d.line([(PAD + l.width + PAD // 2, LABEL_H), (PAD + l.width + PAD // 2, h + LABEL_H)],
           fill=(214, 211, 209), width=2)
    canvas.save(os.path.join(OUT, out_name))
    print(out_name, canvas.size)


compose(
    "side-card.png", "m412-light-home-playlists.png", (140, 560, 720, 1230),
    right("closeup-card-light.png"),
    "Folia 基准 · m412-light-home-playlists（封面卡）",
    "广乐 · FoliaCoverCard（2 列、16dp 圆角）",
)
compose(
    "side-row.png", "m412-light-panel-queue.png", (60, 1080, 800, 1530),
    right("closeup-row-light.png"),
    "Folia 基准 · m412-light-panel-queue（曲目行）",
    "广乐 · FoliaSongRow（56dp 行高、常显操作）",
)
compose(
    "side-controls.png", "m412-light-settings-playback.png", (60, 1250, 800, 1700),
    right("closeup-button-light.png"),
    "Folia 基准 · settings/playback（开关 / 分段）",
    "广乐 · FoliaToggle / FoliaSegmented / FoliaButton",
)
compose(
    "side-sheet.png", "m412-light-panel-queue.png", (60, 880, 800, 1660),
    right("gallery-light-4.png"),
    "Folia 基准 · m412-light-panel-queue（玻璃面板）",
    "广乐 · FoliaGlassPanel + FoliaBottomSheet",
)
compose(
    "side-dark-card.png", "m412-dark-home-playlists.png", (140, 560, 720, 1230),
    right("closeup-card-dark.png"),
    "Folia 基准 · m412-dark-home-playlists（封面卡）",
    "广乐 · FoliaCoverCard（深色）",
)
