"""Generate hand-painted ink-wash hero portraits (warrior/mage/taoist)."""
from __future__ import annotations

import io
import time
import urllib.parse
import urllib.request
from pathlib import Path

from PIL import Image
from rembg import remove

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT / "art" / "ai_raw"
DST = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"

STYLE = (
    "traditional chinese ink wash fantasy game character portrait of {desc}, "
    "cute round jelly blob body with simple face, full body standing, "
    "dark brush stroke outlines, lacquer and jade texture, mineral pigment colors, "
    "subtle xuan paper texture, masterful detailed brushwork, muted earthy palette, "
    "plain light beige empty background, no text, no border, no watermark"
)

HEROES = {
    "hero_warrior": "a brave jelly blob warrior holding a big sword, red accents, small iron shoulder plates",
    "hero_mage": "a wise jelly blob mage with deep hood and wide sleeves, holding a wooden staff with glowing orb, blue accents",
    "hero_taoist": "a serene jelly blob taoist priest with hair bun and wooden hairpin, green cross-collar robe, holding a talisman, jade pendant",
}


def fetch(url: str, tries: int = 4) -> bytes:
    last: Exception = RuntimeError("no tries")
    for i in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=180) as r:
                data = r.read()
                if len(data) > 8000:
                    return data
                last = RuntimeError(f"too small: {len(data)}")
        except Exception as e:  # noqa: BLE001
            last = e
        time.sleep(6 + i * 8)
    raise last


def main() -> None:
    RAW.mkdir(parents=True, exist_ok=True)
    for iid, desc in HEROES.items():
        raw = RAW / f"{iid}.png"
        if raw.exists() and raw.stat().st_size > 20000:
            img = Image.open(raw).convert("RGBA")
        else:
            prompt = urllib.parse.quote(STYLE.format(desc=desc))
            url = (
                f"https://image.pollinations.ai/prompt/{prompt}"
                f"?width=576&height=576&nologo=true&seed={abs(hash(iid)) % 9000 + 100}&model=flux"
            )
            img = remove(Image.open(io.BytesIO(fetch(url))).convert("RGB"))
            img.save(raw)
        box = img.getchannel("A").getbbox()
        item = img.crop(box)
        # portraits keep more height: target 470 tall
        scale = min(470 / item.width, 470 / item.height)
        item = item.resize(
            (max(1, round(item.width * scale)), max(1, round(item.height * scale))),
            Image.Resampling.LANCZOS,
        )
        icon = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
        icon.alpha_composite(item, ((512 - item.width) // 2, (512 - item.height) // 2))
        icon.save(DST / f"{iid}.png", optimize=True)
        print(f"{iid} ok")


if __name__ == "__main__":
    main()
