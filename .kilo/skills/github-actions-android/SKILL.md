---
name: github-actions-android
description: Use when creating or repairing GitHub Actions for this Android project: Java/SDK setup, Gradle caching, unit tests, lint, debug APK assembly and artifact upload.
---

# GitHub Actions for Android

## Workflow shape

- One job can build and test a small app; split core tests, CLI/relay integration and the APK when the
  timeout or cache profile differs.
- Use `actions/checkout`, `actions/setup-java` with Temurin 17, `android-actions/setup-android` with
  the SDK platforms/build-tools the project actually uses, and `gradle/actions/setup-gradle` for
  caching.
- Use the wrapper: `./gradlew ...`, never a global Gradle.
- Pin major versions of actions. Do not use `latest` for the Android SDK package list; specify
  `platforms;android-36` and the build-tools version.

## Expected pipeline

```yaml
- run: ./gradlew help --stacktrace
- run: ./gradlew :core:test --stacktrace
- run: ./gradlew :app:testDebugUnitTest --stacktrace
- run: ./gradlew :app:lintDebug --stacktrace
- run: ./gradlew :app:assembleDebug --stacktrace
```

Then verify the APK path is non-empty, print its size and checksum, and upload it:

```yaml
- uses: actions/upload-artifact@v7
  with:
    name: Mika-Android-debug
    path: app/build/outputs/apk/debug/*.apk
    if-no-files-found: error
```

Upload test/lint reports with `if: always()` so failures are diagnosable.

## Triggers and concurrency

- Support `push`, `pull_request` and `workflow_dispatch`.
- Do not hardcode the default branch unless the repository does; a branch filter that excludes the
  default branch is a silent outage. Prefer running pushes broadly or resolve the default branch
  explicitly.
- Use a concurrency group keyed by workflow and PR/ref, with `cancel-in-progress: true`, so a new
  commit does not waste a runner.
- Set `permissions: contents: read` unless a release step genuinely needs more.

## Caching and reliability

- Gradle caching is useful, but never cache secrets or build outputs that contain user data.
- The Android SDK action is fast when the package list is stable; cache what the action provides.
- Avoid network steps that can flake. If a feed test needs the network, mark it clearly and keep it
  out of the required build.
- Keep `timeout-minutes` realistic; an Android build with dependency download can take a while.
- Use `if-no-files-found: error` for the APK; a green run with no artifact is a failed delivery.

## Debugging

1. Read the first failing step and the first Gradle error.
2. Compare Java, SDK, build-tools, Gradle and AGP with a known-good local run.
3. If dependency resolution fails, check the repository list and the lock/version catalog.
4. If an artifact is missing, print `find app/build/outputs -name '*.apk'`.
5. Never claim success from a queued or cancelled run. Report the run URL/status and whether the
   artifact exists.

## Security

- Use GitHub secrets for signing keys only if release signing is explicitly configured.
- Never echo secrets; never commit a keystore.
- Do not run untrusted pull-request code with write permissions or repository secrets.
