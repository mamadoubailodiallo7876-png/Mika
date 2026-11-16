---
name: job-matching-and-ranking
description: Use when implementing or explaining job match scores, weighted factors, caps, missing requirements, ranking or match preference weights.
---

# Job matching and ranking

## What a score means

A match score is an explainable estimate of how well the confirmed profile fits the stated listing. It
is not a probability of being hired and must never be presented as one. Hiring also depends on other
candidates, timing, interviews and factors the app cannot see.

## Factors

Use a small, weighted set:

- Skills: required and preferred coverage.
- Experience: relevant years and seniority, not total years alone.
- Education/qualifications: only when the listing requires them.
- Location/remote: candidate preference vs. stated arrangement.
- Employment/salary preference: type, salary stated vs. minimum.
- Eligibility: hard requirement that must be surfaced separately.

Normalize weights so they sum to a stable total. Expose each component score and a one-line reason.

## Caps and blockers

- A missing must-have skill caps the score and says which skill.
- A deal-breaker phrase caps the score; it does not silently hide the job.
- An eligibility failure is a blocker, not a low score. Show "not eligible as stated" and why.
- Unknown data lowers confidence, not the candidate's worth. Say "not stated" rather than guessing.

## Explanation

Every result includes:

- matched skills and missing skills,
- missing qualifications,
- strengths and weaknesses,
- two or three recommendations,
- the factor breakdown,
- any cap and its cause.

Do not produce a single number without the explanation. Do not use language like "you will get an
interview" or "80% chance".

## Ranking

- Rank by score, then by recency/deadline, with expired/closed listings excluded or clearly separated.
- Keep deterministic tie-breaking for tests.
- Do not rank a job higher because it pays more if the candidate's minimum is already met; use the
  preference factor, not a hidden boost.
- Recompute when the profile, preferences or listing changes.

## Evaluation

- Fixed profiles and listings with expected component scores.
- Missing must-have -> cap.
- Unknown salary -> no penalty beyond the stated preference factor.
- Expired listing -> excluded from recommendations.
- Explanation contains the exact missing skill, not a generic phrase.

## Failure handling

- Empty profile: return a clear "not enough confirmed data" result, not a random score.
- Malformed salary: treat as unknown; do not parse a number that could mislead.
- Listing closed: keep the record but exclude it from active ranking.
