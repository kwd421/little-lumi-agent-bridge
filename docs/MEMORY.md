# Memory and compaction

## Little LUMI's own memory

The bridge leaves Little LUMI's memory files untouched. In the build inspected on 2026-09-04,
normal turns are appended to `speech/mem/<character>/chat_history.jsonl`. At 90 active messages,
Little LUMI asks the selected LLM to summarize the oldest 50 messages into an episode containing
roughly a date, summary, keywords, and newly learned user facts. Old turns move to an archive and
future retrieval uses recent episodes plus simple keyword matching.

This behavior belongs to Little LUMI and may change in later releases.

## Why compaction needs a guard

Without an extra rule, a model can turn its own earlier hallucination into a persistent user fact.
For example, an assistant statement about a game character could be summarized as though the user
had asserted it. Roleplay threats and jokes can also be flattened into false events.

When the bridge recognizes the structured compaction prompt, it:

- removes all `tools` and `tool_choice` fields;
- skips automatic web search and evidence recall;
- injects instructions that `user_facts` contain only stable facts directly stated by the user;
- tells the model not to store assistant claims, game data, web facts, jokes, roleplay, or guesses as user facts;
- preserves the original requested JSON response format.

The detection is intentionally conservative: several summary/keyword/user-fact markers, a JSON
signal, and a sufficiently long prompt must appear together.

## Optional evidence cache

This project can keep a separate cache of facts actually returned by web tools:

```properties
memory.evidence.enabled=true
memory.evidence.recall=true
```

Entries contain the retrieval time, tool, query, title, URL, and clipped content. Retrieval is a
small local keyword-overlap search, not embeddings or a vector database. Recalled evidence is
explicitly marked as possibly stale. Current questions still trigger a fresh search.

The cache is disabled by default because:

- web content changes;
- a search snippet is not a durable source of truth;
- local chat context may contain private interests;
- more stored context means more prompt tokens.

Delete `data/evidence.jsonl` while the bridge is stopped to clear it.
