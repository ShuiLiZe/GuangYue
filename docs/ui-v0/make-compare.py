# 生成 docs/ui-v0/folia-vs-guangyue-*.png：Folia（左）与广乐（右）并排对照。
#
# 左：Folia 真机渲染基线（D:\workspaces\GuangYue\docs\port\baseline\screenshots\player-playing-m412-*.png）
# 右：Roborazzi 拍下的广乐流光样式（docs/ui-v0/v0-classic-*-*.png，720x1600）
# 统一高度、等高裁剪、加标题条与分隔线，便于一眼比对同一句歌词的三态。
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

OUT = Path(r"D:\workspaces\GuangYue-u2\docs\ui-v0")
FOLIA = Path(r"D:\workspaces\GuangYue\docs\port\baseline\screenshots")

ROWS = [
    ("waiting", "v0-classic-waiting-{m}.png"),
    ("active", "v0-classic-active-{m}.png"),
    ("passed", "v0-classic-passed-{m}.png"),
]

TARGET_H = 1200
HEADER = 64
GAP = 24
BG = (26, 26, 26)
FG = (235, 235, 235)
MUTED = (150, 150, 150)


def load_scaled(path: Path) -> Image.Image:
    img = Image.open(path).convert("RGB")
    w = round(img.width * TARGET_H / img.height)
    return img.resize((w, TARGET_H), Image.LANCZOS)


def font(size: int):
    for cand in (r"C:\Windows\Fonts\msyh.ttc", r"C:\Windows\Fonts\msyhbd.ttc", r"C:\Windows\Fonts\simhei.ttf"):
        try:
            return ImageFont.truetype(cand, size)
        except OSError:
            continue
    return ImageFont.load_default()


def build(mode_is_dark: bool) -> Path:
    m = "dark" if mode_is_dark else "light"
    folia = load_scaled(FOLIA / f"player-playing-m412-{m}.png")
    canvas_w = GAP + folia.width + GAP + folia.width + GAP
    canvas_h = HEADER + GAP + len(ROWS) * (TARGET_H + GAP + HEADER)
    canvas = Image.new("RGB", (canvas_w, canvas_h), BG)
    draw = ImageDraw.Draw(canvas)
    f_title = font(34)
    f_sub = font(26)

    draw.text((GAP, 16), f"Folia v0.7.25（真机基线，{m}）", font=f_title, fill=FG)
    draw.text((GAP + folia.width + GAP, 16), f"广乐 V0 流光（Roborazzi，{m}）", font=f_title, fill=FG)

    for i, (_, pattern) in enumerate(ROWS):
        top = HEADER + GAP // 2 + i * (TARGET_H + GAP + HEADER)
        right = load_scaled(OUT / pattern.format(m=m))
        canvas.paste(folia, (GAP, top))
        canvas.paste(right, (GAP + folia.width + GAP, top))
        draw.text(
            (GAP + folia.width + GAP // 2 - 40, top + TARGET_H // 2),
            ROWS[i][0],
            font=f_sub,
            fill=MUTED,
        )

    out = OUT / f"folia-vs-guangyue-{m}.png"
    canvas.save(out, optimize=True)
    return out


if __name__ == "__main__":
    for dark in (False, True):
        p = build(dark)
        print(p, Image.open(p).size)
    sys.exit(0)
