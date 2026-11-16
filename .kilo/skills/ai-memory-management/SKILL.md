---
name: ai-memory-management
description: Use when designing or reviewing structured memory, long-term context, retrieval, retention, user-visible memory edits or deletion.
---

# AI memory management

## Model

Separate three things:

1. **Conversation history** — what was said in this thread; bounded by the context window.
2. **Memory** — durable, user-relevant facts the assistant may reuse; user-visible and editable.
3. **Domain records** — structured data owned by a feature (for example the career profile). These are
   not free-form memory and should not be copied into it.

Mika's `MemoryRuntime` owns memory and the general profile. Career data lives in `CareerStore` with its
own schema and encryption; the agent should read it through career tools, not by recalling a memory
blob.

## What to store

- Stable preferences the user states explicitly.
- Reusable facts that improve future help and are safe to retain.
- A source and timestamp for each item.
- A scope (global vs. conversation) when the store supports it.

Do not store secrets, API keys, passwords, full documents, contact details copied from a CV, or
inferred sensitive traits. Do not store a model's guess as a fact.

## Retrieval

- Retrieve by relevance, recency and scope; cap the number and length of items.
- Prefer exact, structured matches over dumping all memory into the prompt.
- Show the user when a memory influenced an answer if the product supports it.
- If memory conflicts with a newer explicit instruction, the newer instruction wins; mark the old item
  as stale rather than silently keeping both.

## Writes

- A memory write is a user-visible action; provide an edit and delete path.
- Deduplicate before insert. Merge corrections instead of creating contradictory entries.
- Keep the original wording when it matters; do not "improve" a user's preference into something they
  did not say.
- If the user asks to forget something, delete it and any derived index entry. Verify the delete.

## Retention and privacy

- Define a retention policy; do not keep everything forever by default.
- Encrypt at rest. Never log memory contents in run records.
- Make export and erase complete, including derived summaries and cached embeddings.
- On schema change, migrate with a version and keep a backup; never silently drop unknown fields.

## Evaluation

- Test recall for a relevant fact and non-recall for an unrelated one.
- Test correction, duplicate insert and deletion.
- Test that a secret or document body cannot be stored by a tool call.
- Test that a cleared memory is absent after a restart.
