"""AI-generate hand-painted ink-wash icons for all non-premium gear.

For each item: pollinations.ai (Flux) generates a single-item ink-wash painting
matching the style of the 9 original hand-painted legendary icons, then rembg
cuts it out, and we pad to 512x512 into drawable-nodpi.

Run: python tools/gen_gear_ai.py [--only id1,id2]
"""
from __future__ import annotations

import argparse
import io
import sys
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
    "traditional chinese ink wash fantasy game item icon of {desc}, "
    "single object centered, dark brush stroke outlines, lacquer and jade texture, "
    "mineral pigment colors ochre vermilion azurite malachite, subtle xuan paper texture, "
    "masterful detailed brushwork, muted earthy palette, "
    "plain light beige empty background, no text, no border, no watermark, no human"
)

ITEMS: dict[str, str] = {
    # ── 武器 · 战士 ──
    "w_iron": "a rusty short iron sword with worn blade and leather-wrapped grip",
    "w_steel": "a long steel jian sword with bright polished blade and golden guard",
    "w_blood": "a blood-red long sword with dark crimson blade and deep blood groove",
    "w_quake": "a heavy bronze battle axe with crescent moon blade and studded haft",
    "w_gold_spear": "a golden spear with willow-leaf spearhead, red tassel and gilded shaft",
    "w_thund_edge": "two crossed slender blades, one ice-blue one violet, lightning arcing between them",
    "w_guard_blade": "a green-tinged guard sword with wide tower-shield shaped crossguard",
    # ── 武器 · 法师 ──
    "w_staff": "a gnarled wooden mage staff with a small glowing amber orb held by curved claws",
    "m_flame": "a mage staff with flaming orb and rising fire tongues",
    "m_ice": "a mage staff with icy sapphire orb and orbiting ice crystals",
    "m_thunder": "a tall dark mage staff with crackling lightning orb",
    "m_wood_orb": "a living wooden orb with green sprout on top and small roots below, jade sheen",
    "m_abyss": "a dark navy grimoire tome with glowing single eye emblem on cover",
    "m_earth_tome": "an aged leather-bound tome with golden clasp and red square seal stamp",
    # ── 武器 · 道士 ──
    "w_peach": "a peach wood short sword with hanging yellow paper talisman and red tassel",
    "t_talisman": "a taoist peach sword with vermillion paper talisman covered in red script",
    "t_dust": "a taoist horsehair whisk with dark handle and flowing white silk strands",
    "t_fire_charm": "a taoist talisman sword with burning fire charm paper and small flames",
    "t_poison_bell": "a green bronze bell with poison bubbles rising, toxic taoist artifact",
    "t_jade": "a teal jade pendant tablet with golden cap and cloud carvings on red cord",
    # ── 武器 · 通用 ──
    "u_water_blade": "a curved water blade shaped like crescent wave, aqua blue, swirling water rings around",
    "u_earth_hammer": "a square bronze hammer with mountain rune inscription and floating rock chunks",
    "u_wood_bow": "a living wooden bow with vine wrapping and sprouting green leaves",
    "u_spark": "a blazing fire orb core with electric sparks radiating",
    # ── 防具 ──
    "a_cloth": "a simple beige cloth traveler robe folded, plain and humble",
    "a_w_leather": "a brown leather cuirass armor with shoulder plates and strap bindings",
    "a_w_iron": "a silver iron plate armor with layered lames and rivets",
    "a_w_blood": "a crimson blood-red plate armor with dark engravings",
    "a_w_quake": "a heavy bronze-orange plate armor with golden trim and crack motifs",
    "a_m_robe": "a blue mage robe with wide sleeves and deep hood",
    "a_m_frost": "an ice-blue mage robe with frost crystal patterns",
    "a_m_storm": "a violet mage robe with lightning motifs on chest",
    "a_m_abyss": "a deep navy abyss mage robe with faint glowing runes",
    "a_t_cloth": "a green taoist robe with cross collar, sash and hair bun stick",
    "a_t_jade": "a teal-jade taoist robe with jade ornament at belt",
    "a_t_poison": "an olive-green taoist robe with rising poison mist",
    "a_t_gourd": "a purple taoist robe with small gourd hanging at belt",
    "a_u_chain": "a chainmail armor made of interlocking metal rings",
    "a_u_scale": "a teal scale armor with overlapping five-color metal scales",
    # ── 戒指 ──
    "r_wood": "a wooden ring with leaf-shaped green gem and tiny sprout",
    "r_spark": "a dark iron ring with flame-shaped fire crystal and flying sparks",
    "r_ice": "a silver ring with snowflake-shaped ice blue gem",
    "r_blood": "a gold ring with blood-drop ruby gem and one dripping bead",
    "r_jade": "a chinese jade bi disc ring with a notch cut, tied with red cord, cloud carvings",
    "r_penta": "a gold ring with five small colored gems arranged in a circle",
    # ── 鞋子 ──
    "b_cloth": "a pair of traditional dark chinese cloth shoes with stitched cloth sole",
    "b_leather": "a pair of brown leather hunting boots with strap bindings",
    "b_iron": "a pair of iron greaves war boots, silver metal plates",
    "b_wind": "a pair of light wind boots with swirling wind stream lines and green accents",
    "b_mage": "a pair of blue silk mage shoes with arcane patterns",
    "b_tao": "a pair of earthen taoist travel shoes with cloud patterns",
    "b_quake": "a pair of heavy bronze war boots with molten glowing cracks",
}

PREMIUM = {
    "w_flame", "m_crown", "t_gourd", "a_w_flame", "a_m_crown", "a_t_seal",
    "u_penta", "r_thunder", "b_star",
}


def fetch(url: str, tries: int = 4) -> bytes:
    last = None
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


def process(item_id: str, desc: str, seed: int) -> bool:
    raw_path = RAW / f"{item_id}.png"
    if raw_path.exists() and raw_path.stat().st_size > 20000:
        img = Image.open(raw_path).convert("RGBA")
    else:
        prompt = urllib.parse.quote(STYLE.format(desc=desc))
        url = (
            f"https://image.pollinations.ai/prompt/{prompt}"
            f"?width=576&height=576&nologo=true&seed={seed}&model=flux"
        )
        data = fetch(url)
        img = Image.open(io.BytesIO(data)).convert("RGB")
        cut = remove(img)
        cut.save(raw_path)
        img = cut
    # trim to content bbox, pad to 512 canvas with same margins as original atlas
    alpha = img.getchannel("A")
    box = alpha.getbbox()
    if box is None:
        print(f"  !! {item_id}: empty after cut")
        return False
    item = img.crop(box)
    target = 464
    scale = min(target / item.width, target / item.height)
    item = item.resize(
        (max(1, round(item.width * scale)), max(1, round(item.height * scale))),
        Image.Resampling.LANCZOS,
    )
    icon = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    icon.alpha_composite(item, ((512 - item.width) // 2, (512 - item.height) // 2))
    icon.save(DST / f"gear_{item_id}.png", optimize=True)
    return True


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", default="")
    args = ap.parse_args()
    RAW.mkdir(parents=True, exist_ok=True)
    only = {s for s in args.only.split(",") if s}
    todo = [(k, v) for k, v in ITEMS.items() if k not in PREMIUM and (not only or k in only)]
    ok = 0
    for i, (iid, desc) in enumerate(todo):
        seed = 1000 + abs(hash(iid)) % 9000
        try:
            if process(iid, desc, seed):
                ok += 1
                print(f"[{i+1}/{len(todo)}] {iid} ok")
            else:
                print(f"[{i+1}/{len(todo)}] {iid} FAIL")
        except Exception as e:  # noqa: BLE001
            print(f"[{i+1}/{len(todo)}] {iid} ERROR {e}")
        time.sleep(3)
    print(f"done: {ok}/{len(todo)}")
    if ok < len(todo):
        sys.exit(1)


if __name__ == "__main__":
    main()
