---
name: professional-resume-writer
description: Use when generating, tailoring, formatting or reviewing a resume, ATS compatibility, document versions or PDF/DOCX export.
---

# Professional resume writer

## Inputs

Only confirmed facts from the career profile. If a fact is unconfirmed, exclude it or show it as a
proposal outside the document. A job description is used to choose emphasis and keywords that are
already true, never to invent experience.

## Structure

1. Header: name, headline, location, email, phone, links.
2. Summary: two or three lines tied to the target role and supported by the profile.
3. Skills: grouped, relevant first; only confirmed skills.
4. Experience: reverse chronological; employer, title, dates, location; bullets that state action,
   scope and result.
5. Projects: only when they add evidence for the target role.
6. Education, certifications, languages: as applicable.
7. Optional: work authorization only if the user provided it.

## Bullet quality

- Start with a concrete verb.
- Name the action, the system/scope and the result.
- Use a metric only if the profile contains it. Never invent a percentage, user count or revenue.
- Prefer 3-5 bullets for recent roles, fewer for older ones.
- Remove filler ("responsible for", "helped with") when a specific action is available.

## Tailoring

- Reorder skills and bullets to match the listing's named requirements.
- Mirror a keyword only when the candidate has that skill or experience.
- Do not change dates, titles or employers.
- Keep one master version and create a new version for each tailored document; never overwrite a prior
  version.
- Show the match gaps after generation so the user knows what is not covered.

## ATS and formatting

- Single column, standard section headings, no text in images, no tables that scramble reading order.
- Use a common font, consistent date format and real bullet characters or paragraphs.
- Export a text-based PDF and a DOCX. Verify the PDF has selectable text, not a screenshot.
- Keep file names plain and private; the user chooses where to share.

## Review checklist

- [ ] Every employer, title and date is confirmed.
- [ ] No invented metric, skill, degree or certification.
- [ ] Contact details match the profile.
- [ ] The summary is specific to the target role but still true.
- [ ] Spelling and tense are consistent.
- [ ] The document fits the requested length without shrinking text below readability.
- [ ] A new version id was created; the old one still exists.

## Failure handling

- Empty profile: explain what is missing instead of generating a generic resume.
- Export failure: keep the document and report the file error.
- Unsupported format: offer PDF/DOCX/TXT explicitly; do not silently change the format.
