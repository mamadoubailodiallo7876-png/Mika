# Mika Career Agent design

## Product promise

The career workspace keeps one truthful record of the candidate, turns it into documents, finds and
explains fit, prepares applications and tracks the result. It is a local-first assistant with explicit
approval checkpoints. It is not an auto-apply bot.

## Domain model

All career records live in `CareerData` under `CareerModels.kt`:

- `CandidateProfile`: contact details, links, work authorization, preferences, experience,
  education, skills, certifications, projects, languages and qualifications. Every fact carries
  `FactSource` and a `confirmed` flag.
- `JobPosting`: title, company, description, location, remote arrangement, salary text, employment
  type, requirements, skills, experience, deadline, source, URLs, discovery time, status, fingerprint
  and suspicious-listing flags.
- `ApplicationRecord`: job link, role, company, status, documents, events, approval, submission
  evidence, verification, interview and follow-up times.
- `CareerDocument`: versioned resume or cover letter, with the fact ids used to produce it.
- `WebsiteAdapter`: recognition hosts, capabilities, notes and a terms note. Adapters are data, not
  hardcoded branches in the agent.

`CAREER_SCHEMA` versions the document. Unknown fields are ignored on read so older builds can open
newer minor additions. Existing Mika conversations, settings and memory are separate stores.

## Truthfulness

`ProfileQuality` owns completeness, validation and `TruthGuard`. The document engine only emits facts
that are confirmed. Imported facts are proposals:

1. `ProfileImporter` extracts field candidates with evidence and confidence.
2. `CareerService.mergeImportedProfile` adds them without overwriting existing confirmed values.
3. The UI/agent asks the user to confirm them.
4. `DocumentEngine` and `TruthCheck` exclude or flag anything still unconfirmed.

The rule is simple: no invented employer, title, date, qualification, metric or skill. A document can
be less impressive than the user hopes; it cannot be false.

## Matching

`JobMatcher` compares confirmed profile data with the listing on weighted factors: skills, experience,
education, location, preferences and salary. Each `JobMatch` includes the component scores, matched
and missing skills, missing qualifications, strengths, weaknesses, recommendations and a plain
explanation. Must-have skills and deal-breakers cap the score and say why. The result is an estimate,
not a probability of being hired, and the UI labels it that way.

## Application state machine

`ApplicationWorkflow` defines the allowed transitions. The important separation:

- `prepare` links generated documents and moves a discovered/shortlisted application to preparing.
- `requestApproval` moves a prepared application to awaiting approval. This is the last step the
  model may take.
- `submit` is only called by `CareerService.recordSubmission`, which is only called by the UI after the
  user checks a confirmation box. It requires an approval timestamp and a confirmation reference or
  message. A click alone is not evidence.
- `verify` requires text that contains the reference; it moves submitted to under review.
- `interview`, `offer`, `rejected`, `withdrawn` and `closed` follow the allowed graph.

No career tool exposes a submit action. A model cannot forge an approval through arguments.

## Career tools

Registered by `CareerCapability` through the existing dispatcher, only when a `CareerStore` exists:

- `career_profile`: read, validate, update, preferences, add entries, confirm imported facts and
  extract proposals. Erasing all career data is deliberately UI-only, not a model-callable action.
- `career_resume`: generate or tailor a resume from confirmed facts.
- `career_cover_letter`: generate a draft or save an edited letter after a truth check.
- `career_document`: list, preview and export PDF/DOCX/TXT.
- `job_search`: search locally saved listings, optionally rank.
- `job_extract`: structure a pasted page, deduplicate, flag safety issues and save.
- `job_match`: explain one match or rank saved jobs.
- `job_application`: prepare, check, analyse form fields, request approval, verify receipt and track.
- `career_interview`: prepare questions, review a practice answer and draft a follow-up.
- `career_dashboard`: deterministic counts, next actions and warnings.

Each tool has a schema, runtime validation, error mapping, an audit record through the service layer
and a risk/verb declaration. They are off-screen local tools; they never drive the browser directly.
Browser and phone tools still perform any external interaction.

## Specialist roles

The existing `AgentLoop` is the only reasoning loop. The career capability prompt acts as the Career
Manager: it decides the order, keeps shared ids and blockers, and hands external steps to the browser
or phone. The specialist roles are tool domains plus prompts:

- Candidate Profile and Resume/Cover Letter — profile and document tools.
- Job Research and Matching — job extraction, local search and matching.
- Application and Website Automation — form analysis, browser/phone execution, approval.
- Tracking and Interview Preparation — application tracking and interview tools.

Read-only child runs can use the career capability for research and matching; they cannot submit or
change application state. This keeps delegation real without pretending there are independent loops
with their own hidden state.

## Storage and privacy

`CareerStore` writes one JSON document atomically, keeps a corrupt copy, serialises updates and keeps
an audit log of action names and ids. `KeystoreCareerCodec` encrypts the document with an Android
Keystore key. Exports are separate files; the UI copies a user-requested export to `Downloads/Jobby`
through MediaStore. Nothing in core uploads a profile or document.

`Redactor` masks career results before run records are stored: contact fields, document text,
confirmation text and whole profile objects are replaced with a mask. Tool ids, statuses and counts
remain so a run can still be audited.

Job descriptions, website text and uploaded documents are untrusted data. The career prompt tells the
model to ignore instructions embedded in them. Sensitive form fields are never auto-filled; the form
mapper returns them to the user.

## UI

`CareerScreen` uses the existing SubPage, MikaCard, GroupCard, ListRow, MikaButton, SegmentedControl,
StateCard and MikaDialog components. Tabs cover Home, Profile, Jobs, Applications and Documents. The design goals are:

- A clear next action on every empty state.
- No false success: the submission dialog explicitly says the user submitted the application.
- Every destructive action asks for confirmation.
- Every match score is paired with an explanation and gaps.
- Career data is readable with large text, touch targets at least 48dp and no reliance on color alone.
- Export is explicit and lands in a normal Downloads folder.

## Failure handling

- Unknown job/application/document id returns a typed failure, not an empty success.
- Invalid profile values return field-specific errors; the store is not partially written.
- Duplicate jobs return the existing record and a warning.
- Expired listings are marked expired, not silently recommended.
- A blocked submission lists concrete blockers.
- A verification text without the reference is rejected.
- A missing Keystore key is a hard failure rather than a silent plaintext fallback.
