# Mika skills guide

## What a skill is

A skill is a directory under `.kilo/skills/` containing a `SKILL.md` file with YAML frontmatter and
practical instructions. Skills are for development agents working in this repository. They are
separate from the phone runtime's knowledge/skill catalog and from the user's personal skills.

Each skill must have:

```yaml
---
name: skill-name
description: When to use this skill, in one specific sentence.
---
```

The `name` matches the directory. The description names the trigger, the task and the boundary so an
agent can decide whether to load it. The body contains a workflow, concrete checks, examples and
failure handling; it is not a motivation essay.

## How to use a skill

1. Read `.kilo/skills/<name>/SKILL.md` when the task matches its description.
2. Follow its workflow before editing files.
3. Use the repository's real commands and conventions; a skill does not override project rules.
4. Run the checks the skill names. If a check cannot run (for example, no Android SDK), report that
   limitation instead of claiming success.
5. Keep user data and credentials out of examples, logs and commits.

Skills compose. A build task may use `android-build-engineering` and `github-actions-android`; a
career feature may use `career-profile-builder`, `professional-resume-writer` and
`application-security`. Load only the skills the task actually needs.

## Skill index

| Skill | Use it when |
| --- | --- |
| `android-kotlin-engineering` | Changing Kotlin/Android architecture, coroutines, Flow, lifecycle or performance. |
| `jetpack-compose-design` | Building or reviewing Compose/Material 3 screens, components, motion or accessibility. |
| `android-build-engineering` | Diagnosing Gradle, AGP, Kotlin compiler, variants or APK assembly. |
| `android-testing-qa` | Adding unit, integration, Compose, instrumentation or regression tests. |
| `github-actions-android` | Creating or repairing Android CI, caching, SDK setup and APK artifacts. |
| `application-security` | Handling secrets, auth, permissions, encryption, privacy or untrusted input. |
| `ai-agent-architecture` | Changing the agent loop, tool schemas, context, memory, retries or evaluation. |
| `mcp-integration` | Adding or debugging MCP clients, tool discovery or remote tool execution. |
| `browser-automation` | Reading pages, identifying forms, uploading files or verifying a web action. |
| `ai-memory-management` | Designing structured memory, retrieval, retention or user-controlled deletion. |
| `career-profile-builder` | Implementing or reviewing the candidate profile and import flows. |
| `professional-resume-writer` | Generating, tailoring or reviewing resumes and ATS formatting. |
| `cover-letter-writer` | Generating, personalising or reviewing cover letters. |
| `job-discovery-research` | Adding job sources, feeds, extraction, deduplication or safety checks. |
| `job-requirements-analysis` | Turning a listing into explicit requirements, skills and eligibility rules. |
| `job-matching-and-ranking` | Implementing or explaining match factors, caps and ranking. |
| `job-application-assistant` | Building form mapping, approval, submission recording or verification. |
| `interview-preparation` | Generating interview questions, practice feedback or company research checklists. |
| `application-tracking` | Implementing status transitions, follow-ups, statistics or history. |
| `recruitment-website-adapters` | Adding a site adapter, recognition rules or adapter capabilities. |

## Creating a new skill

1. Check the index; extend an existing skill instead of creating a near-duplicate.
2. Create the directory and `SKILL.md`.
3. Write a specific description: include the trigger words a developer would use.
4. Include: preconditions, workflow, validation, failure handling and at least one concrete example.
5. Add it to the index above.
6. Run the Markdown/path checks (`DocsConsistencyTest` covers docs, not skill files) and a YAML
   frontmatter lint if available.

## Quality bar

- No empty placeholders, no generic "be careful" advice.
- Prefer exact file paths and class names from this repository.
- Separate verified facts from assumptions.
- State what must never happen: invented credentials, bypassed CAPTCHAs, unapproved submissions,
  secrets in logs.
- Keep instructions short enough to load, long enough to be useful.
