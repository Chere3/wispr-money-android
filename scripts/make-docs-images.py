#!/usr/bin/env python3
"""Genera las imágenes del README a partir de capturas del emulador con datos sintéticos.

Uso: python3 scripts/make-docs-images.py <carpeta-con-capturas>

Las capturas salen de la app real conectada a `scripts/demo_server.py` (nunca a un servidor con
datos reales), en un emulador de 1080 × 2400 con la barra de estado en modo demo:

    raw-{hoy,movimientos,flujo,patrimonio,nuevo,hoy-oscuro}.png   pantallas completas
    raw-notif.png                                                  panel con la notificación expandida
    crop-{1x1,3x1,4x1,2x2,2x4,4x4}.png                             el widget recortado en cada tamaño

Requiere Pillow. Usa la fuente del propio repo (Google Sans Flex, SIL OFL).
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

SRC = Path(sys.argv[1])
ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs"
FONT = ROOT / "app/src/main/res/font/google_sans_flex.ttf"

BLUE = (31, 95, 196)
INK = (26, 27, 32)
MUTED = (68, 71, 79)


def font(size, weight=400, rond=100):
    f = ImageFont.truetype(str(FONT), size)
    # Ejes: opsz, wdth, wght, GRAD, ROND, slnt
    f.set_variation_by_axes([min(max(size, 6), 144), 100, weight, 0, rond, 0])
    return f


def rounded(im, radius):
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, im.width - 1, im.height - 1), radius, fill=255)
    out = im.convert("RGBA")
    out.putalpha(mask)
    return out


def shadowed(im, radius=36, offset=(0, 22), opacity=110):
    pad = radius * 2
    canvas = Image.new("RGBA", (im.width + pad * 2, im.height + pad * 2), (0, 0, 0, 0))
    alpha = im.split()[3].point(lambda a: opacity if a > 0 else 0)
    shadow = Image.new("RGBA", im.size, (20, 30, 60, 255))
    shadow.putalpha(alpha)
    canvas.paste(shadow, (pad + offset[0], pad + offset[1]), shadow)
    canvas = canvas.filter(ImageFilter.GaussianBlur(radius))
    canvas.alpha_composite(im, (pad, pad))
    return canvas, pad


def phone(name, width):
    """Captura completa como teléfono: esquinas redondeadas y un bisel oscuro fino."""
    im = Image.open(SRC / f"raw-{name}.png").convert("RGBA")
    scale = width / im.width
    im = im.resize((width, round(im.height * scale)), Image.LANCZOS)
    bezel = max(6, width // 60)
    body = Image.new("RGBA", (im.width + bezel * 2, im.height + bezel * 2), (18, 19, 22, 255))
    body = rounded(body, width // 9)
    body.alpha_composite(rounded(im, width // 9 - bezel), (bezel, bezel))
    return body


def gradient(size, top, bottom):
    w, h = size
    g = Image.new("RGBA", size)
    px = g.load()
    for y in range(h):
        t = y / max(h - 1, 1)
        c = tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,)
        for x in range(w):
            px[x, y] = c
    return g


def soft_background(size):
    """Fondo claro con dos manchas de color difuminadas, como el velo de las secciones."""
    bg = gradient(size, (236, 241, 252), (246, 247, 251))
    blobs = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(blobs)
    w, h = size
    d.ellipse((-w * 0.15, -h * 0.4, w * 0.45, h * 0.7), fill=(120, 160, 240, 110))
    d.ellipse((w * 0.55, h * 0.3, w * 1.2, h * 1.4), fill=(150, 220, 205, 110))
    bg.alpha_composite(blobs.filter(ImageFilter.GaussianBlur(min(size) // 5)))
    return bg


def widget(name):
    """Recorte del widget: busca su caja real por color (fondo claro) y redondea las esquinas."""
    im = Image.open(SRC / f"crop-{name}.png").convert("RGB")
    px = im.load()
    cols, rows = [0] * im.width, [0] * im.height
    for y in range(im.height):
        for x in range(im.width):
            r, g, b = px[x, y]
            # widgetBackground del tema claro: (235, 240, 255)
            if abs(r - 235) <= 4 and abs(g - 240) <= 4 and b >= 251:
                cols[x] += 1
                rows[y] += 1
    # Solo filas/columnas donde el fondo del widget domina: descarta píxeles sueltos del wallpaper.
    xs = [x for x, n in enumerate(cols) if n > max(cols) * 0.35]
    ys = [y for y, n in enumerate(rows) if n > max(rows) * 0.35]
    box = (min(xs), min(ys), max(xs) + 1, max(ys) + 1)
    return rounded(im.crop(box), 74)


def paste_shadowed(canvas, im, xy, **kw):
    s, pad = shadowed(im, **kw)
    canvas.alpha_composite(s, (xy[0] - pad, xy[1] - pad))


def screens():
    out = OUT / "screenshots"
    out.mkdir(parents=True, exist_ok=True)
    for name in ["hoy", "movimientos", "flujo", "patrimonio", "nuevo", "hoy-oscuro"]:
        p = phone(name, 420)
        canvas = Image.new("RGBA", (p.width + 80, p.height + 90), (0, 0, 0, 0))
        paste_shadowed(canvas, p, (40, 30))
        canvas.save(out / f"{name}.png", optimize=True)


def widgets():
    """Galería de tamaños del widget sobre un fondo de pantalla de inicio."""
    order = [("1x1", "1×1"), ("3x1", "3×1"), ("4x1", "4×1"), ("2x2", "2×2"), ("2x4", "2×4"), ("4x4", "4×4")]
    crops = {k: widget(k) for k, _ in order}
    s = 0.5
    crops = {k: im.resize((round(im.width * s), round(im.height * s)), Image.LANCZOS) for k, im in crops.items()}
    gap, margin, label_h = 36, 60, 44
    col1 = [crops["1x1"], crops["3x1"], crops["4x1"]]
    left_w = max(i.width for i in col1)
    left_h = sum(i.height for i in col1) + gap * 2 + label_h * 3
    right = [crops["2x2"], crops["2x4"], crops["4x4"]]
    right_w = sum(i.width for i in right) + gap * 2
    W = margin * 2 + left_w + gap + right_w
    H = margin * 2 + max(left_h, max(i.height for i in right) + label_h)
    canvas = gradient((W, H), (34, 52, 98), (18, 24, 44))
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse((-W * 0.2, -H * 0.5, W * 0.6, H * 0.8), fill=(90, 140, 240, 90))
    canvas.alpha_composite(glow.filter(ImageFilter.GaussianBlur(H // 4)))
    d = ImageDraw.Draw(canvas)
    f = font(22, 600)
    labels = dict(order)
    y = margin
    for k, im in zip(["1x1", "3x1", "4x1"], col1):
        paste_shadowed(canvas, im, (margin, y), radius=20, offset=(0, 10), opacity=120)
        d.text((margin + 6, y + im.height + 10), labels[k], font=f, fill=(220, 228, 245))
        y += im.height + label_h + gap
    x = margin + left_w + gap
    for k, im in zip(["2x2", "2x4", "4x4"], right):
        paste_shadowed(canvas, im, (x, margin), radius=20, offset=(0, 10), opacity=120)
        d.text((x + 6, margin + im.height + 10), labels[k], font=f, fill=(220, 228, 245))
        x += im.width + gap
    canvas.convert("RGB").save(OUT / "screenshots" / "widgets.png", optimize=True)


def notification():
    im = Image.open(SRC / "raw-notif.png").convert("RGBA")
    # Tarjeta de la notificación expandida (coordenadas del emulador 1080 × 2400).
    card = rounded(im.crop((42, 1398, 1038, 2004)), 48)
    W, H = card.width + 120, card.height + 120
    canvas = soft_background((W, H))
    paste_shadowed(canvas, card, (60, 50), radius=24, offset=(0, 12), opacity=90)
    canvas.convert("RGB").resize((W // 2 * 2 * 3 // 4, H * 3 // 4), Image.LANCZOS).save(
        OUT / "screenshots" / "notificacion.png", optimize=True)


def hero(size, title, subtitle, phones_w):
    W, H = size
    canvas = soft_background(size)
    d = ImageDraw.Draw(canvas)
    tx = round(W * 0.06)
    d.text((tx, round(H * 0.20)), "Wispr Money", font=font(round(H * 0.105), 700), fill=INK)
    d.text((tx, round(H * 0.33)), title, font=font(round(H * 0.105), 700), fill=BLUE)
    y = round(H * 0.50)
    for line in subtitle:
        d.text((tx, y), line, font=font(round(H * 0.042), 450), fill=MUTED)
        y += round(H * 0.062)
    chips = ["Jetpack Compose", "Material 3 Expressive", "Glance"]
    x, y = tx, round(H * 0.78)
    fc = font(round(H * 0.032), 600)
    for c in chips:
        w = d.textlength(c, font=fc) + H * 0.05
        d.rounded_rectangle((x, y, x + w, y + H * 0.068), radius=H * 0.034, fill=(255, 255, 255, 230))
        d.text((x + H * 0.025, y + H * 0.013), c, font=fc, fill=BLUE)
        x += w + H * 0.02
    # Teléfonos superpuestos a la derecha, recortados por el borde inferior.
    names = ["flujo", "hoy", "patrimonio"]
    px = [W * 0.52, W * 0.70, W * 0.86]
    py = [H * 0.20, H * 0.10, H * 0.22]
    for name, x0, y0 in zip(names, px, py):
        p = phone(name, phones_w if name == "hoy" else round(phones_w * 0.88))
        paste_shadowed(canvas, p, (round(x0 - p.width / 2 + W * 0.03), round(y0)))
    return canvas.convert("RGB")


def main():
    screens()
    widgets()
    notification()
    hero((1600, 640), "para Android",
         ["Tus finanzas en vivo, con widget redimensionable", "y notificación permanente. Sin caché."],
         300).save(OUT / "banner.png", optimize=True)
    hero((1280, 640), "para Android",
         ["En vivo por MCP, sin caché,", "con widget y notificación."],
         280).save(OUT / "social-preview.png", optimize=True)
    print("Imágenes escritas en", OUT)


if __name__ == "__main__":
    main()
