# Bench kit

Linux/macOS:

- Build CLI: `./gradlew :mapper-cli:installDist --no-daemon`
- Run: `bash bench/run_bench.sh`

Windows PowerShell:

- Build CLI: `./gradlew :mapper-cli:installDist --no-daemon`
- Run: `pwsh bench/run_bench.ps1`

Aggregate to CSV:

- `python3 bench/aggregate.py`

Notes

- JARS_DIR defaults to `/data/weeks`. Override via env.
- Outputs in `bench/out/*.json/.tiny`, aggregated `bench/ablation.csv`.
- The bash runner uses GNU bash/find features; on macOS, install GNU tools or run on Linux.
