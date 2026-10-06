# Standards review — relaunch delivery precheck

Baseline: `05e7f546f5885680e333ad8d3dea3645b25d26b7`. This is a
read-only prospective review of the five relaunch helper/authority files,
using the previously recorded repository standards and Fowler smell baseline.
No final gate, certification, receipt or completion result is claimed.

Reviewed SHA-256 identities:

- `audit-worktree-relaunch.py`: `a11a87c1921fdfbe7edab0910e112735692edad78bbab1bb568b978fb1345983`
- `write-receipt-relaunch.py`: `e6ccd9c00024b89ea9628543bafb5ed84a885dc7483efdc12729e11c10a07b85`
- `diagnose-attachments-jdk17-worker.py`: `12e5864fb7a19ec1d2fd5adb543761773c8f4a1436df9537648e94b90f385e73`
- `relaunch-authority.json`: `9873c5fc7ab7331c0bac8aa029963be8075fc559ff4b63a19509254b2869b12e`
- `validation/relaunch-worker-diagnostic-decision-r1.json`: `a258de89f6396c86a584d38c89323dabf8ccc4358c5930f336cf9fa1d380d475`

**Findings: 0 documented-standard violations; 0 applicable smell findings.**

The receipt adaptation retains the exact 92-scope/372-chain requirement,
current-index and staged-build verification, passing final validation gates,
selected-obligation satisfaction, concrete unrelated blockers and passing
baseline-relative final reviews. Relaunch references augment the existing
criterion mappings. The authority/worktree descriptions correctly distinguish
the original clean entry from the authorized dirty relaunch.

The diagnostic recipe changes only the writable diagnostic directory in the
original complete 24-test command. Its result remains explicitly diagnostic,
with no inferred cause, product fix, changed timeout or certification claim.
The retained transcript confirms `OK (24 tests)` and `Time: 234.221`.
Original helper identities and relaunch/diagnostic reference hashes matched;
the three Python sources parsed without execution. Historical failure evidence
remains retained, consistent with ADR-0040 and the sole contract.

Execution note: the worktree helper refuses existing audit destinations.
Create the planned final review/receipt/map filenames before its final snapshot,
or retain an earlier audit and select a fresh final destination. This keeps
reported untracked counts aligned with the delivered tree. Required execution
and final evidence/receipt review remain separate; this precheck endorses no
mandatory test, tool, fixture or certification omission.
