# T75 final candidate, second staging

The unsigned local candidate and acceptance harness were rebuilt successfully
after the final appearance and observer-budget review closures. The actual
stage command returned zero. `build-inputs.json` binds 1,933 source inputs,
23 artifacts, 30 contract inputs and the acceptance harness. The source and
contract inputs match the completed host verification and four-JDK matrix.

`staged-build.tar.xz` retains the original build log, exact commands, build
inputs, actual command exit record and the external command-retention wrapper.
Every archived member was compared byte-for-byte with its original source;
`original-archive-identities.json` records those identities.

This stage supersedes the first candidate retained in `../final-candidate`.
The original text observations were collected under
`capabilities/evidence/T75-foundation-20260913-r2` and are retained in the
byte-verified [Worker timeout history](../worker-batch-timeout/README.md).
Staging itself is not certification.

The text run completed one JDK 8 / IN_PROCESS four-chain tuple, then failed its
JDK 8 / HARDENED_WORKER contract suite: 124 tests, one ten-second method timeout.
No eight-tuple certification index was published. The later Worker batch-test
split changes the test and contract identities and requires a fresh stage.
This candidate and its observations remain preserved development history.
