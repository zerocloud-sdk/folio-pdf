# T30 publication preparation — independent Spec review

**No unresolved spec gap or scope issue found.**

The supplemental delta adds three T30-specific `.gitattributes` rules following the existing T29 evidence-preservation convention. They disable text normalization and trailing-blank whitespace checks for T30 evidence paths, preserving the exact bytes referenced by recorded hashes. Attribute checks confirm that product source and ordinary contract documentation retain their prior settings.

Independently verified:

- All 1,517 file hashes in `T30-final-workspace-audit.json` still match.
- The 1,518-file prior delivery, including that audit, remains staged without omissions; `.gitattributes` is the sole additional staged path.
- All 1,519 staged blobs exactly match working-tree bytes.
- `git diff --cached --check` passes.

The metadata change introduces no product, test, contract, inventory, promotion-status, or decoder-coverage change. The completed experimental T30 Spec review therefore remains valid. Earlier reports and audits correctly retain their historical capture state; subsequent user authorization belongs in the later publication record.

**Counts: 0 unresolved spec gaps; 0 scope issues.** This bounded review performed no Maven rerun or external action.
