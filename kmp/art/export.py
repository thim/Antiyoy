"""Exports the "Ilhas de brinquedo" art (sprites.py) to the PNGs the KorGE app loads.

Usage: python3 kmp/art/export.py   (needs rsvg-convert)
"""
import math, os, subprocess, sys
sys.path.insert(0, os.path.dirname(__file__))
from sprites import *

OUT = os.path.join(os.path.dirname(__file__), '..', 'resources')
SPRITE_PX = 256
TILE_R = 64          # tile radius in px
SIDE = 0.22          # side band, fraction of radius

# fraction index -> color (0..6 kingdoms, 7 neutral, 8..10 extra kingdoms)
FRACTION_COLORS = [KINGDOMS['verde'], KINGDOMS['vermelho'], KINGDOMS['azul'], KINGDOMS['ciano'], KINGDOMS['amarelo'],
                   KINGDOMS['roxo'], KINGDOMS['laranja'], NEUTRAL, '#e57fb5', '#9b7a55', '#a6cf3f']

def write_png(svg_text, path, w, h):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    subprocess.run(['rsvg-convert', '-w', str(w), '-h', str(h), '-o', path], input=svg_text.encode(), check=True)

def sprite(inner, path):
    write_png(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">{inner}</svg>', path, SPRITE_PX, SPRITE_PX)

def tile_svg(color, top_only=False, white=False):
    r = 50.0
    h = R3 / 2 * r
    t = 0 if top_only else r * SIDE
    W, H = 2 * r, 2 * h + t
    if white:
        P = ' '.join(f'{x:.2f},{y:.2f}' for x, y in [(2*r, h), (1.5*r, 2*h), (0.5*r, 2*h), (0, h), (0.5*r, 0), (1.5*r, 0)])
        body = f'<polygon points="{P}" fill="#ffffff"/>'
    else:
        body = hex_tile(r, h, r, color, t=t, stroke=True)
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W:.3f} {H:.3f}">{body}</svg>', W, H

def main():
    for i, c in enumerate(FRACTION_COLORS):
        svg, W, H = tile_svg(c)
        write_png(svg, f'{OUT}/tiles/hex_{i}.png', 2 * TILE_R, round(2 * TILE_R * H / W))
        sprite(castle(c), f'{OUT}/fe/castle_{i}.png')
        sprite(strong_tower(c), f'{OUT}/fe/strong_tower_{i}.png')
    svg, W, H = tile_svg('#fff', top_only=True, white=True)
    write_png(svg, f'{OUT}/tiles/hex_top_white.png', 2 * TILE_R, round(2 * TILE_R * H / W))
    for i, f in enumerate([peasant, spearman, baron, knight]):
        sprite(f(), f'{OUT}/fe/man{i}.png')
    sprite(tower(), f'{OUT}/fe/tower.png')
    for v in (1, 2, 3):
        sprite(farm(v), f'{OUT}/fe/farm{v}.png')
    sprite(pine(), f'{OUT}/fe/pine.png')
    sprite(palm(), f'{OUT}/fe/palm.png')
    sprite(grave(), f'{OUT}/fe/grave.png')
    # water background
    waves = ''.join(f'<path d="M{x} {y} q12 -8 24 0 t24 0" stroke="#ffffff" stroke-opacity=".12" stroke-width="4" fill="none" stroke-linecap="round"/>'
                    for x, y in [(60, 140), (480, 90), (300, 420), (580, 610), (90, 760), (420, 980), (150, 1180), (600, 1220), (330, 300), (50, 520)])
    write_png(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 720 1280"><rect width="720" height="1280" fill="{WATER}"/>{waves}</svg>',
              f'{OUT}/game_background_water.png', 720, 1280)
    coin = ('<circle cx="50" cy="54" r="38" fill="#b8862a"/><circle cx="50" cy="50" r="38" fill="#f2c34a"/><circle cx="50" cy="50" r="29" fill="none" stroke="#d9a62e" stroke-width="4"/>'
            '<path d="M50 30 V70 M58 38 Q50 32 43 37 Q38 43 47 48 L54 51 Q62 56 56 63 Q49 68 41 62" stroke="#8a6214" stroke-width="5.5" fill="none" stroke-linecap="round"/>')
    sprite(coin, f'{OUT}/coin.png')

if __name__ == '__main__':
    main()
