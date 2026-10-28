import os, json
from PIL import Image, ImageDraw, ImageFilter
import numpy as np

BASE = r"D:\workspaces\GuangYue"
ICON = os.path.join(BASE, "design", "icon")
RES = os.path.join(BASE, "app", "src", "main", "res")

full = Image.open(os.path.join(ICON, "ic_launcher_full_1024.png")).convert("RGBA")
bg = Image.open(os.path.join(ICON, "bg.png")).convert("RGBA")
fg_raw = Image.open(os.path.join(ICON, "ic_launcher_foreground_1024.png")).convert("RGBA")

print("full", full.size, "bg", bg.size, "fg", fg_raw.size)

# ---- 1. 从 full 图生成"白色字形 + 透明底"的 alpha 蒙版 ----
a = np.asarray(full).astype(np.float32)
r, g, b = a[..., 0], a[..., 1], a[..., 2]
mx = a[..., :3].max(axis=2)
mn = a[..., :3].min(axis=2)
sat = (mx - mn) / np.maximum(mx, 1.0)
v = mx / 255.0
# 半透明细描边（浅青）饱和度明显高于蓝底，算作字形；饱和度低且亮 = 白色
white_score = np.clip((1.0 - sat * 6.0), 0, 1) * np.clip((v - 0.5) / 0.5, 0, 1)
alpha = np.clip(white_score * 1.25, 0, 1)
alpha[alpha < 0.06] = 0.0
mask = Image.fromarray((alpha * 255).astype(np.uint8), "L")

bbox = mask.getbbox()
print("glyph bbox", bbox)
if bbox is None:
    raise SystemExit("no glyph found")

# 去掉细描边：轻度收缩 alpha（描边比字面细很多，收缩后基本消失，字面基本不变）
mask = mask.filter(ImageFilter.MinFilter(5))
mask = mask.filter(ImageFilter.GaussianBlur(0.6))
mask = mask.point(lambda x: 0 if x < 40 else min(255, int(x * 1.06)))
bbox2 = mask.getbbox()
print("glyph bbox after shrink", bbox2)


def glyph(size, scale):
    """返回 size×size 的白色字形蒙版，字形按 bbox 居中缩放到画布的 scale 比例。"""
    m = mask.crop(bbox2)
    w, h = m.size
    target = size * scale
    k = target / max(w, h)
    nw, nh = max(1, round(w * k)), max(1, round(h * k))
    m = m.resize((nw, nh), Image.LANCZOS)
    out = Image.new("L", (size, size), 0)
    out.paste(m, ((size - nw) // 2, (size - nh) // 2))
    return out


def white_from_mask(m, color=(255, 255, 255, 255)):
    img = Image.new("RGBA", m.size, color)
    img.putalpha(m)
    return img


def smooth_bg(size):
    return bg.resize((size, size), Image.LANCZOS).convert("RGBA")


def rounded(img, radius):
    m = Image.new("L", img.size, 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, img.size[0] - 1, img.size[1] - 1], radius=radius, fill=255)
    out = img.convert("RGBA")
    out.putalpha(m)
    return out


def circle(img):
    m = Image.new("L", img.size, 0)
    ImageDraw.Draw(m).ellipse([0, 0, img.size[0] - 1, img.size[1] - 1], fill=255)
    out = img.convert("RGBA")
    out.putalpha(m)
    return out


written = []


def save(img, rel):
    p = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p, "PNG", optimize=True)
    written.append((rel, img.size))


# ---- 2. adaptive icon foreground：108dp 画布，字形缩到 72% 居中以避开圆形遮罩 ----
ADAPTIVE = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
for dpi, px in ADAPTIVE.items():
    m = glyph(px, 0.72)
    save(white_from_mask(m), f"mipmap-{dpi}/ic_launcher_foreground.png")
    # monochrome 层：同字形，系统会按主题着色
    save(white_from_mask(m), f"mipmap-{dpi}/ic_launcher_monochrome.png")

# adaptive background：纯渐变，108dp；圆角/圆形遮罩由 launcher 处理，铺满即可
for dpi, px in ADAPTIVE.items():
    save(smooth_bg(px), f"mipmap-{dpi}/ic_launcher_background.png")

# ---- 3. legacy launcher PNG：full 图，圆角 / 圆形 ----
LEGACY = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for dpi, px in LEGACY.items():
    # 先在高分辨率合成再缩小，边缘更干净
    big = px * 4
    canvas = smooth_bg(big)
    gm = glyph(big, 0.62)
    canvas.alpha_composite(white_from_mask(gm))
    save(rounded(canvas, int(big * 0.22)).resize((px, px), Image.LANCZOS), f"mipmap-{dpi}/ic_launcher.png")
    save(circle(canvas).resize((px, px), Image.LANCZOS), f"mipmap-{dpi}/ic_launcher_round.png")

# ---- 4. Play 商店 512x512 ----
store = smooth_bg(2048)
store.alpha_composite(white_from_mask(glyph(2048, 0.62)))
store = store.resize((512, 512), Image.LANCZOS)
store.save(os.path.join(ICON, "ic_launcher_play_512.png"), "PNG", optimize=True)
print("play icon saved")

# ---- 5. 预览图（检查用） ----
prev = Image.new("RGBA", (760, 300), (245, 246, 248, 255))
big = smooth_bg(512)
big.alpha_composite(white_from_mask(glyph(512, 0.62)))
prev.paste(rounded(big, 112).resize((240, 240), Image.LANCZOS), (20, 30), rounded(big, 112).resize((240, 240), Image.LANCZOS))
r = circle(big).resize((240, 240), Image.LANCZOS)
prev.paste(r, (270, 30), r)
# adaptive 预览：圆形遮罩裁 108dp 画布
ad = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
ad.alpha_composite(smooth_bg(432))
ad.alpha_composite(white_from_mask(glyph(432, 0.72)))
ad = circle(ad).resize((240, 240), Image.LANCZOS)
prev.paste(ad, (520, 30), ad)
prev.save(os.path.join(ICON, "_preview.png"))
print(json.dumps({"written": len(written), "bbox": bbox2}, ensure_ascii=False))
