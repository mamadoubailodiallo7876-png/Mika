---
name: browser-automation
description: Use when reading web pages, identifying forms, filling fields, uploading files, navigating a site or verifying a web action through Mika's browser capability.
---

# Browser automation

## Boundary

Use the built-in browser port and its typed tools. Do not drive a separate WebView, bypass sign-in
walls, defeat CAPTCHAs, spoof a user agent to evade controls, or ignore a site's terms. If a page
requires MFA, identity verification or a challenge, hand control to the user.

## Read before acting

1. Navigate and read the page. Record the final URL and title; redirects matter.
2. Identify the site adapter (`WebsiteAdapters.forUrl`) and its terms note.
3. Locate the actual application form. A listing page is not the form.
4. Read labels, not placeholders alone. Labels are what a screen reader and a human see.
5. Note required fields, upload controls, consent checkboxes and eligibility questions.

## Form map

Produce a table of canonical field, page label, value source and confidence. Value sources:

- confirmed profile (name, contact, links, work authorization),
- generated document (resume/cover letter),
- user answer (salary, start date, custom question),
- never auto-filled (password, government id, banking, demographic/health unless the user explicitly
  chooses).

Sensitive fields are always handed to the user. Do not infer a protected characteristic.

## Filling

- Fill one field, then read it back if the site masks or reformats it.
- Upload only a document the user generated or approved, from the task workspace.
- Do not check "I agree" or "I certify" on the user's behalf unless the user explicitly asked and the
  statement is factual.
- Before the final submission, stop. Show the user a summary of what was filled and what remains.
- Only the user submits. If the user takes over, do not keep clicking in the background.

## Verification

- A click is not a confirmation. Look for a confirmation page, reference number or email text.
- Record the URL, timestamp, reference and a short evidence excerpt.
- If the page times out or changes, report "unknown", not "submitted".
- Do not resubmit after a timeout; check the application history first.

## Failure handling

| Symptom | Action |
| --- | --- |
| login wall | Ask the user to sign in; do not automate credentials. |
| CAPTCHA/MFA | Hand over; do not solve or bypass. |
| form validation error | Read the message and map it to the field; ask for missing data. |
| upload rejected | Check type/size; ask the user for a valid file. |
| duplicate application | Stop and check the tracking screen. |
| page changed mid-fill | Re-read; do not type into stale coordinates. |

## Privacy

Do not place document text, contact details or confirmation messages in a run summary. Log field names
and outcomes, not values.
