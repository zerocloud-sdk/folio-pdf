# T75 validation after Worker batch-test closure

The independent six-file Worker batch correction and full 126-case development
regression are retained in `../worker-batch-timeout`. This directory records
fresh complete validation of those reviewed source and contract identities.
The preceding inventory generate, validate and check commands all passed.

`source-and-contract-inputs.json` freezes the 1,933 source and 30 contract
inputs before the new complete host verification. The host Java version and
original inventory commands, logs, exits and hashes are retained separately.
The new complete host run passed: 1,504 tests, zero failures, zero errors and
four existing opt-in skips, with actual process exit 0 and all ten reactor
modules successful. `host-full-verify.tar.xz` retains the original command,
output, process receipt and 177 report files; its archive identity and receipt
record byte verification and unchanged source and contract inputs. The complete
four-JDK matrix also passed against those same inputs: each of JDK 8, 11, 17
and 21 reported 1,504 tests, zero failures, zero errors and four existing opt-in
skips, with all ten reactor modules successful. The retained command result
records actual outer process exit 0.

`jdk-matrix.tar.xz` preserves the complete original log, exact command, process
result, source/contract snapshot, receipt and 177 final JDK 21 report files.
All 87 XML class results exactly match the final JDK 21 section of the log.
The serial matrix overwrote earlier JDK XML reports; those earlier reports are
not claimed as separately retained. All 182 archive members were byte-verified.
The fresh [candidate](../final-candidate-r3/README.md) was subsequently staged
against these same source and contract inputs. Actual 48-tuple certification
remains pending. Earlier successful runs in
`../final-validation` retain their original pre-split input identities.

The independent [host evidence review](standards-host-report.md) found no hard
violation or actionable judgement finding. It checked all 183 archive members,
the complete log against all 87 XML class results, original process exit and
Java observations, unchanged input identities and prior review closure linkage.
Its original 24 files are retained in `standards-host-review.tar.xz`, with an
archive identity recording byte verification. This review covers the completed
host run; the new matrix and certification results have separate review gates.

The independent [matrix evidence review](standards-matrix-report.md) also found
no hard violation or actionable judgement finding. It checked every one of the
182 archive members, reconstructed all four complete result multisets, compared
the final JDK 21 XML reports with the log, and independently enumerated and
hashed the complete source and contract input sets. The actual command exit,
pinned image headers, helper identity, unchanged skip gates and prior review
identities all agree. Its original 25 files are preserved in the byte-verified
`standards-matrix-review.tar.xz`. Actual candidate certification remains a
separate gate.
