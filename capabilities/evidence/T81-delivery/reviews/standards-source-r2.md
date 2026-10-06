# Standards source review r2 — issue #81

Comparison baseline and HEAD: `05e7f546f5885680e333ad8d3dea3645b25d26b7`.
Reviewed the baseline-relative tracked diff and all new T20 source, inventory,
fixtures, pins, tests and documentation. This supersedes r1 for source review;
pending exact-candidate certifications and final delivery evidence are unassessed.

**Hard violations: none found. Optional smells: none warrant a change.**

- `T20ResourceContracts.java:212`, `:227`, `:363`, `:436`, `:473` and `:514`
  observe Command/donor/product accounting, distinct workflows sharing one
  Environment, independently controlled live-file arithmetic and actual
  cancellation receipts through public seams. `:147` checks both owned and
  target-adjacent cleanup. These preserve the ownership and publication rules
  in ADR-0013/0025 and `CONTRIBUTING.md:42–43` without product changes.
- `scripts/t20_foundation_reports.py:87` binds the original complete candidate,
  environment, command, locale and input closure; `:116` requires the original
  133-test command/success transcript; `:125` reruns the suite and recorder
  between actual environment observations. `:158` compares retained findings
  with live results. `scripts/t03-foundation.py:1180` keeps the reused value
  tests explicitly IN_PROCESS through their established T09 selector. Five
  chains remain separate and producer-bound, satisfying ADR-0023/0040's
  architecture; predecessor defaults and invalidation remain intact.
- New Java stays within Java 8 APIs; acceptance tools remain outside production
  runtime (ADR-0006/0009). The project-owned syntax generator and updated
  `PROVENANCE.md:3035` retain the origin, license and exposure declarations
  required by `CONTRIBUTING.md` and ADR-0002. All 56 non-tool pins matched.
- `docs/t20-certification.md:72` and `:79` preserve actual Ubuntu/JDK tuples,
  Native-only policy controls and the cooperative guarantee (ADR-0016/0040).
  The 75-case inventory, guide and provenance agree.

Final gates: `CONTRIBUTING.md:46` still requires full verification. Its
`verify-jdk-matrix.sh` requirement at `:47–48` is conditional on shipped-code
or build-compatibility changes. This diff changes acceptance-only Java and
scripts; inspected product modules, POM/build machinery and CI are unchanged,
so that conditional gate is not triggered. The contract's four actual JDK
certifications and all mandatory cases without skips remain independently
required. This source review claims no pending gate result.

Read-only review apart from this report. Findings: **0 hard violations,
0 actionable optional smells**.
