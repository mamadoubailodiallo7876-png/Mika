---
name: recruitment-website-adapters
description: Use when adding a recruitment site adapter, host recognition, capabilities, form-field hints, terms notes or a custom adapter for an ATS.
---

# Recruitment website adapters

## Adapter as data

An adapter describes a site; it is not a branch in the agent loop. Use the `WebsiteAdapter` shape:
id, display name, host patterns, capabilities, notes and a terms note. Custom adapters should be
stored with the career data only when the user adds one; built-ins are code-reviewed.

## Capabilities

Declare only what the site actually supports:

- JobSearch: public listing pages can be read.
- OfficialApi: a documented, permitted API exists.
- AccountRegistration: an account may be required; creation is a user-approved step.
- ApplicationForm: an application form exists.
- ResumeUpload / CoverLetter: the form accepts the file.
- StatusTracking: a status page or portal exists.

A capability is not permission to bypass a sign-in wall. It tells the agent what to look for.

## Recognition

- Match the registrable host, not a substring of the URL. `evil-greenhouse.io` is not Greenhouse.
- Prefer the final URL after redirects; aggregators often redirect to the ATS.
- If no adapter matches, use the generic adapter and inspect labels.
- Keep host patterns lowercase and specific.

## Terms and safety

- Write a concrete terms note: what is permitted, what requires the user, what must never be automated.
- Do not add an adapter whose only purpose is to evade a control.
- LinkedIn/Indeed/Glassdoor: public pages only; respect access restrictions and rate limits.
- Workday and similar ATS: account creation is user-approved; MFA is always a handover.

## Form hints

- Map labels by accessible name, then name/id, then nearby text. Do not rely on placeholder alone.
- Record sensitive-field patterns and hand them to the user.
- A multi-page form needs a step list and a re-read between pages.
- Upload controls are not normal text fields; use the file-upload path with a workspace document.

## Adding an adapter

1. Verify the host and a real public posting.
2. Check the site's terms and API documentation.
3. Add host patterns and capabilities.
4. Add a terms note and a failure note.
5. Add a recognition test for the host and a lookalike host.
6. Add a form mapping test only if the site has a stable, public form structure.

## Failure handling

- Unknown host: generic adapter, no guessing about the ATS.
- Redirect to a login: hand over; do not attempt credentials.
- Layout change: re-read labels; do not reuse stale field ids.
- Adapter says API but the endpoint requires auth: treat as unavailable, not a silent failure.
