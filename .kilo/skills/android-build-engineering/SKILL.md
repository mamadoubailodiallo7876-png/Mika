---
name: android-build-engineering
description: Use when diagnosing Gradle, Android Gradle Plugin, Kotlin compiler, dependency resolution, build variants, APK assembly or packaging problems in Mika.
---

# Android build engineering

## First facts

- The project uses the Gradle wrapper in `gradle/wrapper/`; always run the wrapper, not a system
  Gradle.
- Java 17 is the expected toolchain. Do not change Java, Kotlin, Gradle or AGP versions without a
  compatibility test and a clear reason.
- `compileSdk` is 36 and `minSdk` is 30. Build tools are pinned in CI.
- The debug application module is `:app`; the APK is produced by `:app:assembleDebug`.

## Triage order

1. Read the first error, not the last warning. Kotlin type errors often cascade.
2. Run the narrowest task: `./gradlew :core:test`, `:app:testDebugUnitTest`, then
   `:app:assembleDebug`.
3. Use `--stacktrace` when the message is opaque. Use `--info` only for dependency/configuration
   problems; it is noisy.
4. Check that generated sources exist before blaming the compiler. Convex/other codegen is not part of
   this Android project; Android resources and AIDL are.
5. If a dependency cannot resolve, check `settings.gradle.kts` repositories and the version catalog
   before editing versions.

## Common failures

| Symptom | Check |
| --- | --- |
| `Unresolved reference` in a new file | Is the module/source set correct? Is the class `internal` in another module? |
| `Cannot access 'X': it is internal` | Same module or same package? Public API needs a deliberate visibility change. |
| `when` must be exhaustive | A sealed/enum branch was added; update every `when` or add a justified `else`. |
| Duplicate class | A dependency was added twice or a jar was bundled manually. |
| `SDK location not found` | `ANDROID_HOME`/`local.properties`; CI sets the SDK explicitly. |
| Resource linking failed | Missing string/style/theme, or a locale-specific resource typo. |
| APK not found | Check the variant/flavor and the actual `app/build/outputs/apk/...` path. |
| OOM during build | Lower workers or increase Gradle heap in `gradle.properties`; do not silently remove checks. |

## APK checks

- `:app:assembleDebug` must finish with `BUILD SUCCESSFUL`.
- The APK exists under `app/build/outputs/apk/debug/` and is non-empty.
- Verify the application id and version from the module configuration, not from memory.
- A debug APK is installable but not a release artifact. Do not publish it as a release.
- Never commit a keystore, password or signing property. If signing secrets are absent, ship the
  unsigned debug APK only.

## CI alignment

The workflows use Java 17, Android SDK 36 and build-tools 35.0.0, plus Gradle caching. Keep local
commands aligned with CI. If a build works locally but not in CI, compare JDK, SDK, build-tools and
Gradle cache state before changing code.

## Honesty rule

If the SDK or wrapper cannot run in the current environment, report the exact missing prerequisite.
Do not claim a build passed because the code "looks right". A workflow run is evidence; a local
compile of a subset is evidence for that subset only.
