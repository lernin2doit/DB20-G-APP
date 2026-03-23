#!/usr/bin/env python3
"""Quick verification of generated PCB net assignments."""
import re
import os

pcb_path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                        "DB20G-Interface-v10.kicad_pcb")

with open(pcb_path, "r") as f:
    content = f.read()

# Find all footprints and their pad-net assignments
fps = re.finditer(r'\(footprint\s+"([^"]+)"', content)
ref_pattern = re.compile(r'\(property\s+"Reference"\s+"([^"]+)"')
pad_net_pattern = re.compile(
    r'\(pad\s+"([^"]+)".*?\(net\s+(\d+)\s+"([^"]*)"\)', re.DOTALL
)

fp_starts = [(m.start(), m.group(1)) for m in fps]

for i, (start, lib_id) in enumerate(fp_starts):
    end = fp_starts[i + 1][0] if i + 1 < len(fp_starts) else len(content)
    section = content[start:end]

    ref_m = ref_pattern.search(section)
    ref = ref_m.group(1) if ref_m else "???"

    pads = pad_net_pattern.findall(section)

    if ref in ("U1", "K1", "J2"):
        print(f"\n=== {ref} ({lib_id}) ===")
        for pn, ni, nn in pads:
            print(f"  Pad {pn:>2s} -> net {ni:>2s} ({nn})")
        print(f"  [{len(pads)} pads with nets]")

# Summary
total_fps = len(fp_starts)
print(f"\nTotal footprints: {total_fps}")
