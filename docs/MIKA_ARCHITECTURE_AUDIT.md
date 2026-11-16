# Mika architecture audit

This audit describes the repository as it is, not an intended architecture. It was written after
reading the Gradle modules, the agent loop, the capability and tool plumbing, the storage layer, the
Compose screens and the existing workflows. It records what was verified, what is incomplete, and
which constraints shaped the career work.

## Modules and entry points

- `settings.gradle.kts` includes `:core` and `:app`. `core` is the JVM/Android library that owns the
  agent contract, tools, prompts, memory interfaces, persistence contracts and most tests. `app` owns
  Android services, accessibility and browser ports, model providers, storage implementations and
  the Compose UI.
- The process container is `MikaApp`. It creates one shared instance of the conversation store,
  provider store, memory runtime, run store, sandbox, browser, MCP runtime and the new career store.
- `MainActivity` hosts the Compose tree and the `when (nav.current)` route switch. `NavStack` keeps a
  simple stack of `Route` values; the chat page is always the root and secondary pages return to it.
- `TaskService` owns long-running task lifecycle. `TaskRun` assembles the model, tools, memory,
  prompt, approval gate and child-run host for one run.

## Agent loop and model calls

`AgentLoop` is the single reasoning loop. It receives a model, a `ToolDispatcher`, the system prompt,
confirmation/approval hooks, stop rules and a set of feature flags. It streams assistant events and
tool results through a sink, records run events, and applies provider-specific model capabilities.
`ModelCaller` and the provider store keep model configuration and API keys outside the loop; the
career work does not add a competing loop.

`RunSpec` scopes a run to a set of capability ids. `RunAssembler` opens only the providers allowed by
the spec, attaches their tools and prompt sections, and applies read-only/sub-agent rules. A child run
is normally limited to web, sandbox and read-only career tools. This is the extension point used for
specialist research; the career capability is not a second orchestrator.

## Capabilities, tools and prompts

- A capability is an internal `Capability`: an id, a status, a list of `AgentTool`s, optional prompt
  sections, an optional screen surface and a task-start hook.
- `ToolDef` defines the model-facing name, description and JSON schema. `Args` validates values and
  rejects unknown fields at execution time. `ToolDispatcher` resolves names, routes read-only and
  write calls, applies guards and records results.
- `allTools()` is the legacy, always-on tool set used by the prompt-surface and risk tests. The career
  tools are not added there. They are exposed only through `CareerCapability`, registered in
  `toolDispatcher(...)` when a `CareerStore` is supplied. Their names are declared in
  `knownToolNames()` so skill and documentation references validate.
- The existing prompt layout is covered by `PromptLayoutTest`, `PromptSurfaceTest` and `ToolRiskTest`.
  Those tests assert the exact legacy prompt files and tool/risk sets. The career capability therefore
  keeps its policy text inline and uses tool descriptions instead of adding prompt files. The career
  text has its own review surface in `CareerCapability`.
- Tool results are JSON. `ok`/`fail` results distinguish proposed, executed and failed operations.
  Career tools are local, off-screen, read-only or write-declared, and never pretend that an external
  submission happened.

## Memory, knowledge and skills

`MemoryRuntime` stores user memory and the profile. `ProfileStore` is separate from the career
profile: the former is Mika's long-term user profile, the latter is a structured career record.
Knowledge and skills are read-only catalogs; personal skills are user-authored files. The career
subsystem does not write into memory automatically, because career documents and contact details do
not belong in free-form memory.

`skills` in the catalog are loaded by the tool surface; the repository-level `.kilo/skills/` library
is for development agents, not the phone runtime. Keeping these separate avoids leaking developer
instructions into a user's model context.

## Browser, phone, sandbox and MCP

- Phone automation uses an accessibility service and a hidden background display. `PhoneCapability`
  exposes screen reads, taps, text entry, navigation and app launching.
- Web automation uses the built-in browser through `WebCapability`, with tab management, page reads,
  form interaction, uploads and page scripts. `WorkspacePathCheck` constrains uploads to a task's
  workspace.
- The sandbox runs commands and scripts in a Linux environment when the device supports it. It is not
  available everywhere and is intentionally not used for career document generation.
- MCP servers are external tool providers. `McpClientProvider` maps remote tools into capabilities;
  remote tool outputs are untrusted. The career design treats all job descriptions and website text
  the same way: data, never instructions.

## Storage

Conversations use `ConversationStore`; runs use `RunStore`/`FileRunStateStore`; provider keys use the
Android Keystore through `KeyCipher`; memory has its own encrypted file. Existing stores were not
replaced or migrated.

The career store is a new append-only JSON document written atomically through a temp file and
rename. It is versioned with `CAREER_SCHEMA`, ignores unknown fields, preserves a corrupt copy instead
of deleting it, serialises updates through one lock and keeps a small audit log containing action
names and ids only. `KeystoreCareerCodec` encrypts the document with a Keystore key before it reaches
disk. Generated documents live in a separate `exports/` directory and are never uploaded by core.

## UI

The app uses Compose and a mature custom Material 3 theme (`MikaTheme`, `MikaType`, `MikaShapes`,
`MikaIcons`). Reusable components include SubPage, MikaCard, GroupCard, ListRow, MikaButton,
SegmentedControl, StateCard and MikaDialog. Screens are thin and mostly read immutable snapshots
or flows from app services.

The new `CareerScreen` follows that system rather than introducing a second design language. It uses
the existing page shell, cards, rows, buttons, type scale and colors. It adds no global theme
mutation and no new navigation model; it adds one route and one drawer entry.

## CI and delivery

`ci.yml` runs core tests, CLI contract tests, translation checks, app unit tests, debug assembly,
device-test compilation and relay integration. `build-apk.yml` builds debug and perf APKs and uploads
artifacts. `android-build.yml` is the focused APK pipeline for this assignment: it validates the
Gradle configuration, runs core and app unit tests, attempts lint, assembles the debug APK, verifies
the file and uploads `Mika-Android-debug`.

## Verified gaps and technical debt

- No Android SDK is installed in this workspace, so the APK cannot be assembled locally. The workflow
  is the executable verification path; a workflow result must not be described as successful until it
  actually completes.
- The existing prompt-surface goldens intentionally constrain new always-on tools. New capabilities
  must either update those goldens with a justified review or remain capability-scoped.
- The browser cannot bypass sign-in walls, CAPTCHAs, MFA or site terms. A recruitment site that
  requires those steps must hand control to the user.
- DOCX/PDF extraction is deliberately conservative. Scanned PDFs and unusual layouts still need a
  text-extraction fallback or user correction; the importer marks uncertain values instead of
  guessing.
- Job feeds and official APIs are adapter-friendly but not all wired. The current discovery path is
  user-pasted text plus pages the user opens; the adapter registry documents what each site permits.
- Career tools are local and synchronous. Large imports and document exports should eventually move
  to a background dispatcher if profiles grow much larger.
- Localization for the new career screen is currently English copy. The existing localized resources
  and translation checks are unchanged.
- Instrumentation coverage for the career UI is not added; the domain and tool contracts have unit
  coverage, and the screen is compile-checked by CI.
