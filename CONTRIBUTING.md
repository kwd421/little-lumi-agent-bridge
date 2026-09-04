# Contributing

Contributions are welcome for the bridge code, tests, installer, documentation, and compatibility fixes.

Please do not submit:

- Little LUMI binaries or decompiled proprietary source;
- character images, voice packs, persona files, or game assets;
- API keys, OAuth material, private chat logs, or personal paths that should not be public.

Run the dependency-free test suite before opening a pull request:

```bash
./scripts/test.sh
```

On Windows with JDK 17+:

```powershell
.\scripts\test.ps1
```

Keep new capabilities disabled or read-only by default when they can access local or external state.
