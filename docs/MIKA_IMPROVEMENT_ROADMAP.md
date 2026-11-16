# Mika improvement roadmap

The order below is intentional. Safety, truthfulness and a working build come before breadth.

## Phase 0 — audit and guardrails

Completed in this change:

- Verified the real module graph, agent loop, capability registry, dispatcher, storage and UI shell.
- Identified the prompt-surface goldens that constrain new always-on tools.
- Kept existing conversations, provider settings, memory, permissions and navigation untouched.
- Added the career domain as a separate, testable core package.

## Phase 1 — career foundation

Completed in this change:

- Structured career profile with confirmed and proposed facts.
- Truth guards, profile completeness, validation and merge-without-overwrite import.
- Explainable job matching with weighted factors and score caps, never a hire probability.
- Job extraction, duplicate detection, suspicious-listing flags and local search.
- Application state machine with approval checkpoints and evidence-based submission recording.
- Resume and cover-letter generation from confirmed facts, DOCX/PDF/TXT writers.
- Interview preparation and structural practice feedback.
- Career capability with a small action-based tool surface registered through the existing dispatcher.
- Career workspace with profile, job, application and document screens.
- Keystore-encrypted, atomic, versioned career storage with audit and erase controls.
- Localized docs, the repository skill library and a focused APK workflow.

## Phase 2 — deeper automation, still human-approved

1. Add official job-feed adapters where terms permit, behind a per-site capability flag.
2. Add a form-filling executor that uses the browser port only after `job_application` has produced a
   mapping, with a diff shown to the user before typing.
3. Add resume/cover-letter template selection with ATS-oriented previews and print-safe PDF layout.
4. Add a background export queue so large documents do not block the UI thread.
5. Add end-to-end Compose tests for profile editing, approval, submission recording and export.
6. Add a per-application timeline UI with editable follow-up reminders.
7. Add opt-in local analytics for application response rates, clearly labelled as historical counts.

## Phase 3 — specialist agents

The current design uses one loop plus capability-scoped tool domains. The next step is to make the
specialist roles explicit where they add value:

- A Career Manager prompt that plans a workflow and delegates read-only research to child runs.
- Resume, cover-letter, tracking and interview specialists as tool-domain presets with their own
  verification checks, not as independent loops.
- A shared structured context object containing ids, confirmed facts and blockers; no free-form
  profile text passed between agents.
- Execution budgets, cancellation, retry with backoff for network reads, and interrupted-run recovery.
- A visible progress model: proposed, executing, verified, failed, awaiting approval.

## Phase 4 — quality and scale

- Cache compiled match factors for large job lists instead of recomputing every row.
- Add migration fixtures for every career schema version.
- Add redaction tests for every tool result that can contain contact or document text.
- Add accessibility audits for the career screen and all new dialogs.
- Add a privacy dashboard that shows exactly what is stored, where it is exported and how to erase it.
- Consider an encrypted export container for users who need to move career data between devices.

## Non-goals

- Auto-submitting applications without the user's explicit approval.
- Creating accounts, bypassing CAPTCHAs, MFA, identity checks or site restrictions.
- Inventing experience, education, metrics or employer details to improve a score.
- Replacing the existing agent loop, model providers, memory or navigation wholesale.
