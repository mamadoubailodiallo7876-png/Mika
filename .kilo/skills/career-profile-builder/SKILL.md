---
name: career-profile-builder
description: Use when implementing or reviewing the candidate profile, conversational profile creation, CV import, completeness, validation, merge or privacy controls.
---

# Career profile builder

## Goal

One structured, truthful record that every document, match and application reads from. The profile is
not a chat transcript and not a resume blob.

## Workflow

1. Start with identity and contact details: full name, email, phone, location, links.
2. Add target roles, locations, remote preference, employment types and salary preference.
3. Add experience, education, skills, certifications, projects, languages and qualifications one at a
   time. Each entry carries an id, source and confirmed flag.
4. Show completeness and the missing items that actually matter for applications.
5. Import an existing CV as proposals, never as confirmed facts.
6. Ask the user to review each proposal; merge without overwriting a confirmed value.
7. Offer export and a complete erase path.

## Truth rules

- Never infer an employer, title, date, degree or metric.
- A date range must be valid; "current" means no end date.
- A skill can be listed only if the user claims it; do not infer skills from a job title alone.
- A certification needs a name; issuer/year are optional but must not be invented.
- If the user cannot remember a detail, leave it blank and show it as missing.

## Import

- Accept text extracted from PDF/DOCX or pasted by the user.
- Detect sections: summary, experience, education, skills, projects, certifications, languages.
- Keep evidence (the source line) and a confidence for each proposal.
- Clean empty parentheses and common PDF artifacts, but do not rewrite facts.
- A scanned PDF with no text layer should return "needs OCR/text extraction", not a blank success.
- Merge by section and normalized key; never overwrite a confirmed value with a proposal.

## Completeness

Score only what the product needs: contact, at least one role or education entry, skills, target
roles and location/remote preference. Missing optional items are suggestions, not errors. Do not
punish a career starter for having no employment history.

## UI

- Show a compact header with name, headline, contact and completion.
- Separate confirmed facts from proposals with a clear banner and "Confirm all".
- Provide edit, add-entry and delete-entry actions.
- Put privacy and erase at the bottom, not buried in a menu.
- Use the existing SubPage, GroupCard, ListRow and MikaDialog components.

## Failure handling

- Invalid email/date: field-specific error; keep the rest of the form.
- Unknown entry id: typed not-found error.
- Store write failure: do not report success; keep the previous data.
- Duplicate skill: report it, do not add a second copy.
