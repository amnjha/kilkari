"""Regenerate Kilkari's launcher assets from common/design/icon-source.jpg.

Run with Pillow available:  python3 android/tools/make_icons.py

The source is a square badge sitting on a palette backdrop. We crop the badge, discard
the sliver of backdrop caught in its rounded corners, then extend the badge's own edge
pixels outward so the adaptive-icon mask never reveals a gap whatever shape it uses.
"""
from PIL import Image, ImageDraw, ImageFilter
import os

ANDROID = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WORKSPACE = os.path.dirname(ANDROID)
# The badge is the brand, not an Android asset: both platforms cut their icons from it.
SOURCE = os.path.join(WORKSPACE, 'common', 'design', 'icon-source.jpg')
RES = os.path.join(ANDROID, 'app', 'src', 'main', 'res')

BADGE_BOX = (548, 256, 548 + 1208, 256 + 1208)
# Enough inset to clear the backdrop trapped in the badge's rounded corners.
CORNER_INSET = 95

src = Image.open(SOURCE).convert('RGB')
badge = src.crop(BADGE_BOX)
field = badge.crop((CORNER_INSET, CORNER_INSET,
                    badge.width - CORNER_INSET, badge.height - CORNER_INSET))


# Measured with a ringed calibration icon on the Pixel launcher: the mask reveals roughly the
# centre 81% of the 108dp layer. The badge is sized just past that so it covers the mask, while
# the gold waves — which reach 0.908 of the badge's half-width — stay inside it.
BADGE_ON_CANVAS = 0.85


def adaptive_foreground(img, canvas):
    """Badge centred on a transparent `canvas` px foreground layer.

    The artwork rides on the foreground with a flat brand colour behind, rather than the other
    way round: the launcher insets and plates anything it does not recognise as a filled
    adaptive layer, and a matching background means any sliver it reveals reads as intentional.
    """
    content = round(canvas * BADGE_ON_CANVAS)
    art = img.resize((content, content), Image.LANCZOS).convert('RGBA')
    mask = Image.new('L', (content, content), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, content - 1, content - 1), radius=int(content * 0.225), fill=255,
    )
    art.putalpha(mask)
    layer = Image.new('RGBA', (canvas, canvas), (0, 0, 0, 0))
    off = (canvas - content) // 2
    layer.paste(art, (off, off), art)
    return layer


def rounded(img, radius_ratio=0.225):
    """Square icon with the badge's own corner rounding, transparent outside."""
    out = img.convert('RGBA')
    mask = Image.new('L', img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, img.width - 1, img.height - 1),
        radius=int(img.width * radius_ratio), fill=255,
    )
    out.putalpha(mask)
    return out


def circle(img):
    out = img.convert('RGBA')
    mask = Image.new('L', img.size, 0)
    ImageDraw.Draw(mask).ellipse((0, 0, img.width - 1, img.height - 1), fill=255)
    out.putalpha(mask)
    return out


# Adaptive background. Launcher masks reveal far more than the 66dp "safe zone" — the Pixel
# launcher's circle measured ~77% of the 108dp canvas. The badge is sized to 80% so it covers
# that circle edge to edge, while the gold waves (which reach 0.908 of the badge's half-width)
# still clear it. The full badge is used rather than the inset field crop: at this scale the
# mask never reaches the rounded corners where the original backdrop shows through.

ADAPTIVE = {'mdpi': 108, 'hdpi': 162, 'xhdpi': 216, 'xxhdpi': 324, 'xxxhdpi': 432}
# Legacy launcher icon: 48dp.
LEGACY = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}

for density, size in ADAPTIVE.items():
    d = f'{RES}/mipmap-{density}'
    os.makedirs(d, exist_ok=True)
    adaptive_foreground(badge, size).save(f'{d}/ic_launcher_foreground.png')

for density, size in LEGACY.items():
    d = f'{RES}/mipmap-{density}'
    legacy = field.resize((size, size), Image.LANCZOS)
    rounded(legacy).save(f'{d}/ic_launcher.png')
    circle(legacy).save(f'{d}/ic_launcher_round.png')

# Play Store listing icon: 512 square, full bleed, no alpha.
field.resize((512, 512), Image.LANCZOS).save(
    os.path.join(ROOT, 'design', 'play-store-icon.png'))

print('icons written to', RES)
