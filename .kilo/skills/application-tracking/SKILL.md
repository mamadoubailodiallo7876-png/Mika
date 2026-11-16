---
name: application-tracking
description: Use when implementing application status transitions, history, follow-up reminders, interview dates, search, filters or application statistics.
---

# Application tracking

## Status model

Use the application state machine in `ApplicationWorkflow`:

Discovered -> Shortlisted -> Preparing -> AwaitingApproval -> Submitted -> UnderReview -> Interview ->
Offer / Rejected, plus Withdrawn and Closed from the allowed states. Do not add a shortcut that skips
approval or evidence.

## Events

Every change records an event with timestamp, from/to status and a short note. Evidence text is
bounded and sensitive; do not put it in a run log or a public summary. A status change without an event
is a bug.

## Submission and verification

- Submitted requires user approval plus a reference or confirmation message.
- UnderReview requires verification text containing the reference.
- Interview requires a verified receipt and a future time.
- Offer/Rejected require the allowed predecessor; do not allow arbitrary edits.

## Follow-ups

- A submitted application gets a sensible default reminder; the user can change or clear it.
- Show upcoming follow-ups in the dashboard and application detail.
- Do not send anything automatically. A follow-up draft is a draft until the user sends it.
- A reminder for a closed/withdrawn/rejected application should be cleared or hidden.

## Statistics

Counts must be derived from records, not estimated:

- total applications, active, submitted, interviews, offers, rejected, awaiting approval.
- response rate = responses / submitted, labelled as a historical ratio, not a prediction.
- Do not count a draft or an unsubmitted application as submitted.

## Search and filters

- Filter by status, company, role and date; search notes cautiously (notes may contain personal data).
- Sorting defaults to most recently updated; a stable tie-break keeps tests deterministic.
- Empty states explain how to create the first application.

## Privacy and deletion

- Deleting an application removes its events and document links; it does not delete the documents
  unless the user asked.
- Erasing career data removes applications, documents and exports together.
- Export a human-readable history only on explicit request; keep it in app-private storage or a
  user-chosen Downloads location.

## Tests

Cover each allowed transition, each rejected transition, submission approval/evidence, verification
with/without the reference, follow-up clearing and stats after a deletion.
