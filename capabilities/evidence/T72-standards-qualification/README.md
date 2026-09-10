# T72 standards-checker qualification

This directory retains the real checker observations used to qualify the frozen
T10 standards rule assignments. Inputs are original project-authored valid and
illegal PDF 1.7 fixtures. Every invocation records the input hash before and
after execution, executable and pin identity, arguments, exit status, and raw
stdout/stderr.

The first `observations.json` contains 116 executions: 58 each through pdfcpu
0.15.0 strict/offline and the original Arlington TestGrammar 0.81. Those runs
accepted all positive Sources and the valid structure but exposed four required
rules that neither checker diagnosed: destination array length, destination page
kind, name-tree key ordering, and name-tree key uniqueness. These observations
remain as the failing qualification history; they are not product evidence.

`patched-r1/` contains 122 executions after adding the separately versioned,
acceptance-only Arlington T10 r1 checker and two additional high-byte/alternate-
encoding name-tree controls. Its six new diagnostics cover destination array
length/page kind and name-tree byte ordering/uniqueness. The final frozen union
contains 84 rules: 68 assigned to pdfcpu and 16 assigned to Arlington T10 r1.
`T10StandardsQualificationTest` requires every positive input to be accepted and
every assigned illegal fixture to emit its exact configured diagnostic.

The patched executable SHA-256 is
`35c15bc7d78f70c2a378938623d2c5e604d66019aefe9f485b71cac63a46bee5`.
The patch SHA-256 is
`ebdf4b1e23a1d6f1e211ba88eda9ef0f9777e373640a95f3ee81e5001d25506a`.
The source commit/model/source archive identities, build procedure, version
suffix, and Apache-2.0 provenance are fixed by
[`scripts/t10-arlington-pin.properties`](../../../scripts/t10-arlington-pin.properties)
and the [acceptance-only patch record](../../../build-tools/acceptance/arlington/README.md).

This is checker-role qualification, not candidate certification. Each Foundation
T10 product reruns both required profiles and retains all rule-specific illegal
controls; a missing rule, profile hash, tool identity, or diagnostic cannot
produce standards PASS.
