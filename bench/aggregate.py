# FILE: bench/aggregate.py
# CODEGEN-BEGIN: bench-aggregate-py
#!/usr/bin/env python3
import os, json, csv, re

OUT_DIR = "bench/out"
CSV_OUT = "bench/ablation.csv"

def parse_pair_cfg(filename):
  m = re.match(r"(.+?)_(.+?)\.(json|tiny)$", os.path.basename(filename))
  return (m.group(1), m.group(2), m.group(3)) if m else (None,None,None)

def safe_get(d, *keys, default=None):
  cur = d
  for k in keys:
    if not isinstance(cur, dict) or k not in cur: return default
    cur = cur[k]
  return cur

rows = []
for root,_,files in os.walk(OUT_DIR):
  for f in files:
    if not f.endswith(".json"): continue
    path = os.path.join(root,f)
    pair,cfg,_ = parse_pair_cfg(path)
    if not pair: continue
    with open(path,"r",encoding="utf-8") as fh:
      j = json.load(fh)
    tiny_path = os.path.join(root, f.replace(".json",".tiny"))
    mappings_lines = None
    if os.path.exists(tiny_path):
      with open(tiny_path,"rb") as tf:
        mappings_lines = sum(1 for _ in tf)

    rows.append({
      "pair": pair,
      "config": cfg,
      # candidate stats
      "cand_exact_med": safe_get(j,"candidate_stats","cand_count_exact_median"),
      "cand_exact_p95": safe_get(j,"candidate_stats","cand_count_exact_p95"),
      "cand_near_med":  safe_get(j,"candidate_stats","cand_count_near_median"),
      "cand_near_p95":  safe_get(j,"candidate_stats","cand_count_near_p95"),
      # WL-relaxed thresholds & counters
      "wl_relaxed_l1":  safe_get(j,"wl_relaxed_l1"),
      "wl_size_band":   safe_get(j,"wl_relaxed_size_band"),
      "wl_gate_passes": safe_get(j,"wl_relaxed_gate_passes"),
      "wl_candidates":  safe_get(j,"wl_relaxed_candidates"),
      "wl_hits":        safe_get(j,"wl_relaxed_hits"),
      "wl_accepted":    safe_get(j,"wl_relaxed_accepted"),
      # Flattening telemetry
      "flat_detected":  safe_get(j,"flattening_detected"),
      "near_before":    safe_get(j,"near_before_gates"),
      "near_after":     safe_get(j,"near_after_gates"),
      # proxy for acceptance (lines in tiny)
      "mappings_lines": mappings_lines
    })

rows.sort(key=lambda r: (r["pair"], r["config"]))

os.makedirs(os.path.dirname(CSV_OUT), exist_ok=True)
with open(CSV_OUT,"w",newline="",encoding="utf-8") as fh:
  w = csv.DictWriter(fh, fieldnames=[
    "pair","config",
    "cand_exact_med","cand_exact_p95","cand_near_med","cand_near_p95",
    "wl_relaxed_l1","wl_size_band","wl_gate_passes","wl_candidates","wl_hits","wl_accepted",
    "flat_detected","near_before","near_after",
    "mappings_lines"
  ])
  w.writeheader()
  for r in rows:
    w.writerow(r)

print(f"[OK] Wrote {CSV_OUT} with {len(rows)} rows")
# CODEGEN-END: bench-aggregate-py
