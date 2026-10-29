"""
Generates the SkiesClear Plus icon set.

Design: a dark night-sky rounded tile (the "Skies" of the name) with the line-art
trash can from the SkiesClear family in cyan, plus a gold "+" badge marking the fork.
Everything is drawn from scratch with Pillow primitives, supersampled 4x and
downsampled with LANCZOS, so there is no bitmap from the upstream icon involved.
"""

from __future__ import annotations

import math
import pathlib

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

OUT = pathlib.Path(__file__).resolve().parents[1] / "docs"
OUT.mkdir(parents=True, exist_ok=True)

SS = 4                      # supersampling factor
BASE = 1024                 # master size
S = BASE * SS               # working canvas
U = S / 1000.0              # design units -> pixels


def design(v: float) -> int:
    return int(round(v * U))


# ---------------------------------------------------------------- palette
TOP = np.array([26, 46, 84], dtype=np.float64)       # deep navy
BOTTOM = np.array([8, 13, 26], dtype=np.float64)     # near black
GLOW = np.array([56, 170, 235], dtype=np.float64)    # cyan haze behind the can
CYAN_TOP = (86, 231, 255)
CYAN_BOTTOM = (14, 138, 214)
GOLD_TOP = (255, 214, 102)
GOLD_BOTTOM = (245, 158, 32)


def vertical_gradient(size: int, top: np.ndarray, bottom: np.ndarray) -> np.ndarray:
    t = np.linspace(0.0, 1.0, size)[:, None, None]
    return top[None, None, :] * (1 - t) + bottom[None, None, :] * t


def background() -> Image.Image:
    grad = np.repeat(vertical_gradient(S, TOP, BOTTOM), S, axis=1)

    # radial cyan glow, centred a little above the trash can
    yy, xx = np.mgrid[0:S, 0:S].astype(np.float64)
    cx, cy = S * 0.5, S * 0.44
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / (S * 0.52)
    glow = np.clip(1.0 - d, 0.0, 1.0) ** 2.4
    grad = grad + (GLOW[None, None, :] - grad) * (glow[:, :, None] * 0.55)

    # faint corner vignette keeps the edges from bleeding into white pages
    edge = np.clip(
        np.minimum(np.minimum(xx, S - xx), np.minimum(yy, S - yy)) / (S * 0.30), 0.0, 1.0
    )
    grad = grad * (0.82 + 0.18 * edge[:, :, None])

    img = Image.fromarray(np.clip(grad, 0, 255).astype(np.uint8), "RGB").convert("RGBA")
    return add_stars(img)


def add_stars(img: Image.Image) -> Image.Image:
    rng = np.random.default_rng(20261029)
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    for _ in range(90):
        x = rng.uniform(0, S)
        y = rng.uniform(0, S * 0.78)
        r = rng.uniform(0.9, 2.6) * U * 1.6
        a = int(rng.uniform(30, 130))
        draw.ellipse([x - r, y - r, x + r, y + r], fill=(255, 255, 255, a))
    for _ in range(7):
        # a handful of brighter four-point sparkles
        x = rng.uniform(S * 0.08, S * 0.92)
        y = rng.uniform(S * 0.05, S * 0.55)
        r = rng.uniform(3.0, 6.0) * U * 1.6
        draw.line([(x - r, y), (x + r, y)], fill=(255, 255, 255, 150), width=max(1, int(r * 0.34)))
        draw.line([(x, y - r), (x, y + r)], fill=(255, 255, 255, 150), width=max(1, int(r * 0.34)))
    return Image.alpha_composite(img, layer.filter(ImageFilter.GaussianBlur(S * 0.0007)))


def gradient_stroke_mask(
    size: int, draw_fn, top: tuple, bottom: tuple, blur_px: float = 0.0
) -> Image.Image:
    """Renders `draw_fn` as a white mask, then tints it with a vertical gradient."""
    mask = Image.new("L", (size, size), 0)
    draw_fn(ImageDraw.Draw(mask))
    if blur_px:
        mask = mask.filter(ImageFilter.GaussianBlur(blur_px))
    arr = np.array(mask, dtype=np.float64) / 255.0
    grad = vertical_gradient(size, np.array(top, dtype=np.float64), np.array(bottom, dtype=np.float64))
    rgb = np.repeat(grad, size, axis=1)
    out = np.dstack([rgb, arr[:, :, None] * 255.0])
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGBA")


def trash_can(draw: ImageDraw.ImageDraw, ox: float = 0.0, oy: float = 0.0, scale: float = 1.0) -> None:
    stroke = design(52 * scale)
    slot = design(48 * scale)

    def pt(x: float, y: float) -> tuple[float, float]:
        return (design(x * scale) + ox, design(y * scale) + oy)

    # lid
    draw.line([pt(186, 330), pt(800, 330)], fill=255, width=stroke)
    # handle: a wide rounded arch sitting on the lid. Kept tall and narrow enough
    # that the opening under it stays clearly readable at 32 px.
    draw.line([pt(384, 330), pt(432, 196), pt(554, 196), pt(602, 330)],
              fill=255, width=stroke, joint="curve")
    # bucket outline, open at the top like the upstream icon
    draw.line([pt(250, 404), pt(300, 812), pt(686, 812), pt(736, 404)],
              fill=255, width=stroke, joint="curve")
    # the two internal slots
    draw.line([pt(428, 522), pt(428, 706)], fill=255, width=slot)
    draw.line([pt(558, 522), pt(558, 706)], fill=255, width=slot)


def plus_sign(draw: ImageDraw.ImageDraw, cx: float, cy: float, r: float, stroke: float) -> None:
    draw.line([(cx - r, cy), (cx + r, cy)], fill=255, width=int(stroke))
    draw.line([(cx, cy - r), (cx, cy + r)], fill=255, width=int(stroke))


def build() -> Image.Image:
    canvas = background()

    # huddle the can slightly left/up so the badge can sit bottom-right
    can_layer = gradient_stroke_mask(
        S,
        lambda d: trash_can(d, ox=-design(46), oy=-design(30), scale=0.90),
        CYAN_TOP,
        CYAN_BOTTOM,
    )

    # soft cyan bloom underneath the strokes
    bloom = can_layer.filter(ImageFilter.GaussianBlur(S * 0.022))
    bloom.putalpha(bloom.getchannel("A").point(lambda a: int(a * 0.55)))
    canvas = Image.alpha_composite(canvas, bloom)
    canvas = Image.alpha_composite(canvas, can_layer)

    # ---- gold badge with the "+" of the fork name
    bx, by = design(800), design(806)
    br = design(148)
    ring = design(26)

    badge_mask = Image.new("L", (S, S), 0)
    bd = ImageDraw.Draw(badge_mask)
    bd.ellipse([bx - br - ring, by - br - ring, bx + br + ring, by + br + ring], fill=255)
    # punch a hole so the badge reads as a separate coin, not a blob on the can
    bd.ellipse([bx - br, by - br, bx + br, by + br], fill=0)
    ring_layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ring_layer.paste(Image.new("RGBA", (S, S), (10, 16, 30, 235)), mask=badge_mask)
    canvas = Image.alpha_composite(canvas, ring_layer.filter(ImageFilter.GaussianBlur(S * 0.0012)))

    coin = gradient_stroke_mask(
        S,
        lambda d: d.ellipse([bx - br, by - br, bx + br, by + br], fill=255),
        GOLD_TOP,
        GOLD_BOTTOM,
    )
    canvas = Image.alpha_composite(canvas, coin)

    plus = gradient_stroke_mask(
        S,
        lambda d: plus_sign(d, bx, by, br * 0.52, br * 0.30),
        (90, 52, 8),
        (60, 32, 4),
    )
    canvas = Image.alpha_composite(canvas, plus)

    return canvas


def rounded(img: Image.Image, radius_ratio: float = 0.225) -> Image.Image:
    size = img.size[0]
    r = int(size * radius_ratio)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=r, fill=255)
    out = img.copy()
    out.putalpha(mask)
    return out


def main() -> None:
    """Writes the three images the repository actually uses.

    The full resolution ladder (32…1024 px, plus a rounded variant) is written to
    `docs/` as well; only icon.png / icon-large.png / banner.png are referenced,
    the rest are there so nobody has to open Pillow to get a 64 px copy.
    """
    master = build()

    master.resize((BASE, BASE), Image.LANCZOS).save(OUT / "icon-large.png")
    master.resize((512, 512), Image.LANCZOS).save(OUT / "icon-512.png")
    master.resize((256, 256), Image.LANCZOS).save(OUT / "icon.png")
    master.resize((128, 128), Image.LANCZOS).save(OUT / "icon-128.png")
    master.resize((64, 64), Image.LANCZOS).save(OUT / "icon-64.png")
    master.resize((32, 32), Image.LANCZOS).save(OUT / "icon-32.png")
    rounded(master).resize((BASE, BASE), Image.LANCZOS).save(OUT / "icon-rounded.png")

    # README / Modrinth gallery banner
    banner = Image.new("RGBA", (1280, 400), (0, 0, 0, 0))
    tile = rounded(master).resize((360, 360), Image.LANCZOS)
    banner.alpha_composite(tile, (48, 20))
    strip = np.repeat(vertical_gradient(400, TOP, BOTTOM), 872, axis=1)
    strip_img = Image.fromarray(np.clip(strip, 0, 255).astype(np.uint8), "RGB").convert("RGBA")
    banner.alpha_composite(strip_img, (408, 0))
    banner.save(OUT / "banner.png")

    # the copy that ships inside the jar
    mod_icon = OUT.parents[0] / "src" / "main" / "resources" / "assets" / "skiesclear" / "icon.png"
    if mod_icon.parent.is_dir():
        master.resize((256, 256), Image.LANCZOS).save(mod_icon)

    for p in sorted(OUT.glob("*.png")):
        with Image.open(p) as im:
            print(f"{p.name:22} {im.size[0]:>4}x{im.size[1]:<4} {p.stat().st_size:>7,} B")


if __name__ == "__main__":
    main()
