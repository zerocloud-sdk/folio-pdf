# T75 candidate after complete Worker batch validation

The unsigned local candidate and acceptance harness were rebuilt successfully
after the independently reviewed Worker batch-test correction and fresh complete
host verification and four-JDK matrix. The actual stage command returned zero.
`build-inputs.json` binds 1,933 source inputs, 23 artifacts, 30 contract inputs
and 24 harness inputs. The current source and contract inputs exactly match
both complete validation runs in `../final-validation-r3`.

`staged-build.tar.xz` preserves the original build output, exact commands,
build inputs, actual command exit, source/contract snapshot, freeze receipt and
external command-retention wrapper. All nine archived files were compared
byte-for-byte with their originals; `original-archive-identities.json` records
their hashes. No compiled candidate or acceptance binaries are copied into this
development-evidence archive.

This stage supersedes the failed 124-case candidate preserved in
`../final-candidate-r2` and `../worker-batch-timeout`. Its fresh text contract
contains 126 cases: 115 Native, nine Stable Facade and two artifact contracts.
Actual certification is collected separately under
`capabilities/evidence/T75-foundation-20260913-r3` and is pending at this stage.
Staging and an unverified execution plan do not certify the candidate.
