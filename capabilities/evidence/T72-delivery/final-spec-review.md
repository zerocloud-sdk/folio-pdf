# T72 second/final clean-context Spec review

## Findings

No findings.

## Review identity and candidate lineage

This is the required clean-context, independent Spec-axis review of every tracked
change since fixed baseline `e5053749e35e517fe74f81bd3ebb39d8b41e28ce` and all
relevant untracked T72 files. I reviewed the complete [#72 objective and every
completion criterion](/home/ubuntu/.codex/attachments/6b536f30-ca4a-4539-a554-7fdf86993317/pasted-text-1.txt:46),
the repository instructions, [CONTRIBUTING.md](/home/ubuntu/IdeaProjects/open-pdf/CONTRIBUTING.md:1),
[CONTEXT.md](/home/ubuntu/IdeaProjects/open-pdf/CONTEXT.md:1), and ADRs 0004,
0005, 0011, 0013, 0017, 0020, 0023, 0025, 0029, and 0040. I inspected the
working tree, staged jars and PDFs, machine authorities, and transitive raw
evidence directly; implementer summaries were not the verdict source.

The previously requested candidate
`ff723b191a0ad222b7795898c533dafe0fecb751f3dbfaf1d9402e8a349aac7e`
was independently checked, then superseded when stale public-surface totals in
three candidate inputs were corrected. I do not accept that candidate or its
evidence as current. This verdict covers only candidate
`2f82ec56970be1a8ce70b4d9a7e2288505bed6e20c3123cb736ed5b0003ef648`
and contract
`0e8cc59037963c606a3f8a6fb823650f0e51a68936f0da0b65b08227cb94606c`.
I independently recomputed both identities. The current [stage
receipt](/home/ubuntu/IdeaProjects/open-pdf/target/foundation-0.1.0/build-inputs.json:1)
has SHA-256
`31811b6a861a81aead269e4b162eed1164231e6909d4ec20729c983001a90daf`
and exactly 1,067 source inputs, 23 required artifacts, 30 contract inputs, and
24 harness inputs. Every recorded hash and the expanded source-root membership
matched the current tree, with no missing or extra candidate input. Its candidate
snapshot equals the current evidence authority snapshot.

## Criterion-oriented audit receipt

- **Native and Facade behavior.** I traced all six public Native commands and
  their validation, preservation, and publication paths, plus the named
  Source/Target Facade starting at [PdfDocument](/home/ubuntu/IdeaProjects/open-pdf/pdf-migration-itext7/src/main/java/net/zerocloud/pdf/itext7/kernel/pdf/PdfDocument.java:90)
  and its declaration/ownership boundary in
  [FacadeDeclarations](/home/ubuntu/IdeaProjects/open-pdf/pdf-migration-itext7/src/main/java/net/zerocloud/pdf/itext7/kernel/pdf/FacadeDeclarations.java:27).
  The 53 public Native tests cover reopened insert/remove/move/copy/merge/split
  order, one-based inclusive ranges, post-removal move positions, original-order
  copy positions, explicit Source selection and order, collisions, complete
  split Target coverage, terminal `COMMAND_REJECTED`, safe non-mutating
  rejection, independent products, Source/sibling isolation, and ordered
  partial-publication receipts; representative coverage begins in
  [PageManipulationWorkflowTest](/home/ubuntu/IdeaProjects/open-pdf/pdf-document/src/test/java/net/zerocloud/pdf/consumer/PageManipulationWorkflowTest.java:609).
  The 10 public Facade cases verify the corresponding page-handle, selection,
  ownership, stream-lifetime, declaration atomicity, isolation, safe-failure,
  and `COMMITTED`/`FAILED`/`NOT_ATTEMPTED` behavior in
  [PageManipulationFacadeTest](/home/ubuntu/IdeaProjects/open-pdf/pdf-migration-itext7/src/test/java/net/zerocloud/pdf/itext7/consumer/PageManipulationFacadeTest.java:56).
  Public reopen checks retain inherited boxes, rotation, resources, ordered
  content and resource Form XObjects, safe page attachments, identifiers and
  destinations. Copy/import collision handling retains the legacy annotation
  key set while applying the required `/NM` rename and `/P` retarget. Native and
  Facade products are byte-identical per operation in all eight scopes: 64
  actual PDFs and 240 checked pages. The frozen behavioral contract is explicit
  in [docs/page-manipulation.md](/home/ubuntu/IdeaProjects/open-pdf/docs/page-manipulation.md:9).

- **Actual jars and mixed classpaths.** The staged Stable jar SHA-256 is
  `18d9b30e89c2a16c07f9c6cb7b16b6f1459fa6ad1536fb5b8bf5475e37ceef5c`;
  Preview is
  `453ec5c8a3041bb58b77a60e366ad1445ef81c91b6529358ffffcd18e7b1f099`.
  Each has 34 class files, all class-major 52, and its reflected signatures
  exactly match the declared 19-type/105-member surface. Preview contains the
  complete Stable surface and declares no additions. The permanent exact-artifact
  comparison is [JarContractIT](/home/ubuntu/IdeaProjects/open-pdf/pdf-migration-itext7/src/test/java/net/zerocloud/pdf/migration/itext7/contract/JarContractIT.java:48).
  I also invoked the staged classpath probe independently for `PdfMerger` and
  `PdfSplitter`: all four type × jar-order cases exited 1 with the required
  coexistence diagnostic. The permanent regression names both types and both
  orders in [ClasspathExclusivityIT](/home/ubuntu/IdeaProjects/open-pdf/pdf-migration-itext7/src/test/java/net/zerocloud/pdf/migration/itext7/contract/ClasspathExclusivityIT.java:38),
  and every pages certification ran the 65-test actual-jar/public contract set
  ([representative result](/home/ubuntu/IdeaProjects/open-pdf/capabilities/evidence/foundation/T72-pages/jdk8-in_process/contract-tests.txt:41)).
  This closes the earlier High finding.

- **T10 syntax, standards, semantic, and visual chains.** Every Native/Facade
  edited, merged, left-split, and right-split product in each scope has four
  separate passing records. The standards union is exactly the frozen 84 real
  rules: 68 pdfcpu and 16 Arlington, each with its illegal PDF, unchanged input
  identity, invocation, nonzero/finding outcome, and matching rule diagnostic;
  see representative [pdfcpu](/home/ubuntu/IdeaProjects/open-pdf/capabilities/evidence/foundation/T72-pages/jdk8-in_process/observations/native-edited/pdfcpu/standards.properties:1)
  and [Arlington](/home/ubuntu/IdeaProjects/open-pdf/capabilities/evidence/foundation/T72-pages/jdk8-in_process/observations/native-edited/arlington/standards.properties:1)
  records. qpdf is used only for syntax. PDFium is the authoritative renderer
  and ImageMagick receives PNGs only. All expected-image and renderer-agreement
  comparisons have absolute error 0 against fixed zero thresholds
  ([sample visual report](/home/ubuntu/IdeaProjects/open-pdf/capabilities/evidence/foundation/T72-pages/jdk8-in_process/observations/native-edited/page-1-visual.md:65)).
  The corpus, rule set, page order/resource expectations, profiles, and expected
  rasters were frozen before acquisition. Damaged syntax, same-length wrong
  semantic order, all 84 standards controls per product, and a one-pixel raster
  change all produce the required failed negative result
  ([negative observations](/home/ubuntu/IdeaProjects/open-pdf/capabilities/evidence/foundation/T72-pages/jdk8-in_process/observations/negative/result.properties:1)).
  Missing tools/rules/patches/profiles and identity mismatches fail closed.

- **Eight tuples and refreshed prerequisites.** The current [Foundation evidence
  authority](/home/ubuntu/IdeaProjects/open-pdf/capabilities/foundation-evidence.yaml:4400)
  contains exactly 24 certifications and 96 passing records: eight each for
  pages, refreshed transactions, and refreshed values; 12 `IN_PROCESS`, 12
  `HARDENED_WORKER`; six certifications per pinned JDK environment; and 24
  records for each evidence chain. Every execution record binds the current
  candidate, contract, actual Native mode, truthful `IN_PROCESS` Facade mode,
  exact environment, and acceptance profile. The Ubuntu 24.04 x86-64 Temurin
  JDK 8/11/17/21 observations retain actual image, vendor/build, Java byte hash,
  HarfBuzz, qpdf, pdfcpu, original and patched Arlington, PDFium, ImageMagick,
  and project-producer identities. Environment records for the three obligations
  are byte-identical per JDK. Each pages scope records `OK (65 tests)`, each
  transactions scope `OK (34 tests)`, and each values scope `OK (83 tests)`.
  All eight T09 raw-preservation observations retain identical source, Native
  rewrite, Native incremental, and Facade stream bytes/attributes, while the
  re-encoding controls differ and fail.

- **Evidence integrity, authorities, documentation, and scope.** The retained
  [authority/stage audit](/home/ubuntu/IdeaProjects/open-pdf/capabilities/evidence/T72-delivery/final-authority-and-stage-audit.txt:1)
  reports 37,710 verified reference occurrences and 19,028 unique references.
  My broader recursive walk revisited references through parent reports and
  verified 65,374 traversals with zero mismatch or missing file. Capability
  Matrix, Facade Surface, and the pages obligation contain the same ordered 17
  Stable mappings and no Preview additions; compare
  [capability-matrix.yaml](/home/ubuntu/IdeaProjects/open-pdf/capabilities/capability-matrix.yaml:319),
  [facade-surface.yaml](/home/ubuntu/IdeaProjects/open-pdf/capabilities/facade-surface.yaml:95),
  and [foundation-release.yaml](/home/ubuntu/IdeaProjects/open-pdf/capabilities/foundation-release.yaml:215).
  Public totals are synchronized at 19 types/105 members in
  [capabilities/README.md](/home/ubuntu/IdeaProjects/open-pdf/capabilities/README.md:327),
  [T03 certification](/home/ubuntu/IdeaProjects/open-pdf/docs/t03-certification.md:31),
  and [T09 certification](/home/ubuntu/IdeaProjects/open-pdf/docs/t09-certification.md:17).
  Generated public docs, the English contract, Chinese usage, T10 status, tool
  provenance, and the acceptance-only Arlington patch are aligned; the clean-room
  record is in [PROVENANCE.md](/home/ubuntu/IdeaProjects/open-pdf/PROVENANCE.md:2036).
  No product POM or runtime dependency changed, and all shipped bytecode is Java
  8. Forms/tagged-PDF expansion, later tickets, and Windows/macOS remain outside
  this certification as required.

- **Final repository gates.** The replacement root verify log has SHA-256
  `4d9f4c50d04a20ed7ec2a2c0e649b4f9d198a256d466df91bbdf8228a31958e0`
  and records all ten modules successful; independent parsing gives 78 test-class
  records, 1,355 tests, zero failures, zero errors, and four documented optional
  skips ([BUILD SUCCESS](/home/ubuntu/IdeaProjects/open-pdf/capabilities/evidence/T72-delivery/final-root-verify.txt:1039)).
  The fixed JDK matrix log has SHA-256
  `594d408bbf55d31636c1d2aff6ba6d5e810a91bf5be45cb65e83e5c17119ebb1`;
  each JDK 8/11/17/21 segment independently has 78/1,355/0/0/4 and succeeds
  ([matrix start and pinned image](/home/ubuntu/IdeaProjects/open-pdf/capabilities/evidence/T72-delivery/final-jdk-matrix.txt:1)).
  Inventory validation and check pass at 23 capabilities, 105 surfaces, and 20
  exclusions ([validation](/home/ubuntu/IdeaProjects/open-pdf/capabilities/evidence/T72-delivery/final-inventory-validate.txt:20),
  [drift check](/home/ubuntu/IdeaProjects/open-pdf/capabilities/evidence/T72-delivery/final-inventory-check.txt:20));
  two generations produced identical current hashes for all three public docs.
  Readiness identifies transactions #70, values #71, and pages #72 as
  `SATISFIED` and returns the expected overall `NOT READY` solely because future
  obligations remain ([readiness](/home/ubuntu/IdeaProjects/open-pdf/capabilities/evidence/T72-delivery/final-inventory-readiness.txt:20)).
  `git diff --check` passes. The current baseline delta remains an uncommitted,
  reviewable T72 implementation/certification/delivery tree.

## Practical limits and authorization

I did not rerun the multi-hour 24-certification acquisition or the full root/JDK
matrix. I inspected their complete retained command output, actual PDFs, rasters,
raw diagnostics, environment observations, test records, authority graph, and
byte hashes, and independently reran focused jar/classpath/PDF and recursive
integrity audits. Windows, macOS, and future Foundation obligations remain
outside the #72 certification boundary. This review contacted no external
service and made no product, test, authority, checklist, commit, or publication
change; its only repository write is this report.

This is a clean-context independent Spec verdict. It grants no completion,
commit, publication, or checklist authorization.
