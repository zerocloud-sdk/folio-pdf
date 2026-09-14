# T75 final source validation

The later Worker batch-test split changes five source inputs and one profile
contract input. The successful runs below retain their original tested
identities; complete validation must be refreshed for the changed candidate
before delivery.

The second complete host `./mvnw -B -ntp verify` passed in 30:56 minutes with
the explicitly installed HarfBuzz 10.2.0 helper. Its original log, the selected
Surefire/Failsafe reports, observed host Java version and source/contract input
identities are retained. The receipt records the actual totals and four opt-in
skips. Unselected pre-existing report files are listed and excluded.

The preceding inventory generate, validate and check commands all passed.
The earlier failed complete run remains in `../observer-budget`; it is not
replaced or relabeled by this successful run.

The complete `./scripts/verify-jdk-matrix.sh` also passed on the four pinned
JDK 8, 11, 17 and 21 images. Each run recorded 1,502 tests, zero failures,
zero errors and the same four opt-in skips; all ten reactor modules succeeded.
`jdk-matrix-receipt.json` binds the original full log and confirms all 1,933
source and 30 contract inputs remain identical to the host verification inputs.
The archive retains 177 original reports from the final JDK 21 run; their 87
XML suite results exactly match that run's full log. Earlier JDK XML outputs
were overwritten by the serial builds and are not claimed as retained.
`matrix-skip-wording-clarification.json` clarifies an ambiguous phrase in the
original receipt: the three scale-test enabling settings were not selected;
those tests and the T30 offline raster test were skipped. Original records
remain unchanged.

The original matrix tool handle expired across a goal continuation while its
unchanged process kept running. The four complete reactor logs report success;
the `set -e` script advanced from JDK 8 to JDK 11, and read-only `podman wait`
observations retained actual zero container exits for JDK 11, 17 and 21 before
the script terminated. The outer tool exit code is explicitly unavailable in
the receipt. The matrix was not restarted or instrumented.

Final candidate staging and actual environment certification remain separate
gates. No global Foundation readiness is claimed.

The independent [Standards validation review](standards-validation-report.md)
confirmed the complete host and four-JDK results, original-byte retention and
unchanged source/contract inputs, with no remaining hard breach or actionable
smell. Its original report and source identities are archived separately.
