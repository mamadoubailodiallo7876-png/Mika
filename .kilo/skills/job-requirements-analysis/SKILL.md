---
name: job-requirements-analysis
description: Use when turning a job description into explicit requirements, must-have skills, experience, education, eligibility, location and salary constraints.
---

# Job requirements analysis

## Separate the text into facts

- **Must-have**: explicit "required", "must", minimum years, degree/licence, authorization.
- **Nice-to-have**: "preferred", "bonus", "familiarity with".
- **Responsibilities**: what the person will do; not automatically a requirement.
- **Eligibility**: work authorization, location, age/background/security checks, language.
- **Logistics**: employment type, remote/hybrid, travel, schedule, salary if stated.
- **Unknown**: anything the listing does not state. Keep it unknown.

Do not convert a responsibility into a required skill. Do not infer a salary, visa sponsorship or
remote policy from a company's reputation.

## Output

A structured requirement record:

| Field | Value | Evidence | Kind |
| --- | --- | --- | --- |
| skill | Kotlin | "Kotlin required" | must |
| experience | 3+ years | "3+ years" | must |
| education | degree | "CS degree or equivalent" | preferred |
| eligibility | work authorization | "must be authorized" | eligibility |
| location | Berlin hybrid | "2 days in Berlin" | logistics |

Keep the exact source phrase for anything the user may dispute.

## Match-ready normalization

- Map synonyms to a canonical skill (for example "Jetpack Compose" and "Compose").
- Preserve seniority and years; do not round up.
- Distinguish "experience with" from "expert".
- Record equivalence clauses ("or equivalent experience") because they change eligibility.

## Safety and fairness

- Do not infer protected characteristics or use them in matching.
- Surface age/identity/background requirements as eligibility items for the user, never as a hidden
  filter.
- If a listing is discriminatory or suspicious, flag it and do not help circumvent the requirement.

## Failure handling

- Ambiguous requirement: mark it "ambiguous" and ask the user or leave it out of a hard cap.
- Missing section: return what is present; do not fill gaps.
- Conflicting statements: show both and prefer the more specific one, marked as a conflict.
- Very long listing: extract the requirements section first; do not summarize away a must-have.
