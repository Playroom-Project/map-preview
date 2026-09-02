# Third-party notices

The Gradle wrapper scripts and binary originate from the official, checksum-verified Gradle 8.14.3 distribution. Gradle is licensed under Apache License 2.0; the license is included at `gradle/wrapper/LICENSE`.

Gson 2.13.1 (Google) is used for JSON persistence under Apache License 2.0. The Fabric distributable bundles Gson with its package names relocated to `io.github.playroomproject.mappreview.internal.gson`, isolating it from Minecraft's JSON library. Its behavior is otherwise unmodified. The full license is included in the mod at `META-INF/licenses/Gson-Apache-2.0.txt`. This notice is included at `META-INF/THIRD_PARTY_NOTICES.md`.

Fabric Loader and Fabric API are separately installed runtime dependencies. Minecraft, Yarn mappings, Fabric Loom and Shadow are used for development; they are not bundled in the mod. Error Prone annotations are compile-time only. JUnit and its dependencies are test-only. Published dependency metadata and checksums are recorded in Gradle dependency verification; the shared modules additionally use dependency lock files.

No Minecraft, worldgen-mod, World Preview, Genesis, Cubiomes, Chunky or FancyMenu implementation code is copied into this repository. The supplied design PDFs inform the architecture and traceability documentation.

These third-party licenses do not select a distribution license for the original Map PreView source.
