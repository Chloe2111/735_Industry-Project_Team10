# Security & Privacy Module (Java)

A direct port of the JavaScript/Node version — same logic, same two bug
fixes, same 26 tests (plus 6 new tests for the JSON helper Java needed
that JS gets for free), same demo dataset.

## What's here

| File | What it does |
|---|---|
| `TierGate.java` | The single point where Tier 3 / untiered items are refused, before anything else touches them |
| `Grounding.java` | The deterministic checks, including the fixed quote-match logic |
| `Deidentify.java` | Pattern + gazetteer-based de-identification |
| `communityGazetteer.txt` | **Placeholder only** — must be rebuilt with the community before real use |
| `PipelineCore.java` | Wires all three together into one pipeline; auto-switches mock → real Claude API |
| `PipelineDemo.java` | CLI demo — run this live |
| `Server.java` | Plain-JDK web app (no framework) — the clickable-link version |
| `DemoBugFix.java` | Live side-by-side proof of the quote-match bug and its fix |
| `json/JsonUtil.java` | Tiny dependency-free JSON reader/writer, only used for the optional live Claude API call |
| `src/test/java/...` | 32 JUnit 4 tests (26 ported + 6 new for JsonUtil) |

Small data types (`Item`, `MockProfile`, `CodedItem`, `CodedOutput`,
`GroundFlags`, `GroundResult`, `Outcome`, `PipelineResult`) are Java
`record`s — the equivalent of the plain object literals the JS version
passes around.

## Requirements

- **Java 17 or newer** (this project uses `record`s and pattern-matching
  `switch`, both stable since Java 17/21). Check with `java -version`.
- **Maven**, to fetch JUnit and build/run. Check with `mvn -version`. If
  you don't have Maven yet: <https://maven.apache.org/install.html>, or
  on macOS `brew install maven`, or via your IDE (IntelliJ and Eclipse
  both bundle Maven support and will offer to import this project the
  moment they see `pom.xml`).

## Setup

```bash
mvn test                                                       # 32 passed
mvn compile exec:java -Dexec.mainClass=vcnity.pipeline.PipelineDemo   # CLI walkthrough
mvn compile exec:java -Dexec.mainClass=vcnity.pipeline.DemoBugFix     # the live bug demonstration
mvn compile exec:java -Dexec.mainClass=vcnity.pipeline.Server         # then open http://localhost:3000
```

Run these from the project root (the folder with `pom.xml` in it) —
`communityGazetteer.txt` is loaded from the current working directory,
same convention as the JS version.

Or build a runnable jar once and reuse it:

```bash
mvn package
java -jar target/security-module.jar          # runs PipelineDemo
```

To use the real Claude API instead of the mock coder:

```bash
export ANTHROPIC_API_KEY=your-key-here
mvn compile exec:java -Dexec.mainClass=vcnity.pipeline.PipelineDemo
```

## The two bugs, fixed (same as the JS/Python versions)

**1. Tier 3 gate ordering.** `TierGate.tierGate()` is the literal first
method call in the pipeline — Tier 3 and untiered items are rejected
before transcription, de-identification, or any API call.

**2. Quote-match logic.** The original design stripped all whitespace
before comparing quote to source as a substring, letting fabricated
fragments pass as "grounded." The fix normalises whitespace (collapses,
doesn't delete it) and matches on real word boundaries. Run the
`DemoBugFix` class to see the old logic fail and the new logic catch it,
side by side.

## Notes on the port (things that are Java-specific)

- **No third-party dependency in `src/main`.** JUnit is a *test-only*
  dependency. JSON handling for the optional live-Claude-API call uses
  a small hand-rolled `JsonUtil` (Java has nothing built in like
  `JSON.parse`/`JSON.stringify`), and the web demo uses the JDK's own
  `com.sun.net.httpserver.HttpServer` instead of pulling in Spring Boot
  or similar just for a one-page demo. If your team already has a
  standard HTTP framework or JSON library, it's easy to swap either of
  these out — nothing else in the project depends on their internals.
- **`Pattern.quote(...)` instead of manual regex-escaping.** The JS
  version hand-escapes regex metacharacters before building a dynamic
  pattern. Java's `Pattern.quote()` does the same job more safely, so
  `Grounding.quoteIsGrounded` and `Deidentify`'s gazetteer matching use
  that instead.
- **Records, not classes with getters/setters.** `Item`, `Outcome`, etc.
  are immutable `record`s — this is the closest Java equivalent to the
  plain object literals JS passes around, and needs no Lombok or
  boilerplate.

## What's NOT done yet

- `communityGazetteer.txt` is a placeholder — the real list must be
  built with the community, not invented by the dev team.
- The contradiction-scan check in `Grounding.java` is a hook
  (`contradictsPriorTheme` field), not a real NLP model yet.
- No integration with actual Whisper transcription or a real intake
  source yet — these modules are the guardrail layer that sits around
  those, not a replacement for them.
- Phone/email regexes are loose — tune against real (synthetic) sample
  data before the calibration test.
- The live-Claude-API path (`callClaude` in `PipelineCore.java`) is
  written but only exercised when `ANTHROPIC_API_KEY` is set — the same
  limitation the JS version has.

## Suggested next step

Wire `TierGate.tierGate()` and `Deidentify.deidentify()` into your
actual intake code so every item passes through them before it reaches
Whisper or the Claude API. Then wire `Grounding.ground()` around your
Claude API call so its output is checked before being written to the
"clean" output set.
