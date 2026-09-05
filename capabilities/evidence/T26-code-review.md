# T26 independent code review

Fixed point: `aac85ebae1dfe89813a5f16b7fc236cdfc232c79`.
Scope: `git diff aac85ebae1dfe89813a5f16b7fc236cdfc232c79` plus all new,
untracked T26 implementation, test, contract, inventory and evidence files.
At review time there were no commits after the fixed point; an empty three-dot
committed diff was not used to exclude working-tree changes.

The Standards and Spec reviews ran in separate read-only agents. Each reviewed
the subsequent failure-attribution correction independently. Repository and
JDK verification are separate gates recorded in the acceptance profile.

## Standards

**Pass: zero documented-standard violations.** The review covered AGENTS.md,
CONTRIBUTING.md, the domain and issue-tracker instructions, applicable ADRs,
inventory/evidence instructions and the code-review skill's Fowler smell
baseline. Backend isolation, public Workflow test seams, versioned Worker
values, inventories, provenance and independent evidence requirements are
preserved. Artifact hashes and primary/secondary changed-pixel counts match
the retained evidence.

Two optional design judgments remain:

- Possible Primitive Obsession: the one-element glyph cursor array shared by
  `PdfBoxParagraphOperations` and `PdfBoxTableLayout` could become an internal
  cursor object.
- Possible Duplicated Code: repeated compensated-sum arithmetic in
  `PdfBoxTableLayout` could become an internal accumulator while preserving
  explicit work charges.

Neither judgment is a required fix. The follow-up review also confirmed the
candidate-local line-limit reset, public dual-mode regressions, clarified
AUTO boundary and updated existing Worker token rejection test conform to
the documented standards.

## Spec

**Pass after correction: no remaining findings.** The initial review found
one P2 issue: an unrelated paragraph pagination flag could change an
unplaceable table's documented failure from `TABLE_CONSTRAINT_UNSATISFIED`
to `COMPOSITION_CONSTRAINT_UNSATISFIED`.

The correction attributes exhausted version-3 searches to the furthest flow
item reached. Discarded earlier candidates and their line-limit failures no
longer mask later failures; version-2 behavior is retained. Public regressions
reproduced the failure in both execution profiles before the fix and pass
afterward. The follow-up review found no missing requirements or scope creep.

AUTO zero-column rejection follows the explicit preimplementation algorithm.
Its limitation and supported surplus-width/explicit-column alternatives now
have a public regression and contract example. It is not counted as a defect.

Independent totals: Standards has zero hard findings and two optional design
judgments; Spec has one corrected finding and zero remaining findings.
