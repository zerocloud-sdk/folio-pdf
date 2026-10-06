# Spec review r2 — final text recovery restart

Baseline: `05e7f546f5885680e333ad8d3dea3645b25d26b7`. Prospective read-only assessment against the sole issue-81 contract. No tests, bound-source edits or final-certification claim.

No remaining execution blocker or Spec finding:

- Reconstructing the prior recipe in memory reproduced its recorded SHA-256 `660ab3d08ba86fe665502826992ea92ef4f411a9388662129a2c8ee73cd2d4c0`. The only change is journal numbering: the next attempt derives from existing text events rather than the literal 2. Existing events are attempts 1 and 2, so the fresh `text-r4` directory will be journaled as attempt 3; directory suffixes do not define attempt numbers.
- For “preserve historical evidence,” the prior bytes are now retained in `resume-text-r1.py` under that exact hash. `text-recipe-history.json` maps the historical original path/hash to the archived file; the failed journal event remains untouched. The updated recipe, journal and versioned history should all remain in the delivery evidence.
- All previously reviewed restart safeguards are unchanged: retain only the complete original JDK8 IN_PROCESS scope, seal original files, bind current staged/configuration/environment identities, complete closing observations, execute seven full fresh tuples with unchanged timeouts, and preflight the exact 48-scope union preserving all 40 predecessors before unchanged publication. Audit PASS still follows successful publication.
- The earlier full 126-test diagnostic pass and three isolated method passes are diagnostic evidence only. The latter report 3,554/3,776/3,745 ms with one run, zero failures and zero ignored tests; the diagnostic runner invokes the existing method through JUnit Request.method, preserving its declared 10-second timeout. Isolation does not establish complete-suite success, cumulative behavior, or the timeout's cause. Neither failed full-suite attempt may supply certification.

The complete unchanged public suite, recorder and four-chain collection must still pass for every remaining tuple. Final delivery should link both failures, diagnostic limits, recipe history and successful recovery; later predecessors and all four limits tuples remain required.
