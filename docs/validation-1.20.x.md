# Validation of the 1.20.x branch

Validated on 2026-09-02 for `0.3.0-alpha.1`. Every row below was built and executed, including a normal Fabric client loading the installable remapped JAR from its `mods` directory.

The [machine-readable summary](validation-1.20.x.json) records exact artifact SHA-256 hashes and per-target results.

| Minecraft | Game Java | Native JUnit | Native server GameTest | Packaged graphical client | Artifact checks |
| --- | --- | --- | --- | --- | --- |
| 1.20 | 17 | 11 passed | Passed | Passed | Passed |
| 1.20.1 | 17 | 11 passed | Passed | Passed | Passed |
| 1.20.2 | 17 | 11 passed | Passed | Passed | Passed |
| 1.20.3 | 17 | 11 passed | Passed | Passed | Passed |
| 1.20.4 | 17 | 11 passed | Passed | Passed | Passed |
| 1.20.5 | 21 | 11 passed | Passed | Passed | Passed |
| 1.20.6 | 21 | 11 passed | Passed | Passed | Passed |

The shared suite contains **84 passing cases on both Java 17 and Java 21**, covering cache identity, stale-session cancellation, bounded scheduling, rendering handoffs, wrapped cancellation, sparse traversal, JSON persistence and save/checkpoint invariants. Each Fabric target additionally passes 11 native JUnit cases: 95 JUnit cases across its shared and native suites, plus its server GameTest.

## Native generator and server checks

Native JUnit runs through Fabric Loader's JUnit launcher with real vanilla datapacks and bound registry tags. It checks biome and raw-height parity at multiple seeds, dimensions and negative coordinates; independent worldgen workers; flat worlds; an extra dimension; native slime rules; estimated structure candidates; registry fingerprints; Generate Structures behavior; and the transformed world-creation invoker.

On every target, the server GameTest processes a 25-chunk area through Minecraft's FULL chunk pipeline, checks the two-chunk in-flight limit, pauses and drains work, cancels, restores the saved job, completes all chunks and verifies the final persisted checkpoint. GameTests run on the target game JDK without build instrumentation. The test-only entrypoint is excluded from distributed JARs.

## Packaged client checks

Each client uses official, unmodified Minecraft files, Fabric Loader **0.16.14**, the exact Fabric API pin in [the target matrix](../gradle/targets.json), and the installable Map PreView JAR. These launches do not use development class directories or a build agent. The tested copy's SHA-256 matches the delivered artifact.

The same interaction sequence passed on all seven versions:

1. Open the Map PreView button in the vanilla world-creation footer.
2. Render populated biome tiles with a sharp, unobscured map background.
3. Replace the seed with `123456789` and verify that the biome pixels change.
4. Pan and zoom, then render the raw-height layer.
5. Rapidly switch layers and cycle through all three vanilla dimensions without displaying expected cancellation as an error.
6. Select **Use seed**, reopen the preview, and reproduce the same biome pixels for that seed.

On 1.20.6, **Create world** also completed through vanilla's creation flow. The resulting world's native `level.dat` contains seed `123456789`. The integrated-server pregeneration panel then completed a square centered at `(4096, 4096)`, radius 32 blocks, with at most two chunks in flight. Its saved checkpoint reports **25 completed chunks, zero failed attempts and no pending chunks**. Saving and quitting returned to the title screen and the client exited normally.

## Reproduction and limits

Run the shared suite on JDK 17 and 21 with `./gradlew -Ploader=none check assemble`. Run the seven native builds and server tests with JDK 21 launching Gradle and both game JDKs installed:

```sh
python3 scripts/build_fabric_matrix.py --game-tests
python3 scripts/check_architecture.py
```

For graphical acceptance, install each exact target as described in [Fabric installation](fabric-1.20.x.md) and repeat the interaction sequence above. CI runs the shared suites, seven native targets, server GameTests and artifact verification; the graphical checks were performed locally, not by GitHub Actions.

The local clients used Linux x86-64, OpenJDK 17.0.20 or OpenJDK 21.0.2, a private Xvfb display with Mesa software rendering, an 854x480 window and GUI scale 2. This establishes functional rendering, not hardware GPU performance or third-party modpack compatibility. Minecraft's online account and Realms services were unavailable in the test environment; local world creation and generation were exercised.

Unix-domain sockets are unavailable in the build environment. A local build-only probe reports that capability as absent to Loom; it is absent from the repository, game/test JVMs and delivered JARs. Downloaded dependencies remain checksum-verified. Only Loom's locally generated mapping/merge outputs are excluded from fixed download checksums, since their bytes are not portable between build environments.

Forge, NeoForge and Quilt remain prepared port descriptors, not completed loader implementations. Native 3D, accurate carved/decorated cave blocks, verified structure starts and optional integrations remain listed in the [requirements](requirements.md) and [compatibility](compatibility.md) matrices. Passing the tests above does not mean every feature in the source PDFs or every modpack is implemented and verified.
