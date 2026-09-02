# Map PreView foundation validation

The shared foundation was built and checked locally on September 2, 2026.

| Check | Result |
| --- | --- |
| Checked-in Gradle 8.14.3 wrapper; clean, check and assemble on Java 17 | Passed |
| JUnit tests | 78 passed; no failures, errors or skipped tests |
| Architecture and naming checks | Passed across eight modules and 73 production Java files |
| Configuration schemas and example | Validated |
| GitHub workflow YAML | Parsed; Java 17/21 build matrix configured |
| Synthetic engine benchmark | Five measured iterations after two warmups; 70 CSV records |
| Dependency integrity | Locked dependencies and SHA-256 verification metadata included |
| Production core artifact | Checked for accidental synthetic-generator inclusion |

The JUnit suite contains 24 core-model, 10 tile-engine, eight rendering, 12 chunk-plan,
11 pregeneration-controller and 13 configuration tests. The raw benchmark data is
available in [the committed CSV](../benchmarks/results/synthetic-java17-2026-09-02.csv).
See [the benchmark protocol](benchmarking.md) for its scope and limitations.

Java 21 and the hosted GitHub workflows have not been executed during this validation.
No native Minecraft or loader adapter has been implemented or validated yet. The
artifacts produced by this foundation are development libraries. They cannot be
installed directly as a Minecraft mod, and synthetic timings do not establish
Minecraft generation performance or compatibility.
