# -*- coding: utf-8 -*-
"""U4：把 Folia 基准图与本项目截图拼成并排对照图（中文标注）。

左=Folia 基准（docs/port/baseline/screenshots 的 m412 两倍图，824x1830），
右=本项目 Roborazzi 截图（720x1600）。两图统一缩放到同高 1400px，加中文标题条。
"""
import os
from PIL import Image, ImageDraw, ImageFont

BASE = r"D:\workspaces\GuangYue\docs\port\baseline\screenshots"
BASE2 = r"D:\workspaces\GuangYue\docs\port\baseline\screenshots-v2"
OUT = r"D:\workspaces\GuangYue-u2\docs\ui-u4"
U4 = OUT

PAIRS = [
    ("u4-side-by-side-cover-light.png",
     os.path.join(BASE, "player-panel-cover-m412-light.png"),
     os.path.join(U4, "u4-playing-light.png"),
     "Folia 基准 · 播放页封面（浅色）", "广乐手机版 · 播放页（浅色）"),
    ("u4-side-by-side-cover-dark.png",
     os.path.join(BASE, "player-panel-cover-m412-dark.png"),
     os.path.join(U4, "u4-playing-dark.png"),
     "Folia 基准 · 播放页封面（深色）", "广乐手机版 · 播放页（深色）"),
    ("u4-side-by-side-queue-light.png",
     os.path.join(BASE2, "m412-light-panel-queue.png"),
     os.path.join(U4, "u4-queue-light.png"),
     "Folia 基准 · 播放队列（浅色）", "广乐手机版 · 播放队列（浅色）"),
]

TITLE_H = 76
GAP = 28
PAD = 28
TARGET_H = 1400
BG = (24, 24, 27)
INK = (244, 244, 245)
SUB = (168, 162, 158)

FONT_CANDIDATES = [
    r"C:\Windows\Fonts\msyh.ttc",
    r"C:\Windows\Fonts\msyhbd.ttc",
    r"C:\Windows\Fonts\simhei.ttf",
]


def font(size):
    for path in FONT_CANDIDATES:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                continue
    return ImageFont.load_default()


def load(path, target_h):
    im = Image.open(path).convert("RGB")
    w, h = im.size
    nw = int(round(w * target_h / h))
    return im.resize((nw, target_h), Image.LANCZOS)


def compose(out_name, left_path, right_path, left_label, right_label):
    left = load(left_path, TARGET_H)
    right = load(right_path, TARGET_H)
    width = PAD * 2 + left.width + GAP + right.width
    height = PAD + TITLE_H + TARGET_H + PAD
    canvas = Image.new("RGB", (width, height), BG)
    d = ImageDraw.Draw(canvas)
    f_title = font(30)
    f_sub = font(20)

    # 两栏顶部的说明 + 一条强调色下划线
    x_left = PAD
    x_right = PAD + left.width + GAP
    for x, w, label, sub in (
        (x_left, left.width, left_label, "824×1830（CSS 412×915 @2x）"),
        (x_right, right.width, right_label, "720×1600（360×800dp @xhdpi）"),
    ):
        d.text((x, PAD + 4), label, font=f_title, fill=INK)
        d.text((x, PAD + 42), sub, font=f_sub, fill=SUB)
        d.rectangle([x, PAD + TITLE_H - 10, x + w, PAD + TITLE_H - 6], fill=(234, 88, 12))

    canvas.paste(left, (x_left, PAD + TITLE_H))
    canvas.paste(right, (x_right, PAD + TITLE_H))
    out = os.path.join(OUT, out_name)
    canvas.save(out)
    print(out, canvas.size)


for name, l, r, ll, rl in PAIRS:
    if not os.path.exists(l):
        print("missing baseline:", l)
        continue
    compose(name, l, r, ll, rl)
