---
name: job-application-assistant
description: Use when building or reviewing application preparation, form-field mapping, approval checkpoints, submission recording, verification or account-creation assistance.
---

# Job application assistant

## Non-negotiables

- The user submits the application and creates any account. The assistant prepares and reviews.
- Never bypass CAPTCHA, MFA, identity verification or site access controls.
- Never invent answers, eligibility, salary history or work authorization.
- A click is not a submission. A confirmation reference or message is required to record it.
- No tool exposed to the model may mark an application as submitted.

## Workflow

1. **Prepare**: confirm the job and profile, generate a tailored resume and optional cover letter, link
   both to the application, move it to preparing.
2. **Check**: list blockers (name/email, resume attached, unconfirmed facts, eligibility, missing
   salary expectation). Do not hide a blocker behind a warning color.
3. **Open the form**: use the browser or phone tools, identify the site adapter, read the real form.
4. **Map fields**: produce a field map with value source and confidence. Sensitive fields go to the
   user. Ask for missing answers instead of guessing.
5. **Fill**: type only verified values, read back masked/reformatted fields, upload approved documents.
6. **Review**: show the user the filled summary, remaining fields, attachments and any unanswered
   question.
7. **Approve**: move the application to awaiting approval. This is the assistant's last state change.
8. **User submits** in the browser. If they hand back control, do not keep clicking.
9. **Record**: the user enters the reference and evidence. Only then call the user-facing record
   operation.
10. **Verify**: require text that contains the reference; move to under review.

## State machine

Use `ApplicationWorkflow`; do not invent statuses. Illegal transitions return a typed error. The
approval must have a timestamp and be tied to this application. Evidence must be non-blank and the
reference must be extractable.

## Form safety

- Password, government id, banking, health and demographic fields: always user input.
- Consent/certification checkboxes: only with explicit instruction and only for factual statements.
- Upload only a document the user generated/approved in this task.
- Do not solve "prove you are human" challenges.

## Failure handling

| Situation | Response |
| --- | --- |
| login/MFA | Hand over; wait for the user. |
| validation error | Map to field, ask for the missing fact. |
| duplicate application | Stop; check tracking first. |
| timeout after submit | Mark unknown; do not resubmit. |
| no reference | Keep status awaiting approval; ask the user for evidence. |
| verification text lacks reference | Reject with a specific message. |

## Tests

Cover: approval required, evidence required, reference extraction, illegal transition, sensitive field
blocked, and a model tool cannot reach submitted.
