<!-- AUTOGEN: BYTECODEMAPPER DOC v1 -->
# Bench Plan

Purpose
Track quality and stability across weekly OSRS jars with reproducible, deterministic runs.

Bench CLI

```
:mapper-cli: bench --in data/weeks --out build/bench.json [--ablate calls,micro,opcode,strings,fields,refine]
```

Metrics
- AUC(S_micro), ΔOscillation, Top-k stability.
- Compare with and without refinement, holding TAU_ACCEPT=0.60 constant.
