# Map PreView development rules

- Keep source comments, API descriptions, documentation and diagnostics in English.
- Use `Map PreView` at runtime, `MapPreView` in Java and `map_preview` as the machine ID. Keep the distribution prefix out of source, logs and configuration.
- Keep all shared modules free from Minecraft, loader, graphics and optional-mod implementation imports.
- Preserve Java 17 source/bytecode compatibility. The first game adapter is Fabric 1.20.1 using Yarn mappings.
- Use the active registries and generator. Synthetic worldgen belongs only in test fixtures and benchmarks.
- Keep sampling lazy, CPU/GPU resources bounded, worldgen cancellation cooperative and native pregeneration server-owned.
- Any fast backend must pass parity tests against its generic/native generator before it becomes the preferred provider.
- Run `./gradlew check assemble` and `python3 scripts/check_architecture.py` before publishing changes. Add regression tests for concrete lifecycle, data identity or correctness risks.
- Update the requirements and compatibility matrices when implementation status changes. Never label an adapter as tested without actual game evidence.
