# AGENTS.md

When modifying this repository:

- Keep the runtime dependency-free Java 17 unless a dependency has a clear interoperability benefit.
- Preserve the external-bridge design; do not package or patch proprietary Little LUMI assets.
- Keep network listeners loopback-only and local/state-changing tools off or read-only by default.
- Never log Authorization headers or commit user credentials.
- Add or update tests for tool routing, safety gates, and protocol changes.
- Run `./scripts/test.sh` before committing.
