# Standards review — retained relaunch outcome

Baseline: `05e7f546f5885680e333ad8d3dea3645b25d26b7`. Read-only
prospective review of `record-relaunch-attachments.py` and the receipt-helper
changes since the prior delivery precheck. Repository standards and the
Fowler smell baseline remain those recorded in the source review.

Reviewed SHA-256 identities:

- `record-relaunch-attachments.py`: `b9b1f3f56d7a5baaf327ca22988581d190b392dd6aada324df673339d209c397`
- `write-receipt-relaunch.py`: `17ae367c1ba19b11d1ccb3271bff9f9b81c6b56ec5a918f7d9adc6a739be2fe8`

**Remaining findings: 0 documented-standard violations; 0 applicable smell
findings. Two integrity/truthfulness concerns were resolved during review.**

The receipt initially omitted rechecking the newly selected attempt logs.
It now verifies the successful result's stored log reference and each failed
result, transcript and log reference. It also requires each failed result's
stored log to equal the outcome's reference. Altered or missing logs therefore
cannot silently acquire new receipt hashes while preserving earlier statuses,
consistent with ADR-0040's exact observed identity discipline and the
Foundation evidence contracts.

The unsuccessful outcome branch initially inferred nonpublication from a
nonzero wrapper exit. It now records only the supported observation:
`unsuccessful; not accepted as a complete obligation`. A failure after
publication is no longer misdescribed by that generic branch.

The outcome derives attempts from actual retained validation results, checks
their command scope and stored log hashes, retains unsuccessful transcripts,
and requires exactly one latest successful attempt with a complete eight-scope
resume audit containing seven retained and one fresh scope. It refuses to
overwrite an existing outcome. Receipt links select that accepted attempt
without replacing the final 92-scope/372-chain, staged-build, validation,
readiness and baseline-relative review gates. Diagnostic passes remain
explicitly separate from certification.

Both sources parsed without execution. At review time the latest recovery
was running and no outcome file existed; no execution, final certification,
gate or completion result is claimed. The final evidence/receipt review
remains separate, with no mandatory omission endorsed.
