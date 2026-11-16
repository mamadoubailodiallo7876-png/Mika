---
name: ai-agent-architecture
description: Use when changing Mika's agent loop, tool schemas, context assembly, memory, structured outputs, retries, stop rules or agent evaluation.
---

# AI agent architecture

## Principles

- One reasoning loop per task. Extend `AgentLoop`; do not create a competing executor with its own
  hidden state.
- Tools are typed contracts, not prompt strings. Each tool has a name, description, JSON schema,
  validation, a risk/verb declaration and a deterministic error shape.
- The model sees only what the task needs. Capability-scoped tools and prompt sections beat one giant
  always-on list.
- Ground every claim. A tool result is evidence; a model sentence is not. Distinguish proposed,
  executed and verified outcomes.
- Prefer deterministic domain code for rules. Use the model for interpretation, drafting and
  planning, not for arithmetic, state transitions or truth checks.

## Tool design

1. Name the action precisely; avoid overlapping tools that differ only by wording.
2. Keep schemas small. Use an `action` enum for a related family, with one clear purpose per action.
3. Validate ranges, enums, lengths and required fields at runtime, not only in JSON Schema.
4. Return structured errors with a stable code and a user-safe message.
5. Declare risk: read-only, local write, external action. External actions need approval.
6. Log action/id/outcome, never sensitive arguments.
7. Add a test for success, validation failure and the dangerous path.

## Context and memory

- Build context from explicit, bounded sources: system policy, capability sections, recent turns,
  selected memory, selected files.
- Do not paste an entire document or profile into every turn. Retrieve the smallest useful slice.
- Keep ids stable across turns. A resumed task should recover its plan and blockers, not restart.
- Memory writes are user-visible and reversible. Do not silently store contact details or documents.

## Retries and cancellation

- Retry only idempotent reads. A write or external action is not retried blindly.
- Use bounded exponential backoff with jitter for transient network errors.
- Check cancellation between steps and before a tool call.
- A timeout is not a failure that authorizes a second submission. Report it and let the user decide.

## Evaluation

- Keep golden tests for the prompt surface, tool names and risk sets; update them only with a reviewed
  diff.
- Add scenario tests for tool selection: does the agent choose the safe path when a page asks it to
  submit without approval?
- Measure token use and tool-call count for a representative task. A shorter prompt is not
  automatically better if it causes more retries.

## Anti-patterns

- A "submit" tool that the model can call without a UI approval.
- A tool that returns hardcoded success.
- A second agent framework beside the existing loop.
- Passing free-form HTML or a full resume through several agents without a schema.
- Treating a model's claim of an external action as verification.
