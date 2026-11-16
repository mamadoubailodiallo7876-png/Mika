---
name: android-kotlin-engineering
description: Use when changing Mika's Kotlin or Android architecture, coroutines, Flow state, lifecycle handling, dependency wiring, performance or memory behavior in the core and app modules.
---

# Android and Kotlin engineering for Mika

## Before editing

- Read the module that owns the behavior: `core` for agent/tool/domain contracts, `app` for Android
  ports, services, Compose and storage implementations.
- Trace the call path end to end. In this repository that often starts at `TaskRun` or
  `MikaApp`, passes through a capability and ends at a port. Do not add a parallel path.
- Check existing tests and golden files before changing a public tool name, prompt file or schema.

## Architecture rules

1. Keep domain logic free of Android framework types. Put it in `core` under a focused package and
   test it on the JVM.
2. Keep Android dependencies at the edge: `MikaApp`, services, providers and Compose screens.
3. Prefer constructor injection and interfaces over singletons. `MikaApp` is the single process
   container; pass what a component needs rather than reaching for a global.
4. Use `StateFlow`/`Flow` for state that survives a screen, and immutable snapshots for one-shot
   reads. Do not mirror server or file state into a second source of truth.
5. Use structured concurrency: a scope owned by the component, `supervisorScope` only when sibling
   failures are genuinely independent, and cancellation checks in long loops.
6. Never block the main thread with file, network or document work. Use `Dispatchers.IO` or a managed
   worker and publish the result back to state.

## Coroutines and Flow checklist

- A `suspend` function must be main-safe or clearly documented as blocking.
- Catch `CancellationException` and rethrow it; do not swallow it in a broad `catch (e: Exception)`.
- Avoid `GlobalScope`. Use an injected scope or `rememberCoroutineScope` for UI work.
- `collectAsStateWithLifecycle` for Compose; avoid collecting in a composable without lifecycle.
- Debounce or conflate rapid inputs; do not launch a coroutine per keystroke.
- A Flow that writes to disk should serialize writes; use a mutex or a single actor.
- Test time and concurrency with a test dispatcher, not `Thread.sleep`.

## Performance and memory

- Use `LazyColumn` keys for lists; avoid large composables recomposing on every state read.
- Keep images downsampled; never decode full-resolution bitmaps on the main thread.
- Avoid holding `Activity` or `Context` in long-lived objects; use `applicationContext` for stores.
- Close streams, cursors and file handles with `use`.
- Measure before claiming a speedup. A trace, benchmark or allocation count is evidence; intuition is
  not.

## Mika-specific checks

- Run the smallest relevant test: `./gradlew :core:test` for domain changes, then
  `./gradlew :app:testDebugUnitTest` when app wiring changed.
- If you touch a capability, verify `RunAssembler` filtering and the child-run rules.
- If you add a tool, register it through a capability; do not edit `allTools()` unless the task
  explicitly requires an always-on tool and its golden tests are updated deliberately.
- Preserve existing user data. New stores must be additive, versioned and recoverable.

## Failure handling

- Return typed errors for expected failures; let programming errors surface in tests.
- Do not catch an exception just to return an empty list or a default value.
- Log action names and ids, never API keys, document text or contact details.
- When a dependency is unavailable, degrade explicitly (status, message) rather than pretending the
  feature worked.
