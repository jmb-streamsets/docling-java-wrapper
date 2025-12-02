# Repository Guidelines

## Project Structure & Module Organization
Handwritten client helpers live in `src/main/java/com/docling/client`, with simple enums in `src/main/java/com/docling/model`; both wrap the OpenAPI-generated stubs emitted into `build/generated/src/main/java` when Gradle runs. Shared assets (Log4j config, future templates) sit under `src/main/resources`, and the API description that drives generation is `openapi-3.0.json` at the repo root. JVM tests are split between `src/test/java` (fast JUnit 5 unit suites) and `src/test/groovy` for Groovy-based behavioral and integration checks.

## Build, Test, and Development Commands
- `./gradlew clean build` — triggers OpenAPI generation, compiles against Java 17, then runs the full test matrix.
- `./gradlew openApiGenerate` — regenerates the API client from `openapi-3.0.json`; rerun after spec edits before committing.
- `./gradlew run -PmainClass=com.docling.client.WrapperShowcaseJava` — executes the runnable sample that demonstrates batch conversions.
- `./gradlew test` — executes both Groovy and Java tests; scope to one class with `--tests com.docling.client.RetryPolicyTest` when iterating.

## Coding Style & Naming Conventions
Target Java 17 (configured via the Gradle toolchain) with four-space indentation, braces on new lines for classes, and fluent builders for configs like `RetryPolicy`. Place new client-facing utilities under `com.docling.client.*`, leaving generated DTOs under `com.docling.model`. Keep names descriptive (`DoclingTimeoutException`, `UsageAsyncStreaming`) and prefer `final` fields plus try-with-resources for streams. Follow your IDE’s Google/Oracle formatter profile so diffs stay minimal.

## Testing Guidelines
Unit tests rely on JUnit Jupiter assertions, while the Groovy suites spin up lightweight `HttpServer` instances to validate multipart streaming. Name new tests `*Test.java` or `*Test.groovy` to be picked up automatically. Opt-in integration coverage (`DoclingClientIntegrationTest`) only runs when `DOCLING_IT_BASE_URL` and a sample PDF are present; guard any similar tests with `Assumptions` and favor deterministic fake servers.

## Commit & Pull Request Guidelines
History currently only shows the initial import, so keep commit subjects short, imperative, and component-oriented (e.g., `client: tighten retry backoff`). Reference the spec or ticket number in the body when changing `openapi-3.0.json`. Pull requests should explain the user-facing impact, list manual follow-up (code generation, sample commands), and paste relevant CLI output or screenshots. Call out any new environment variables explicitly.

## Security & Configuration Tips
Never commit actual `DOCLING_API_KEY` values; load them at runtime via `DoclingClient.fromEnv()` or run commands like `DOCLING_BASE_URL=http://localhost:5001 ./gradlew run -PmainClass=com.docling.client.UsageSync`. Keep `log4j2.xml` changes minimal and review for sensitive appenders. Integration tests expect `DOCLING_IT_BASE_URL` and optional `DOCLING_LOG_LEVEL`; document these in PRs when they change. Treat `openapi-3.0.json` as the source of truth and immediately refresh generated code after editing it.
