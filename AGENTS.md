# Map PreView development rules

- Keep source comments, API descriptions, documentation and diagnostics in English.
- Use `Map PreView` at runtime, `MapPreView` in Java and `map_preview` as the machine ID. Keep the distribution prefix out of source, logs and configuration.
- Keep all shared modules free from Minecraft, loader, graphics and optional-mod implementation imports.
- Preserve Java 17 source/bytecode compatibility in shared libraries and Minecraft 1.20 through 1.20.4. Minecraft 1.20.5/1.20.6 adapters require Java 21. Fabric uses Yarn mappings; Gradle/Loom itself runs on JDK 21.
- Use the active registries and generator. Synthetic worldgen belongs only in test fixtures and benchmarks.
- Keep sampling lazy, CPU/GPU resources bounded, worldgen cancellation cooperative and native pregeneration server-owned.
- Any fast backend must pass parity tests against its generic/native generator before it becomes the preferred provider.
- Run `./gradlew check assemble` and `python3 scripts/check_architecture.py` before publishing changes. Add regression tests for concrete lifecycle, data identity or correctness risks.
- Update the requirements and compatibility matrices when implementation status changes. Never label an adapter as tested without actual game evidence.
