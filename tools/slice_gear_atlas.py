"""Slice a transparent 3x3 equipment atlas into Android-ready square icons."""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image


ICON_NAMES = (
    "gear_w_flame",
    "gear_m_crown",
    "gear_t_gourd",
    "gear_a_w_flame",
    "gear_a_m_crown",
    "gear_a_t_seal",
    "gear_u_penta",
    "gear_r_thunder",
    "gear_b_star",
)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    atlas = Image.open(args.source).convert("RGBA")
    if atlas.width % 3 or atlas.height % 3:
        raise ValueError(f"atlas must divide into 3x3 cells, got {atlas.size}")

    cell_w = atlas.width // 3
    cell_h = atlas.height // 3
    args.output.mkdir(parents=True, exist_ok=True)

    for index, name in enumerate(ICON_NAMES):
        col, row = index % 3, index // 3
        cell = atlas.crop((col * cell_w, row * cell_h, (col + 1) * cell_w, (row + 1) * cell_h))
        alpha_box = cell.getchannel("A").getbbox()
        if alpha_box is None:
            raise ValueError(f"{name} cell is empty")
        item = cell.crop(alpha_box)
        max_content = 464
        scale = min(max_content / item.width, max_content / item.height)
        new_size = (max(1, round(item.width * scale)), max(1, round(item.height * scale)))
        item = item.resize(new_size, Image.Resampling.LANCZOS)
        icon = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
        icon.alpha_composite(item, ((512 - item.width) // 2, (512 - item.height) // 2))
        icon.save(args.output / f"{name}.png", optimize=True)


if __name__ == "__main__":
    main()
