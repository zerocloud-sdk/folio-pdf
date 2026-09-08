# Foundation release Acceptance Profiles

These repository-only profiles govern the evidence contract declared in
`foundation-release.yaml`. They do not certify product behavior. The source
requirements catalogue retains the exact #1/#33 requirements and the approved
#69 platform amendment; the assigned #69–#97 slices must supply actual evidence.

Every behavioral profile runs on each declared environment in both IN_PROCESS
and HARDENED_WORKER unless it specifically owns only one execution profile.
Each run retains independently produced syntax, standards, semantic and visual
records, the original reports, known-invalid controls, the exact PDF corpus and
production artifact identities, and the full configuration. Syntax uses qpdf;
standards evidence requires a separately qualified external checker with explicit
rule coverage; semantic observations use public Workflow/Facade results; visual
evidence uses pinned PDFium and ImageMagick profiles. Uncovered required rules,
missing tools or observations, renderer disagreement, and missing required human
review remain INDETERMINATE and cannot satisfy a mandatory chain.

## Independent subcapabilities

- `T32-password-baseline` covers PDF 1.0–2.0 input, explicit PDF 1.7/2.0 output,
  all-content AES-256 defaults, supported legacy authentication/output under
  explicit Legacy Security Mode, missing/incorrect/user/owner credentials,
  permissions, algorithm/revision dictionaries, protected multi-Source behavior,
  tampering, publication and matching reader/writer Facade behavior (#78).
- `T32-password-clear-metadata` adds the complete metadata-clear input/output
  scope, including AES-256, with independently observed clear metadata and
  encrypted protected content, secure defaults and unchanged authority (#79).
- `T32-password-embedded-files-only` adds embedded-file crypt filters and
  authentication events, ordinary-content accessibility, protected attachment
  creation/read/extraction, mixed Sources and tampering controls (#80).
- `T32-tables-base` covers FIXED/AUTO single-area tables, point/percentage/AUTO
  widths, row/column spans, minima, padding, borders and cell-content order;
  retain the T26 three-page independent corpus and malformed-grid controls (#91).
- `T32-tables-pagination` covers multi-area FIXED/AUTO layout, split rows and
  continued spans, repeated/omitted headers/footers, keeps, overflow and relayout;
  incremental admission, flush and completion must release rows/resources with
  bounded cumulative work and memory. Retain all nineteen T27 raster pages and
  source/emitted-page identity and partial-failure observations (#92).

These are required declarations, not empty API stubs or compatible Capability
Matrix entries. A slice adds its `parent-capability` entry when it can supply the
implementation and evidence. It inherits every external Dependency Gate of the
parent; the aggregate itself is not a prerequisite for its child's independent
certification. The original security/table capability remains required, with all
members compatible before Foundation readiness.

## Project and release controls

The `F69-*` profiles below use separate repository evidence chains in addition
to the four PDF chains on capabilities that affect PDF outcomes. Reports must
identify the candidate, scope, producer version/hash, inputs, results and
configuration; historical CI, implementation evidence and issue closure do not
certify the final candidate.

- `F69-environments`: observe the immutable Ubuntu 24.04 images and actual JDK
  vendor/build, java executable, OS release, host kernel, native installation,
  helper/loaded library and every tool identity. Exercise unknown and mismatched
  environment controls. Windows and macOS remain uncertified, outside this gate.
- `F69-java-artifacts`: run the same artifact set on JDK 8/11/17/21, enforce Java 8
  APIs/class files, public API and Automatic-Module-Name contracts, first-party
  Release Train/BOM consistency, and absence of repository tools/test dependencies
  or unsupported placeholder products from runtime/published artifacts.
- `F69-facade-artifacts`: inspect compiled public signatures against the complete
  required mapping sets; invoke matching behavior; prove Stable contains exactly
  Foundation-compatible mappings, Preview is its declared superset, and both jar
  orders reject a mixed classpath. Compilation alone is insufficient.
- `F69-acceptance`: qualify independent chain tools and their invalid controls,
  pin corpora/configuration/fonts/thresholds, and verify missing chains or stale
  identities fail closed. No iText output is a correctness golden.
- `F69-docs`: regenerate all inventories and verify English contracts, Javadoc,
  Chinese usage and migration examples agree with actual artifacts and evidence;
  retain explicit limitations, non-affiliation notices and 0.x migration notes.
- `F69-provenance`: audit all included source, resources, dependencies and fixtures
  for Apache-2.0/DCO provenance and notices; preserve contributor copyright and
  Compatibility Curator isolation. Never copy iText material or reverse engineer
  closed products. Unavailable curator-dependent evidence is not a pass.
- `F69-supply-chain`: audit pinned dependencies/plugins/Actions, CycloneDX SBOM,
  licenses and vulnerability reports, private disclosure channel and unresolved
  high-severity gates; only public, scoped, unexpired Lead Maintainer exceptions
  satisfy ADR-0032. Scanner/feed failures fail closed.
- `F69-reproducibility`: two clean complete Release Train builds at `0.1.0`,
  matching deterministic entries, separately validated test signatures/checksums
  and retained build inputs/logs. Use the existing non-publishing release tool;
  a rehearsal identity cannot satisfy the approved production identity gate.
- `F69-candidate`: locally validate every Central-layout entry, inherited POM,
  binary, sources, Javadoc, checksum and detached signature under the approved
  fingerprint `C5149FD6B5EF7C2126F1FD0FCC1A12E348E171D8`; retain a candidate receipt
  linking the exact source/artifact identities and certification scope (#97).
- `F69-publication-controls`: verify protected Environment approval, minimum
  permissions, concurrency, pinned Actions, owner-only external secret storage,
  no replacement key, separated upload/validation/publication and disabled
  automatic publication. Local Central bundle validation and actual Central
  service validation are distinct. Upload/staging requires its own authorization;
  immutable publication remains a separate human decision.
- `F69-integration`: run complete public Workflow/Facade, hostile-input, Worker,
  opt-in 5,000-page/exact-1-GiB/concurrency scale, independent acceptance,
  inventory, full Maven/JDK and Standards/Spec review gates on final identities.
  All aggregates, limitations and dependencies must reconcile with no omitted
  behavior. Ordinary CI scale skips cannot certify scale.

`readiness` checks the integrity and scope of these retained observations; it
does not replace the qualified acceptance, artifact or release validators that
produce them, run native tools, grant publication approval, or access secrets.
