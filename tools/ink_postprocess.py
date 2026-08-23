"""Post-process rendered gear icons into ink-wash style bitmaps.

Takes the flat 512px Canvas renders from art/render_raw/gear_render/ and adds:
- xuan-paper fiber texture (multi-octave noise, soft-light blended)
- ink halo around silhouettes (alpha dilate + blur, dark multiply)
- dry-brush flying-white streaks along edges (alpha thinning)
- mineral pigment warmth shift
- subtle vignette so icons read well on parchment UI

Outputs to app/src/main/res/drawable-nodpi/ as gear_<id>.png (512x512).
"""
from __future__ import annotations

import random
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "art" / "render_raw" / "gear_render"
DST = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"

SIZE = 512


def fbm_noise(rng: random.Random, size: int, octaves: int = 4) -> np.ndarray:
    """Cheap value-noise fbm on [0,1]."""
    total = np.zeros((size, size), dtype=np.float32)
    amp = 1.0
    freq = 4
    norm = 0.0
    for _ in range(octaves):
        cell = max(2, int(freq))
        grid = np.array(
            [[rng.random() for _ in range(cell)] for _ in range(cell)], dtype=np.float32
        )
        img = Image.fromarray((grid * 255).astype(np.uint8)).resize(
            (size, size), Image.Resampling.BICUBIC
        )
        total += np.asarray(img, dtype=np.float32) / 255.0 * amp
        norm += amp
        amp *= 0.5
        freq *= 2
    return total / norm


def paper_texture(seed: int) -> np.ndarray:
    """Xuan paper: large fiber waves + fine grain."""
    rng = random.Random(seed)
    fiber = fbm_noise(rng, SIZE, octaves=3)
    grain = np.array(
        [[rng.random() for _ in range(SIZE)] for _ in range(SIZE)], dtype=np.float32
    )
    grain = np.asarray(
        Image.fromarray((grain * 255).astype(np.uint8))
        .filter(ImageFilter.GaussianBlur(0.6))
        .resize((SIZE // 2, SIZE // 2), Image.Resampling.BILINEAR)
        .resize((SIZE, SIZE), Image.Resampling.BILINEAR),
        dtype=np.float32,
    ) / 255.0
    tex = 0.7 * fiber + 0.3 * grain
    # normalize to ~[0.85, 1.08] modulation range
    tex = (tex - tex.min()) / (tex.max() - tex.min() + 1e-6)
    return 0.85 + tex * 0.23


def ink_halo(alpha: np.ndarray, seed: int) -> np.ndarray:
    """Dark bleed around the silhouette, like ink soaking into paper."""
    rng = random.Random(seed + 7)
    a_img = Image.fromarray((alpha * 255).astype(np.uint8))
    halo = a_img.filter(ImageFilter.MaxFilter(7)).filter(ImageFilter.GaussianBlur(6))
    halo_a = np.asarray(halo, dtype=np.float32) / 255.0
    # only the ring outside the art
    ring = np.clip(halo_a - alpha, 0, 1)
    jitter = fbm_noise(rng, SIZE, octaves=2)
    ring = ring * (0.55 + 0.45 * jitter)
    return ring


def dry_brush(rgb: np.ndarray, alpha: np.ndarray, seed: int, strength: float = 0.35) -> np.ndarray:
    """Flying-white: thin alpha in streaks aligned to local stroke direction."""
    rng = random.Random(seed + 13)
    gy, gx = np.gradient(alpha)
    grad_mag = np.sqrt(gx * gx + gy * gy)
    edges = np.clip(grad_mag * 8.0, 0, 1)
    streaks = fbm_noise(rng, SIZE, octaves=3)
    # thin where edges & streaks align (paper showing through brush)
    mask = edges * (streaks > 0.62).astype(np.float32)
    mask = np.asarray(
        Image.fromarray((mask * 255).astype(np.uint8))
        .filter(ImageFilter.GaussianBlur(1.2)),
        dtype=np.float32,
    ) / 255.0
    return np.clip(alpha * (1.0 - mask * strength), 0, 1)


def process(path: Path, seed: int) -> None:
    img = Image.open(path).convert("RGBA")
    arr = np.asarray(img, dtype=np.float32)
    rgb, alpha = arr[..., :3], arr[..., 3] / 255.0

    # 1. paper texture on the art itself (soft-light-ish multiply toward warm paper)
    tex = paper_texture(seed)
    warm = np.array([1.045, 1.0, 0.955], dtype=np.float32)  # mineral pigment warmth
    rgb = np.clip(rgb * tex[..., None] * warm[None, None, :], 0, 255)

    # 2. dry-brush alpha thinning
    alpha = dry_brush(rgb, alpha, seed)

    # 3. ink halo ring drawn UNDER the art (composite onto transparent canvas)
    halo = ink_halo(alpha, seed)
    out = np.zeros((SIZE, SIZE, 4), dtype=np.uint8)
    ink_col = np.array([24, 18, 24], dtype=np.float32)
    # halo first
    out[..., 0] = ink_col[0]
    out[..., 1] = ink_col[1]
    out[..., 2] = ink_col[2]
    out[..., 3] = (halo * 108).astype(np.uint8)
    # art over halo
    a3 = alpha[..., None]
    out_rgb = out[..., :3].astype(np.float32)
    out_al = out[..., 3:4].astype(np.float32) / 255.0
    art_al = alpha
    blend_al = art_al + out_al[:, :, 0] * (1 - art_al)
    safe = np.where(blend_al > 1e-4, blend_al, 1.0)
    final_rgb = (rgb * a3 + out_rgb * out_al * (1 - a3)) / safe[..., None]
    final_rgb = np.clip(final_rgb, 0, 255)
    final_al = np.clip(blend_al, 0, 1)

    canvas = np.zeros((SIZE, SIZE, 4), dtype=np.uint8)
    canvas[..., :3] = final_rgb.astype(np.uint8)
    canvas[..., 3] = (final_al * 255).astype(np.uint8)

    Image.fromarray(canvas, "RGBA").save(DST / path.name, optimize=True)


def main() -> None:
    DST.mkdir(parents=True, exist_ok=True)
    files = sorted(SRC.glob("gear_*.png"))
    for i, f in enumerate(files):
        process(f, seed=hash(f.stem) & 0xFFFF)
        print(f"[{i+1}/{len(files)}] {f.name}")
    print(f"done -> {DST}")


if __name__ == "__main__":
    main()
