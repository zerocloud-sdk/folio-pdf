# T75 complete validation after the inventory reader correction

The independently reviewed [five-file correction](../inventory-read-bound/README.md)
changes the r3 source identity. `source-and-contract-inputs.json` freezes the
1,933 source and 30 contract inputs for the new complete host verification and
four-JDK matrix. The serial command retains each run's actual result and copies
its original reports before another build can overwrite them.

The host verification actually exited 0, running from 2026-09-13 14:10:40.767506
UTC to 14:42:42.486856 UTC. All ten reactor modules succeeded. Its 87 class
results report 1,507 tests, zero failures, zero errors and four unchanged default
skips. All 115 Native extraction tests passed; the inventory module includes
the three new reader regressions. The four skips remain three unselected opt-in
Worker scale profiles and one offline T30 raster profile.

`host-full-verify.tar.xz` retains 184 original files, including 177 reports.
All 87 XML class results match the complete original host log and observed
Java 17 runtime. Every archive member was compared byte-for-byte with its
original; adjacent command, result, log, receipt and archive identities preserve
the observations. The source and contract input sets remain unchanged.

The independent [host Standards review](standards-host-review-report.md) closed
with zero documented-standard violations and zero open judgement findings.
`standards-host-review.tar.xz` preserves all 105 original review files, including
the original diagnostics and their separate reader corrections. The reviewer
independently compared the complete log, all 87 XML results, all 184 host archive
members, and the frozen 1,933 source and 30 contract inputs. This review covers
the completed host verification only.

The four-JDK matrix actually exited 0, running from 2026-09-13 14:42:45.357683
UTC to 17:00:34.619670 UTC. Each of JDK 8, 11, 17 and 21 reports 87 class
results, 1,507 tests, zero failures/errors, the same four default skips and
ten successful reactor modules. All 115 Native extraction cases and all 17
inventory cases passed in each run. The frozen source and contract inputs
remain unchanged.

`jdk-matrix.tar.xz` retains 184 original files, including 177 reports. The
matrix overwrites shared Maven report paths between runs: these retained
reports and their 87 XML class results belong only to the final JDK 21 run.
The complete original log preserves all four JDK results; earlier JDK XML
files are not claimed separately retained. Every archived member was compared
byte-for-byte with its original before candidate staging could overwrite
the live build outputs. The independent
[matrix Standards review](standards-matrix-review-report.md) closed with zero
documented-standard violations and zero open judgement findings. All 96
original review files are byte-preserved in `standards-matrix-review.tar.xz`,
including a reader-only formatting diagnostic and its separate correction.
The review independently checked every archived member, each complete JDK log,
the final JDK 21 XML results and the unchanged source/contract identities.

`completed-validation-stage-queue.tar.xz` retains 14 original command, script,
result, frozen-input and receipt files for the completed serial queue. Its
actual outer exit was 0 at 2026-09-13 17:01:19.995966 UTC. The adjacent receipt
and archive identity preserve its exact execution and retention boundaries.

Fresh [candidate staging](../final-candidate-r4/README.md) has also exited 0
and retains the same source/contract inputs. All 48 new certification tuples,
their independent reviews, final inventory checks and final delivery remain
required.
