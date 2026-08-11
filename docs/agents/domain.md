# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

## Before exploring, read these

- **`CONTEXT.md`** at the repo root — the canonical glossary for the whole program and all eight capability contexts.
- **`docs/adr/`** — read ADRs that touch the area you're about to work in.

If either location doesn't exist, proceed silently. Don't flag its absence or suggest creating it upfront. The `/domain-modeling` skill creates domain documentation lazily when terms or decisions are resolved.

## File structure

The repo uses one domain-document location:

```
/
├── CONTEXT.md
├── docs/
│   └── adr/
├── build-tools/
├── pdf-bom/
└── pdf-document/
```

The eight capability contexts defined by ADR-0014 remain distinct architectural concepts, but their ownership and vocabulary are grouped into sections of the single root `CONTEXT.md`.

## Use the glossary's vocabulary

When output names a domain concept—in an issue title, refactor proposal, hypothesis, or test name—use the term defined in `CONTEXT.md`. Don't drift to synonyms the glossary explicitly avoids.

If the required concept isn't in the glossary, reconsider whether the term belongs to the project or note the gap for `/domain-modeling`.

## Flag ADR conflicts

If output contradicts an existing ADR, surface it explicitly rather than silently overriding it:

> _Contradicts ADR-0007 (offline core with pluggable capability providers) — but worth reopening because…_
