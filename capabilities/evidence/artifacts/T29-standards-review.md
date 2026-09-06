# Independent Standards review: #30 / T29

This is a clean-context read-only review by /root/t29_final_standards, not an implementer self-review. The fixed baseline resolves to dd4cda28c24a304258e75f0854d86852275f5320 and equals HEAD. The commit list BASE..HEAD is empty. Per the user's override, review used `git diff BASE --` and relevant untracked files, not BASE...HEAD.

Current Standards result after the final independent review below: no remaining mandatory source or archival finding; one nonblocking judgment call remains. The initial three source findings and subsequent font-snapshot/declaration-accounting corrections are closed after independent source and public-regression review. Current-source Linux six-chain acceptance, the 58-case focused regression, complete root verification, all four JDK gates (8/11/17/21), runtime JAR inspection and inventory validation are verified against their archived evidence and frozen source identity 94b628481751378665a40a3b033430b1224ec0adc817f811386bc44db59ef24f. Root and every JDK report 1019 tests, zero failures/errors and three unchanged optional scale skips. Required Windows x86-64, macOS x86-64 and macOS arm64 evidence remains absent, so #30 is unfinished/INDETERMINATE. Earlier pending or failed observations below are historical to their stated review time and inputs. No whole-task/platform certification or publication approval is supplied.

## Initial mandatory findings (closed by repair review below)

1. P2 — Require PDF 1.5 before painting shaped ActualText.
   - Hunk: pdf-document/src/main/java/net/zerocloud/pdf/PdfBoxShapedFont.java:79–81 unconditionally emits `/Span << /ActualText ... >> BDC`.
   - Admission path: PdfBoxPositionedTextOperations.java:153, 689–700, 1071–1083 retains T19 checks (Type 0 minimum 1.2 and supplementary mappings minimum 1.5) but adds no minimum for this new marked-content feature.
   - Trigger: unsigned INCREMENTAL input whose effective version is PDF 1.2–1.4, with a BMP-only admitted font, and an explicit shaping preference. The old version is preserved while the new feature is written.
   - Documented rules: docs/pdf-version-password-security.md:76–81 preserves the incremental Source version; docs/harfbuzz-shaping.md:133–136 promises publication-version checks before painting. ISO 32000-1 §14.9.4 specifies PDF 1.5 for marked-content ActualText: https://opensource.adobe.com/dc-acrobat-sdk-docs/standards/pdfstandards/pdf/PDF32000_2008.pdf (primary source searched and inspected; §14.9.4, printed p.615). A structure-element ActualText has a different PDF 1.4 minimum and is not what this code emits.
   - Required repair: reject the unsupported incremental version through a stable DocumentFailure before painting/publication; retain both-mode public regressions with unchanged targets. This review did not execute that regression.

2. P2 — Release obsolete whole-run measurements between buffered relayouts.
   - Hunk: PdfBoxPositionedTextOperations.java:170, 214–215, 264 puts whole-run shaping in PreparedText.shapingMemory and only closes it with PreparedText.
   - Lifecycle: PdfBoxParagraphOperations.java:190 reuses bufferedText for every relayout; shapeAtoms calls shapeRuns(..., null) at line 1033 each time. ShapingCoordinator.java:90 retains 128 bytes per result glyph plus logical text. Old initial atoms/results are no longer referenced after the completed Layout, but their reservations remain in the reused PreparedText scope.
   - Consequence: repeating the same permitted buffered relayout adds obsolete modeled owned-memory charges, eventually causing a spurious memory-limit failure. The separate candidate-line scope fix does not release these initial estimates.
   - Documented rules: docs/harfbuzz-shaping.md:226–230 models retained plans and ties shaping reservations to their use; CONTEXT.md's Workflow Resource Usage defines an owned-memory high-water account; ADR-0025 requires resource ownership/lifecycle discipline.
   - Required repair: give these initial measurements a layout/content lifetime, or reuse genuinely retained measurements; test repeated public relayout under a measured finite memory budget. Static lifetime finding, not a locally executed reproduction.

3. P2 — Count every scalar visit in whole-grapheme fallback.
   - Hunk: PdfBoxPositionedTextOperations.java:186–199 increments fallbackChecks once outside the scalar loop, then probes every scalar in that candidate font.
   - Trigger: one declared font and a two-scalar grapheme such as Devanagari ki. Initial select spends two scalar visits; whole-grapheme coverage then probes two more scalars but charges one. A maximumFallbackChecks value of three can pass despite four visits.
   - Documented rules: docs/font-loading.md:284–296 defines each ordered candidate-font visit for each input scalar and exact-boundary admission; docs/harfbuzz-shaping.md:238 retains fallback limits.
   - Required repair: count each scalar probe or reuse prior coverage without probing again, with exact-boundary public behavior coverage. Static arithmetic finding; the review did not run a new test.

## Judgment call (nonblocking heuristic)

Possible Primitive Obsession / Mysterious Name: T29ShapingReference.java:12–18 returns raw 20-column String arrays. T29NativeShapingAssertions.java:44–61,80–85 and T29ShapingSemanticAssertions.java:70–89 repeat numeric parsing and magic indices such as first[18], row[10], row[12]. A small private named reference-row value would keep glyph, cluster, run and geometry fields together and make both independent observation consumers easier to audit. This is a smell baseline judgment, not a repository-rule violation; no public abstraction is requested.

## Coverage and evidence limits

- Read the user execution contract attachment, AGENTS.md, CONTRIBUTING.md, CONTEXT.md, docs/agents/domain.md and docs/agents/issue-tracker.md; read #30/#1 bodies, labels and all comments through gh.
- Read all requested ADRs (0002,0006,0007,0009,0010,0012,0016,0018,0022,0023,0024,0025,0026,0027,0029,0031,0033,0039) and Provider/font/Unicode/Worker/shaping contracts; additionally checked paragraph and PDF version contracts for concrete findings.
- Reviewed detached HRQ1/HRS1 DTOs, actual Java Provider adapter and C helper, installer/Meson/pins, initialization and parent-brokered Worker exchange/failure catalog/class inventories, line shaping/refitting and script/fallback logic, direct unshaped positioning preservation, shaped CID/ToUnicode/subset writing and name collision handling, TrueType reflected-composite admission, buffered and incremental table ownership, and resource reservation paths.
- Reviewed acceptance native/semantic/product/reference/recorder code, independent reference and subset CLIs, native observation CLI and negative fixtures, new Java/Python tests, visual profiles and selection support, Maven/reactor/CI/JDK integration, Capability Matrix/Facade exclusion, English/Chinese/domain/provenance/dependency documentation. Generated inventory formatting/consistency is tooling-enforced and not a smell finding.
- Read historical development receipt excerpts. They distinguish failed green attempts from passes and retain Red/Green/Refactor paths. They are not proof that current root/JDK gates passed; none is claimed here. Strict TDD repairs remain required for these findings.
- Independent read-only hashes matched the frozen reference PDF, oracle JSON, TSV and properties against T29-reference-receipt.json; both current reference-generation script hashes matched that receipt. All five used font hashes matched. All eight expected PNG hashes matched their profiles and each PNG is 480 x 384. Native installation receipt hashes matched the current adapter C, Meson, installer and pin sources. Retained Linux observation reports the installer engine hash. These are artifact identity checks, not new native/platform execution evidence.
- No unofficial Java wrapper, native product/default bundle, implicit installation/font discovery, backend public/protected signature, new unsupported facade stub, tracker mutation, or unsupported platform certification was found in the reviewed changes. Provider execution remains explicitly parent-side in both Workflow profiles, with correctly disclosed containment limits.
- Windows x86-64, macOS x86-64 and macOS arm64 executions remain absent. Repository evidence and matrix accurately retain experimental/indeterminate state. The root/JDK gates and current acceptance archival were in progress; this review did not run Maven, compile engines, alter repository files, or mark any completion criterion satisfied.
- All twelve requested smell heuristics were considered; only the above raw-row issue was material enough to report. Existing domain boundaries and deliberate private Worker/Provider adapters override mechanical forwarding/duplication heuristics.

## Reviewed tree identities

The following records identify delivered paths read or inspected at the review snapshot; text was reviewed through diffs/current files, and large/binary reference/build artifacts through their manifests, hashes and structured fields.

Snapshot UTC: 2026-09-06T14:33:12.149313+00:00

- `.github/workflows/ci.yml` SHA-256 `4694de5e4073fe9d53438fa8f4ca64c13550d2c8b2101f3ab7f2ddd20ca3942a`
- `CONTEXT.md` SHA-256 `2c3d3494f332e3267d3fb5e0aa1dab2224d097b98deff8a653f74e6505e9f1fa`
- `DEPENDENCIES.md` SHA-256 `6eae9da560553a4c825e9a8060dc8f92268f2146d2655f5036eda0290e8c344c`
- `PROVENANCE.md` SHA-256 `6cfe14d213e0d3db73a01ab1dc4c8995dab41c3b58cfa6082b692bad939323d3`
- `README.md` SHA-256 `4f57cfdf1a8919aae91e950973510beeb605e009f60ae3ff236355f55c0771d7`
- `build-tools/inventory/src/test/java/net/zerocloud/pdf/tools/inventory/InventoryCommandTest.java` SHA-256 `c544ea95324b2562c259650c75b1aaf36a97fbedd4600ac5d500fcfc79223078`
- `capabilities/README.md` SHA-256 `2f5b271f34fad9203f05692e245b36975f7c72661ab497aa0ae228ea932c139f`
- `capabilities/capability-matrix.yaml` SHA-256 `55458d48bc7051b19b4bdec6c77a59adc125ce051a2d2d9137768d93bc4f1d79`
- `capabilities/evidence/T29-shaping.md` SHA-256 `2ffd1161566bf97e1f2af34fd9ef264842473e405f0c45d2092f84b57f65770c`
- `capabilities/evidence/artifacts/T29-development.txt` SHA-256 `817f4f4284430f54c0219690e9d959cd1b1552eb2920a566025e114e514596a9`
- `capabilities/evidence/artifacts/T29-linux-adapter-compile-commands.json` SHA-256 `bfed0ef573e4c5e2cc99565a76fe1f52897f559e49acb75b9f3cb88d0b130232`
- `capabilities/evidence/artifacts/T29-linux-engine-compile-commands.json` SHA-256 `1dc169768c44e702e494a35c4fd08a778e72a1b209af1550b67d7e26be89df98`
- `capabilities/evidence/artifacts/T29-linux-native-build.txt` SHA-256 `8a2be4840e42aa37b20d23fe150f307cf2b508ba96fbf61de63b4a2333e05c0c`
- `capabilities/evidence/artifacts/T29-linux-native-installation.json` SHA-256 `b17c12c25135d2aea3708dabcbd436e8ca1f1b88ce5e14d6bc8ebd3f2ad1b178`
- `capabilities/evidence/artifacts/T29-linux-native-observation.json` SHA-256 `72df1db5f5aaac353707108252854cef4e6cd55a090c26618df9a3d913be5ad4`
- `capabilities/expected/T29-shaping-arabic-page-1-144dpi-srgb.png` SHA-256 `ec41f859c304393f1d5315c7f3bb4374e1e6b59f1f2c331970b946ee3bc6b377`
- `capabilities/expected/T29-shaping-arabic-page-2-144dpi-srgb.png` SHA-256 `1744d99f5830a1c2a22f5f43c046d2bd9bbb23a84d99a4b8c2066b717957de5f`
- `capabilities/expected/T29-shaping-devanagari-page-1-144dpi-srgb.png` SHA-256 `a0ee4de06f45edc6321e0898d34cdbada53f19b0984f714f04ecd88b945674f2`
- `capabilities/expected/T29-shaping-devanagari-page-2-144dpi-srgb.png` SHA-256 `c1fbbdd034042515a701b348131ebe18995bee570093110232c8a947465c228a`
- `capabilities/expected/T29-shaping-hebrew-page-1-144dpi-srgb.png` SHA-256 `e88fe2dcb5ff2bfa1d8a08d0e999b76b682ca577e5095732b39d7d2123ae9644`
- `capabilities/expected/T29-shaping-hebrew-page-2-144dpi-srgb.png` SHA-256 `d11f8b3f10bb3dea86618d7e0e6ca7668be75190b2c452458de53ea6545c9e7a`
- `capabilities/expected/T29-shaping-thai-page-1-144dpi-srgb.png` SHA-256 `7ac0d1028f6738306ef42dfb0100489ff5709ae668a6da05e2932cd7d79c96e6`
- `capabilities/expected/T29-shaping-thai-page-2-144dpi-srgb.png` SHA-256 `e5c971fed6080f677490286533524d28aadc25f6f98f64b5bec1feba8b52e546`
- `capabilities/facade-surface.yaml` SHA-256 `ac6ffebf4536f5082d779c26467ff5609816a61a6504b1d986c18f1793a86d52`
- `capabilities/profiles/T29-shaping-arabic-page-1-visual.properties` SHA-256 `36565b79c3dd8e1ab12f4afa2c0b90747e02b85eecdc1863d6905bef66485f20`
- `capabilities/profiles/T29-shaping-arabic-page-2-visual.properties` SHA-256 `3fde4f13cf58a7f01310ec4fc499fd4c769a39843907f484305f149330452f5f`
- `capabilities/profiles/T29-shaping-devanagari-page-1-visual.properties` SHA-256 `9a74cb6471d0d1546509ebd0347e70277a3cc52b495f2d1d7a31520509c06ff8`
- `capabilities/profiles/T29-shaping-devanagari-page-2-visual.properties` SHA-256 `8f998c20b3632ff42c93eba2a36adf378d17404283da858e6716b9e94f0b4c7a`
- `capabilities/profiles/T29-shaping-hebrew-page-1-visual.properties` SHA-256 `c61197eda5792ed6d55f6ceaa1a70da52956bf442e4bbeb1cf2222eb54fe78ab`
- `capabilities/profiles/T29-shaping-hebrew-page-2-visual.properties` SHA-256 `da15b6c37ddc75c87a8112a1a20e789e951529a5424a078e0112e739d97d912c`
- `capabilities/profiles/T29-shaping-reference.md` SHA-256 `bc2160ee42fe176b42c72b4b79edd5d61dd1b293d9458f8b36aa65f0cf0f18ac`
- `capabilities/profiles/T29-shaping-thai-page-1-visual.properties` SHA-256 `88692a22c21fcc2c9d8cccfcc082234f55fca8ae5c5162fe15345cd17bc567b2`
- `capabilities/profiles/T29-shaping-thai-page-2-visual.properties` SHA-256 `0ff9fa03ae92565500ed4ff42b3458bd28e04f1ba8f6778090e23e2d163199b3`
- `docs/adr/0039-keep-glyph-shaping-explicit-and-detached.md` SHA-256 `22e9911f4ead38ec46d58815bf151e107baffcfaeaa4a7ce8e9ccba18e5d5a16`
- `docs/capability-providers.md` SHA-256 `709470365da2ecd944598892b8916f6ceb5926aeb52bb11e0642d5ca50e99cc1`
- `docs/font-loading.md` SHA-256 `96bb9f090a7ee2aed38eb78362f908810a50def33dbcff1042f6cafb35d4aae7`
- `docs/generated/capability-matrix.md` SHA-256 `a04b45f04bc4a0d22389e2249da85ecbfd1a51a7148abff4ed9d72b5e6c6058b`
- `docs/generated/facade-surface.md` SHA-256 `d7f8f6f892f9db6fa2cf9bcb9b2e60d902f9a60f8627e8c0fd4ad896966cc58f`
- `docs/hardened-worker.md` SHA-256 `57029151aae7b6b543a96bf3f9f189c39c0a1b0cf7be9e2008449825fb7d778f`
- `docs/harfbuzz-shaping.md` SHA-256 `86f5665524028bea79a79169b46cd0741c5dfa2fbdf4cb8d4c66e9f11e9d3030`
- `docs/table-composition.md` SHA-256 `1404eb3d61306de1515cc0c5c8b8dcd2b5603aa155bd2389a81ead6d70b3656a`
- `docs/third-party/harfbuzz-10.2.0-COPYING.txt` SHA-256 `ba8f810f2455c2f08e2d56bb49b72f37fcf68f1f4fade38977cfd7372050ad64`
- `docs/third-party/harfbuzz-10.2.0-linux-oracle-installation.json` SHA-256 `ab322b6c4c88932d5b93a26bb59446cbb58b9e8270681a967429944ece2ae51a`
- `docs/third-party/harfbuzz-10.2.0.md` SHA-256 `e2c541e12c722859d1cc7214f0e900399ad775352917af3a2b78747310e69f53`
- `docs/unicode-composition.md` SHA-256 `7929d590b653318ba553b27efbbc86fe20dd6dfd177c45c7d0df1f102ac6b260`
- `docs/zh-CN/getting-started.md` SHA-256 `544c56e2af9363517d35a92007d74981b3a84927bc86512bd0eca22ebcf29db5`
- `pdf-acceptance/pom.xml` SHA-256 `a4f6f95b1015890381de8096c892e798ebbd3261a12224222140df418068ca17`
- `pdf-acceptance/src/main/java/net/zerocloud/pdf/acceptance/T29NativeShapingAssertions.java` SHA-256 `6338fe3a6910fe82abbe14a95b2388a9364d93c8c8a165ce16ddc52f7051f12e`
- `pdf-acceptance/src/main/java/net/zerocloud/pdf/acceptance/T29ShapingEvidenceCommand.java` SHA-256 `f63361ac701ae809b5c78b5919b1e723872a3bfdc9b7eec4a706a05a5ef494ff`
- `pdf-acceptance/src/main/java/net/zerocloud/pdf/acceptance/T29ShapingProducts.java` SHA-256 `ef2a6fe01b916ae1a2a2b3ae5ec4b2e6a839e2596913a5c3e5fcc2b4c319eef0`
- `pdf-acceptance/src/main/java/net/zerocloud/pdf/acceptance/T29ShapingReference.java` SHA-256 `ea2413481af9979234937e9674edaffac1ffa789c2878d1422a1e539ef95b0c2`
- `pdf-acceptance/src/main/java/net/zerocloud/pdf/acceptance/T29ShapingSemanticAssertions.java` SHA-256 `67850da03d5af2fb9041082cc3367ebea090d38df4ffd9e9600d028b27f71009`
- `pdf-acceptance/src/main/java/net/zerocloud/pdf/acceptance/VisualEvidenceChain.java` SHA-256 `9407c0617cd9ace551039b7a9cca87fcc32825c42a7facbaf38ced9d0eadbb51`
- `pdf-acceptance/src/main/java/net/zerocloud/pdf/acceptance/VisualProfile.java` SHA-256 `30f5043322862df90e04339ed953d308d8f3a9673934533d3c0ff8261a64de38`
- `pdf-acceptance/src/main/resources/net/zerocloud/pdf/acceptance/fonts/noto/NotoSansArabic-Regular.ttf` SHA-256 `ceea25b464a656dc3b26849bab9356740401af62aedf1bfa8b7f0d9b75925b1b`
- `pdf-acceptance/src/main/resources/net/zerocloud/pdf/acceptance/fonts/noto/NotoSansDevanagari-Regular.ttf` SHA-256 `385e78e6359a9d88a0f243d53b1209d7548361ba2194e2b9ec779bcaa7e8949d`
- `pdf-acceptance/src/main/resources/net/zerocloud/pdf/acceptance/fonts/noto/NotoSansThai-Regular.ttf` SHA-256 `404ddfb5ed0aaa6b6ec8a85700d682978992062d67da93903967b56cbd9a4acc`
- `pdf-acceptance/src/main/resources/net/zerocloud/pdf/acceptance/fonts/noto/README.md` SHA-256 `15652f9f095e0237708e1c001b42c62564a3c9f9669419ffe2f30f61a4a43d69`
- `pdf-acceptance/src/main/resources/net/zerocloud/pdf/acceptance/fonts/noto/fonts.properties` SHA-256 `fb8a3aa15177106dd75a808afb637d4b8f823a54e7e59ad4bb5527949aef5905`
- `pdf-acceptance/src/main/resources/net/zerocloud/pdf/acceptance/fonts/noto/sources.json` SHA-256 `d5756f9082b8e4c1fc5aa3eb7de16aa4204829c38c17670dcc0a201702522cd6`
- `pdf-acceptance/src/main/resources/net/zerocloud/pdf/acceptance/shaping/T29-corpus.json` SHA-256 `29bd0d8090a78b1b6a6e11e95f35fa673614bd7bd2b448cb8fd36a9b980d2bf2`
- `pdf-acceptance/src/main/resources/net/zerocloud/pdf/acceptance/shaping/T29-corpus.properties` SHA-256 `00621fa88a8a2c159de40551008de4fa66e3d5aeccc110a42b4c36490e3a5c50`
- `pdf-acceptance/src/main/resources/net/zerocloud/pdf/acceptance/shaping/T29-glyphs.tsv` SHA-256 `a89dbb43a2d96375cd0cbf8f3a2b181eef4916ac8ee7535baf31f3c5864624ff`
- `pdf-acceptance/src/main/resources/net/zerocloud/pdf/acceptance/shaping/T29-oracle.json` SHA-256 `3f96696998c0bd8f0c521fbcbd0e20eda1e1a01bd4de92f9d025bb9ee7c7ed82`
- `pdf-acceptance/src/main/resources/net/zerocloud/pdf/acceptance/shaping/T29-reference-receipt.json` SHA-256 `d4402f407cbdefdbb63d062a787c29b3d109bd451fbb2814738f88c57c45e8a6`
- `pdf-acceptance/src/main/resources/net/zerocloud/pdf/acceptance/shaping/T29-shaping-reference.pdf` SHA-256 `fcfb9619614d6618a81396913bcc9d0e351d23adc9a7a815e80780946d02854a`
- `pdf-acceptance/src/test/java/net/zerocloud/pdf/acceptance/T29NativeShapingAssertionsTest.java` SHA-256 `aac0dfafc10d9079d4298f8ba45ed898f8a5d604e32fb4117c0ca9427186d2b8`
- `pdf-acceptance/src/test/java/net/zerocloud/pdf/acceptance/T29ShapingEvidenceCommandTest.java` SHA-256 `8843609d9646ddc9f47c1098187ede3fbdbf799861b69ac72ce7ca69500c8ba1`
- `pdf-conversion/pom.xml` SHA-256 `1f83cf06b3e8451a3666e26b9ecf8aa15db762b32ae97921e76935c9bd832cb3`
- `pdf-conversion/src/main/java/net/zerocloud/pdf/conversion/HarfBuzzCapabilityProvider.java` SHA-256 `df8d71ebb70c42365183912e51ec43bf6a47531213897cb05ecd613dc37e3c4d`
- `pdf-conversion/src/main/native/folio-harfbuzz.c` SHA-256 `aa5fee1936316050ca764bcd32ba811aaeefd05239631f8b92a2df9416186d1d`
- `pdf-conversion/src/main/native/meson.build` SHA-256 `9baa7df666e880d575777152f9c617a38f9a8325ac01b9fccaa2478a62d0bc49`
- `pdf-conversion/src/test/java/net/zerocloud/pdf/conversion/HarfBuzzCapabilityProviderTest.java` SHA-256 `854cba7a0180513323758102f815a0fbb19b3aec4d123f8ebaf97901b47c116c`
- `pdf-document/pom.xml` SHA-256 `c9a65c167c4bd73f00932ecf1eade7ddd5bbbc8c8cbd4c51f8140286344a20e9`
- `pdf-document/src/main/java/net/zerocloud/pdf/DocumentWorkflow.java` SHA-256 `59bd1eea1050cf9176dca904bea20c9e00abe5e5c27664816a8e4d010eb818e0`
- `pdf-document/src/main/java/net/zerocloud/pdf/HardenedWorkerEngine.java` SHA-256 `27441bea7832aeccc43a2b604acb22c67ae54d28f3b683b29d56bbe14cd13304`
- `pdf-document/src/main/java/net/zerocloud/pdf/HardenedWorkerMain.java` SHA-256 `12bc2a8ef1882784d1de680dd99425843d243a94d6138a7c2a6adfcd094da224`
- `pdf-document/src/main/java/net/zerocloud/pdf/PdfBoxLargeTableOperations.java` SHA-256 `308980cb81f68eb86f51e86c8bb21e28ac64668b75f708bdeb27de8f8557b6c4`
- `pdf-document/src/main/java/net/zerocloud/pdf/PdfBoxParagraphOperations.java` SHA-256 `37c36fcc3de4fb7d5d6fa278b440eac461dc64b060d7f36951b4f96ef1d3a9ec`
- `pdf-document/src/main/java/net/zerocloud/pdf/PdfBoxPositionedTextOperations.java` SHA-256 `6eabc1dba2befb469ca023cfab24e96176c5f9a738708faaaa3a3dd23369a73b`
- `pdf-document/src/main/java/net/zerocloud/pdf/PdfBoxShapedFont.java` SHA-256 `e05727d58721a901b7413053e784242a079461b30d91368b44f0813adc256cdd`
- `pdf-document/src/main/java/net/zerocloud/pdf/PdfBoxSubsetNames.java` SHA-256 `810f3d34cd00bb303920d3bdb36faab70bb253a0f7aac8de637cd5c7949a0b26`
- `pdf-document/src/main/java/net/zerocloud/pdf/PdfBoxTableLayout.java` SHA-256 `f35893d5a2c404198717c79a86e8ff53136e8657c906f935530c6fbd5e4a53a3`
- `pdf-document/src/main/java/net/zerocloud/pdf/PdfBoxTrueTypePreflight.java` SHA-256 `6d30d55cae4d7b0f535ffa0c16ca2fab80784f335afb9bc3d60536cbac78954b`
- `pdf-document/src/main/java/net/zerocloud/pdf/ShapingCoordinator.java` SHA-256 `6cb9e778c49c9e5faab8bb81ce9741ebcc40eb13f4ac1039ee75e6a47446e98d`
- `pdf-document/src/main/java/net/zerocloud/pdf/WorkerFailureCatalog.java` SHA-256 `2f07d087054ce0d09919e9641a8d19a9c86970f94178900b1384df5bffcc02c2`
- `pdf-document/src/main/java/net/zerocloud/pdf/WorkerMessages.java` SHA-256 `7a72d171df5df9f7235380d4544c0f1c88dbbb8338427af5f5b447ead9755b61`
- `pdf-document/src/main/java/net/zerocloud/pdf/WorkerProtocol.java` SHA-256 `0395d865b863d31c2c455b7045c90cd7debaaf465a741b3eb6bcfef5ed61b19e`
- `pdf-document/src/main/java/net/zerocloud/pdf/WorkerShapingCodec.java` SHA-256 `2b6b54dd3d1d39e8bca487e84a63614e0d5bf22977a21cce46dc88bdbc49c81c`
- `pdf-document/src/main/java/net/zerocloud/pdf/WorkflowResourceContext.java` SHA-256 `f0c8d60c307fb01cb695a9d9e9425d4f718e1a4c9318f62abfc07316217b5b0b`
- `pdf-document/src/main/resources/META-INF/folio-pdf/document-worker-classes` SHA-256 `c07502e776f31812de5570344e8f98f9cc86f8083b3f20f95f9959d170f39c58`
- `pdf-document/src/test/java/net/zerocloud/pdf/consumer/ShapingCompositionWorkflowTest.java` SHA-256 `74624dc587b8a420cc8b382d7451a72f7322d78a63a50947633d663343a7a427`
- `pdf-provider-contract/src/main/java/net/zerocloud/pdf/provider/ShapingRequest.java` SHA-256 `5b14e6758a7fc56e3b1b11a2d64ce7a0bd74409ce216b163ec9236fd2e5ab797`
- `pdf-provider-contract/src/main/java/net/zerocloud/pdf/provider/ShapingResult.java` SHA-256 `039d11c4e8b1c22b1efcc9df07077dc904e30379f5c3ef5c47d3316c71f88a7d`
- `pdf-provider-contract/src/main/resources/META-INF/folio-pdf/provider-contract-worker-classes` SHA-256 `56340dc06714414d32db2af86d87db696cbe05de93b4ece4571bb3b412a76f16`
- `pom.xml` SHA-256 `9ffd3d585c57064076e0f759509de5f4059c7632ddaebd2eacdd09207cc7a0e3`
- `scripts/harfbuzz-pin.properties` SHA-256 `20ec016fe6728f485c9bf86f28555da14730f56ff025a35ee484ce8a7fbf2207`
- `scripts/install-harfbuzz.py` SHA-256 `896cbd5f2f9f59ffaff6956baa6b74d281dbcb3f41aabce955f4b887348d828c`
- `scripts/t29-native-observation.py` SHA-256 `51130d9e80f258de2ce23f0ca2c26f30ad616c4fe21c3674ef98ac3a2e53217a`
- `scripts/t29-shaping-reference.py` SHA-256 `cb5455845f1050451072cbe56df80678571b3f451b22bc1c24f519821d2b8b6a`
- `scripts/t29-verify-subsets.py` SHA-256 `fdc08212cbc2efba9166b71100cd3841b5700e09f0a90329992154a57d346888`
- `scripts/tests/fixtures/t29-version-mismatch.c` SHA-256 `086734262365eef31a94e0c409cec8025086baef1a0570844243222c0b3079ca`
- `scripts/tests/test_t29_acceptance_entry.py` SHA-256 `d4de370cf58a76fc5edc8a601428d10aaf8b898c25014dc744ff9fe8496a9d76`
- `scripts/tests/test_t29_native_installation.py` SHA-256 `241be1f687583128e357128c4ec1bf180d545fcb45d27e0f9ef1be28318b3c1e`
- `scripts/tests/test_t29_native_observation.py` SHA-256 `db9b61414dbc84eaf8103a4e2b4e80443153fabef671db61bd2f62fd5ff96f46`
- `scripts/tests/test_t29_reference_subsets.py` SHA-256 `1de7d130dfbf46d8c0629eb26de976007d8b12a2cfcbeda47832423879603a63`
- `scripts/tests/test_t29_shaping_reference.py` SHA-256 `31ee2678e3ba2146781eb4183e822573096cc6d4ad27f0ac2cbb4454af9f6aa5`
- `scripts/verify-jdk-matrix.sh` SHA-256 `5b86f20b390f99a759c1050120529e8ffa7226e4882ab29af652280c92a41faa`


## Independent repair review

Reviewed UTC: 2026-09-06T14:44:40.447608+00:00

No remaining mandatory Standards finding was identified in the three repaired paths. The original whole-change review above remains the coverage record; this follow-up independently inspected all ten changed implementation/test/documentation paths relative to that recorded snapshot, and read the focused regression logs. No repository files were edited and no Maven or native process was invoked by this reviewer.

- Version finding CLOSED: PdfBoxPositionedTextOperations.java:141–145 checks effective PDF 1.5 before font staging whenever Composition shaping is selected. The unshaped overload still supplies false. WorkerFailureCatalog.java:370–371 admits the exact stable diagnostic; its PDF_VERSION_UNSUPPORTED capability mask at 1002–1008 admits the composed paragraph/table mappings. ShapingCompositionWorkflowTest.java:241–274 exercises BMP-only PDF 1.2/1.4 rejection, exact diagnostic, NOT_ATTEMPTED and unchanged source/target; PDF 1.5 succeeds and reopens with the expected text. English shaping documentation, Chinese guide, capability matrix and provenance state the same minimum and incremental behavior.
- Owned-memory finding CLOSED: PreparedText no longer holds a fallback shaping-memory scope; shape at PdfBoxPositionedTextOperations.java:208–215 requires the caller scope. PdfBoxParagraphOperations.java:223–231 closes Layout after painting and font finalization; Layout.shapingPlans at 648 owns initial plans via atoms at 700/746 and table prepare at 748, then closes at 943. Large-table emit at PdfBoxLargeTableOperations.java:182–257 passes its try-with-resources scope to prepare at 195 and retains it through paint/finalization. The scope is threaded through atoms→shapeAtoms→shapeRuns and table content; no null ownership fallback remains. Public regression at ShapingCompositionWorkflowTest.java:307–321 compares 32 wide relayouts against a one-repeat measured peak plus 65536 bytes, then narrows to isolated forms to exercise new subset creation. It verifies committed/reopened glyph mappings and advances. The documentation now assigns initial estimates to layout/emission lifetime.
- Fallback finding CLOSED: PdfBoxPositionedTextOperations.java:189–201 now checks and increments the counter inside the scalar loop, immediately before each actual candidate-font probe. Caching SourceProgram once per candidate removes repeated lookup without changing accounting. Public regression at ShapingCompositionWorkflowTest.java:325–355 proves three visits reject atomically with unchanged target, while exactly four visits admit and reopen the two-scalar grapheme. The English contract explicitly includes this coverage pass in the pre-existing fallback budget.

Recorded validation inspected, not re-executed:

- Version Red: 2 failures, one per mode, because PDF 1.2 was published. The first green attempt had 1 Worker diagnostic transport failure and is not a passing receipt. Worker-green: 2 pass; refactor with unchanged unshaped controls: 4 pass.
- Relayout Red: 2 owned-memory-limit errors, one per mode. First green removed the limit errors but failed a secondary per-item aggregate-text assertion in both modes; it is not counted as a passing receipt. Mapping-green uses the public CharacterMapping (ActualText suppresses the item aggregate contribution): 2 pass. Refactor with borrowed-font relayout and incremental-table controls: 6 pass.
- Fallback Red: 2 failures, both modes admitted three visits. Green: 2 pass.
- Final ShapingCompositionWorkflowTest refactor: 34 tests, zero failures/errors/skips, BUILD SUCCESS; finished 2026-09-06T22:43:29+08:00. This is the focused shaping suite, not the complete root/JDK matrix.

The original nonblocking raw TSV-row Primitive Obsession / Mysterious Name judgment remains unchanged. Windows/macOS executions, final root/JDK gates, generated inventories and current acceptance archival are not certified by this follow-up. No completion checkbox is evaluated or marked here.

### Repair source identities

- `PROVENANCE.md` repair SHA-256 `f702f8aab4c1391c5c0e0969f090d243f37b7d59c8e666fd92a7e1b53bbb1fcc`
- `capabilities/capability-matrix.yaml` repair SHA-256 `acc61d5c9cab204ecd75ff08d7cb1e97f3b577812494e66c7ae42490b05f0310`
- `docs/harfbuzz-shaping.md` repair SHA-256 `5f1247ff85c14400a829683c0ef3e1ba478f3a1e0c7f5645db1d751348e313bd`
- `docs/zh-CN/getting-started.md` repair SHA-256 `171198213a6005e07ea5028fb2201c0d5709c78cc5551775ae21af348a9d558e`
- `pdf-document/src/main/java/net/zerocloud/pdf/PdfBoxLargeTableOperations.java` repair SHA-256 `26ef38ce285904ae7561660e469f0c901f8ebd5295d3725489e175a713b2fc84`
- `pdf-document/src/main/java/net/zerocloud/pdf/PdfBoxParagraphOperations.java` repair SHA-256 `0f3aeed1fed4ec924c39c7ca9905e4c48579a425025025c25e4a29df3c717d04`
- `pdf-document/src/main/java/net/zerocloud/pdf/PdfBoxPositionedTextOperations.java` repair SHA-256 `18fffbdd2de144975fed842577a51fa0a7709c52b3b1925c2b2d9093ab24de0f`
- `pdf-document/src/main/java/net/zerocloud/pdf/PdfBoxTableLayout.java` repair SHA-256 `67a578a3a96affb752c280ec1b02f06841d2a2e37145deb916552f8529e8ddf6`
- `pdf-document/src/main/java/net/zerocloud/pdf/WorkerFailureCatalog.java` repair SHA-256 `0250027ac8ea7e4f6ebca172bc81be4b782bc3f03e44be6e665eee281fd47ab1`
- `pdf-document/src/test/java/net/zerocloud/pdf/consumer/ShapingCompositionWorkflowTest.java` repair SHA-256 `ccf0cbf02cb051b9b6ef66d9f37c33784cef69b2f97cb632d1525e838efd3a36`

### Inspected regression log identities

- `/tmp/folio-t29/shaping-pdf-version-review-red.log` SHA-256 `0cddedd98d9c640fcfa7d12065196f1f35c8fee09a3418c44a3e6597f3fdf337`
- `/tmp/folio-t29/shaping-pdf-version-review-green.log` SHA-256 `8cff62a7e320c3233e56ca1161f7409de40a01b54a9dd8fa454ec40f3f943f91`
- `/tmp/folio-t29/shaping-pdf-version-review-worker-green.log` SHA-256 `3300feb5966ca6314587bec78e470e3a277507dc02319c9d8a9a81e2dab80197`
- `/tmp/folio-t29/shaping-pdf-version-review-refactor.log` SHA-256 `b06e4a3233a5abd4f115cdff2a81689b5f2df0be0f1a4be8193b6bd7afc91b3c`
- `/tmp/folio-t29/shaping-relayout-memory-review-red.log` SHA-256 `515272184d9b6b7f33c1475fb1cfa36206a256277a0151a2b4059cefb6f38ffd`
- `/tmp/folio-t29/shaping-relayout-memory-review-green.log` SHA-256 `2cfe740f96b87b77253fa62a9b17d91663544824b829d41c8ebbd68268eb75a5`
- `/tmp/folio-t29/shaping-relayout-memory-review-mapping-green.log` SHA-256 `1bcc0218935db1b97871cff249ddc1dd57cd95c93100c7925a3c139d4444984e`
- `/tmp/folio-t29/shaping-relayout-memory-review-refactor.log` SHA-256 `8c4777020d63d459f2191b4b9182214f9e7659a525502d76789b441012f319bc`
- `/tmp/folio-t29/shaping-fallback-visits-review-red.log` SHA-256 `dea810f05e0061d284285b77f9cebd7c2c850c57450269554739791704367926`
- `/tmp/folio-t29/shaping-fallback-visits-review-green.log` SHA-256 `4b80535cc59af970edd454aea52edc40052be23cfaf867d09a9c7f8121c04fde`
- `/tmp/folio-t29/shaping-final-review-refactor.log` SHA-256 `2138a84ac3031a4c8fbe16ce775c7e649d190d58eb2a13f547587ae3579ac595`


## Independent archived Linux evidence and inventory review

Reviewed UTC: 2026-09-06T14:54:50.226830+00:00

Result: no new mandatory Standards finding and no new material heuristic smell. The prior three runtime findings remain closed. The one nonblocking named TSV-row judgment remains unchanged. Read-only review; no build, native engine invocation, repository write, tracker action, or completion checkbox action was performed.

Evidence inspected:

- capabilities/evidence/artifacts/T29-linux-run.json identifies the exact acceptance-t29-record reactor command and explicit helper/Python environment, a successful command exit, four reactor JARs, two Workflow modes, six passing chains, the frozen source snapshot and 136 archived files. Its status explicitly limits the result to Linux and leaves other platforms/final gates incomplete.
- Independently recomputed all 663 source file SHA-256 values and byte lengths. Recomputed the documented sorted UTF-8 canonical hash as 39b761992f1677fe25ee7c4d5796c1505a024a890880d51aca95ed516024565f. The snapshot file's own hash matches the run manifest. Independently enumerated current module src trees/POMs, Maven wrapper/config, scripts, CI, LICENSE/NOTICE and T29 profile/raster inputs: none is missing from the recorded source scope. This verifies current identity; the before/after-run observation remains attributed to the recorded run receipt.
- Independently checked every archived file's SHA-256 and length against the 136 manifest entries, and compared its bytes with its corresponding file in /tmp/folio-t29/evidence-linux-final. All matched. The exact command log hash/length and all four current reactor JAR hashes also matched the recorded identities at this inspection. The command log reports BUILD SUCCESS.
- Inspected all six chain records, two syntax-mode records, the 16 page/mode visual records and the underlying native, installation, semantic, subset, qpdf and raster observations. Native data contain no mismatched run; semantic records cover both modes and eight pages. Both subset records pass for the five explicit fonts, including Devanagari's two composite dependencies. The installation observation identifies the same pinned helper/engine and empty loader-interposition environment, with its separate startup-invocation limit explicitly disclosed.
- Independently recounted RGB pixel differences for all 16 archived page/mode observations: all primary expected-to-PDFium counts are zero; maximum PDFium-to-secondary difference is 1437, within the unchanged 3000 bound. Every count matches its raw record. All expected raster bytes equal their frozen authority; all images are 480x384. Visually inspected all eight IN_PROCESS PDFium rasters; no missing content or clipping was apparent. This visual observation does not broaden the declared script corpus.
- Independently recomputed both product ID-neutral SHA-256 values by replacing only the two trailer ID hexadecimal values with zeroes. Both equal a6c1522148d25ec716119791bdc682b9c3b7682b0b9ebfcbbc69a8823964318c. Verified local links in all 24 archived Markdown records plus the hub: no missing local target.
- Reviewed capabilities/evidence/T29-shaping.md:68–92 and its promotion boundaries. The hub accurately links the run/snapshot/six local chains while retaining overall indeterminate status and absent Windows/macOS rows. Read the newly retained TDD result excerpts and independently checked all 145 historical log identities against their current local originals: no mismatch. Failed green and zero-test invocations remain explicitly excluded from passing TDD evidence.
- Reviewed capabilities/capability-matrix.yaml:2030–2056 and docs/generated/capability-matrix.md:525–603: syntax/semantic/visual pass records identify the actual producers, while mandatory independent standards evidence, dependency/promotion gates, experimental status and no certified platforms remain visible. The generated Facade exclusion agrees with its authority and adds no surface. The inspected shaping-final-inventory-generate.log reports successful validation of 21 capabilities, 12 Facade surfaces and 20 exclusions, followed by generation of both Markdown files. Tooling-enforced formatting was not treated as a smell.

Final root verify and JDK 8/11/17/21 matrix receipts remain pending at this review. No missing-platform result has been promoted by Linux success. Later final gate receipt and hub edits require their own narrow evidence review if they are used to close a criterion.

### Evidence review identities

- `capabilities/evidence/artifacts/T29-linux-run.json` evidence-review SHA-256 `5111af9cd99706a3de3ba8c77635d2dce2bec679eb10cf18824e82f2b48a5b5a`
- `capabilities/evidence/artifacts/T29-final-source-snapshot.json` evidence-review SHA-256 `8e677bb64ecb6a6b3c8c3b6edb333a1e0932be8f1ac14d6bbda07ea95867e423`
- `capabilities/evidence/T29-shaping.md` evidence-review SHA-256 `87456ba18185e8839bd79451a5a484bc9651114ce1cbe8ec98ab670bf27dbefa`
- `capabilities/evidence/artifacts/T29-development.txt` evidence-review SHA-256 `d384ed4e2a1564bb9e801a17d7266983b6463fb61d5e1b79ca575e678e6f920e`
- `capabilities/capability-matrix.yaml` evidence-review SHA-256 `e0e98ccd29c33d89c22400355d2687b38972af68c714c0ebb2fc898683767a47`
- `docs/generated/capability-matrix.md` evidence-review SHA-256 `50d57b93f99b146be949bc49228a33a1dcd584cc6674ea09cdab2d3f53d170cf`
- `docs/generated/facade-surface.md` evidence-review SHA-256 `d7f8f6f892f9db6fa2cf9bcb9b2e60d902f9a60f8627e8c0fd4ad896966cc58f`
- `/tmp/folio-t29/shaping-final-inventory-generate.log` evidence-review SHA-256 `b02aac098f56f3eacd0311b102fe226d320e6f75077f3c4df5da741f6f240ce7`

The reviewed 136 archived path identities are exactly the archived_artifacts entries of the run manifest identified above; no archive entry failed the independent identity or original-byte comparison.


## Independent final getter Javadoc delta and completed root-gate review

Reviewed UTC: 2026-09-06T15:32:09.386086+00:00

Result: no new mandatory Standards finding or material heuristic smell. The three original runtime findings remain closed; the earlier raw TSV-row judgment remains nonblocking. This review performed read-only source/log/hash checks and six fresh javap disassemblies. It did not run Maven, compile source, invoke the native engine, or change repository files.

Documentation review:

- ShapingRequest.java:102–131 adds readable summaries before the existing @return block tags for six getters. The descriptions accurately preserve font-copy ownership, logical UTF-16 input, explicit script/language/direction and the requested glyph limit.
- ShapingResult.java:38–57 and 155–189 similarly document eleven getters. Version reporting, native units-per-em bounds, direction validation, immutable visual-order glyph results, half-open logical cluster indices and signed pen/drawing metrics agree with the existing validation and public contract.
- Independently hashed the before.json source text against the recorded tested snapshot and both current files against final-javadoc-delta.json. Reconstructed the exact unified source patch; it byte-matches final-getter-javadoc.patch and its declared SHA-256. Exactly 17 Javadocs changed (6 request, 11 result). Removing only Javadoc comments yields byte-identical remaining source text. All other 661 compilation/reference inputs still match snapshot 39b761992f1677fe25ee7c4d5796c1505a024a890880d51aca95ed516024565f.

Bytecode proof reviewed independently:

- Rehashed all 32 preserved tested class files and all 32 current class files, including nested classes, against before.json and final-javadoc-delta.json. All recorded identities match. Twenty-nine class files are completely byte-identical.
- The changed files are ShapingRequest.class, ShapingResult.class and ShapingResult$Glyph.class. Executed fresh javap -v -p for both preserved/current bytes of each. Compared complete output after excluding only Classfile path, modification-time/size/checksum header lines and LineNumberTable headings/entries. Constant pools, instructions, signatures and all remaining printed metadata are equal. Fresh views also agree with the stored raw inspection views under those same narrow exclusions.
- This accepts executable equivalence for this comment-only delta, not complete byte identity: debug line numbers moved when the block comments grew. Root/acceptance/JDK run identity continues to refer to the preserved tested source snapshot; the current documentation delta is separately bound by this proof.

Completed root and documentation checks, inspected rather than rerun:

- final-root-verification.json and final-root-verify.log match by SHA-256 and byte length. Independently summed all 58 per-class log records: 1018 tests, 0 failures, 0 errors, 3 skipped. The full reactor reports BUILD SUCCESS and finished 2026-09-06T23:06:41+08:00 (15:06:41 UTC). The three skips belong to HardenedWorkerScaleProfileTest; its current source is byte-identical to the fixed review baseline. The only warning lines are those skip summaries.
- final-inventory-check.log reports successful validation of 21 capabilities, 12 Facade surfaces and 20 exclusions, and confirms generated inventory documentation is current.
- final-javadoc-delta.json records strict javadoc -Werror -Xdoclint:all --release 8 exit 0. Its referenced final-public-javadoc-refactor.log is empty and matches the recorded hash, containing no warnings. The inspected final-javadoc-block-bytecode-build.log reports successful recompilation/package with tests deliberately skipped for this documentation proof. That build is not a replacement full test run.

Matrix result remains unresolved: the parent reports an initial JDK 8 T28 Worker termination, a passing isolated exact-JDK reproduction attempt and a full rerun in progress; the cause remains unproven. This reviewer does not label it environmental or certify the matrix. Windows/macOS execution evidence remains absent. No checkbox, PR, commit or tracker action is performed or approved here.

### Javadoc and root receipt identities

- `pdf-provider-contract/src/main/java/net/zerocloud/pdf/provider/ShapingRequest.java` Javadoc/root-review SHA-256 `f073f8e80947c1ace7fc8490e128e59810f7fb7299646fb7e4cc5f7249bfe56b`
- `pdf-provider-contract/src/main/java/net/zerocloud/pdf/provider/ShapingResult.java` Javadoc/root-review SHA-256 `0c94c09d7685dbb6b632d2be4f129e0fdf8433a19a209008e01ad870e108fabd`
- `/tmp/folio-t29/final-javadoc-delta.json` Javadoc/root-review SHA-256 `7ef352d9cd68d38110ef3d16d97dbaf2a5eff0b87e1d91b262a6f5576f750355`
- `/tmp/folio-t29/final-getter-javadoc.patch` Javadoc/root-review SHA-256 `224bc59eefe6cb2a8d8aa4b041d6d6b09b1f4dea89b00e85b6a70f5a1da01def`
- `/tmp/folio-t29/javadoc-before.json` Javadoc/root-review SHA-256 `19888daeb1739d03eb2775a9720b2ff32ccf8655807c8130636268f22a744d56`
- `/tmp/folio-t29/final-root-verification.json` Javadoc/root-review SHA-256 `14d78b4ddec6408e24ea6cfc5cc8946bca254581b876c29b230bea467172104b`
- `/tmp/folio-t29/final-root-verify.log` Javadoc/root-review SHA-256 `f37fa5baa8f2068bce42f21907f072dd8c131c4c1ab2b0e67d0972d9f7a1e2f6`
- `/tmp/folio-t29/final-inventory-check.log` Javadoc/root-review SHA-256 `d642d19dcfbde059ce5f11b99dc6da369a7f0377e2fb60d8b7ef1e254999bf1a`
- `/tmp/folio-t29/final-public-javadoc-refactor.log` Javadoc/root-review SHA-256 `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`
- `/tmp/folio-t29/final-javadoc-block-bytecode-build.log` Javadoc/root-review SHA-256 `4d4ec3f4f873fadf7a5e544db836222e3cf45d6d629e41cb574f83c3c1eac1b7`


## Bounded unshaped lifetime review during JDK 8 heap diagnosis

Reviewed UTC: 2026-09-06T15:51:43.918285+00:00

Scope: actual fixed BASE→current changes in unshaped PdfBoxParagraphOperations and PdfBoxPositionedTextOperations ownership/allocation, with the unchanged T28 producer and the parent's diagnostic/baseline logs for the concrete trigger. No Maven, compilation, reproduction, native invocation or repository edit was performed. This is a bounded negative source finding, not a root-cause determination or a passing JDK gate.

Mandatory findings: none established in this scope. No new material heuristic smell.

Concrete comparisons:

- The diagnostic at /tmp/folio-t29/final-jdk8-acceptance-diagnostic.log:301–331 records Java heap OutOfMemoryError in FontBox RandomAccessReadDataStream/GlyphTable parsing, reached through PDType0Font.load→loadedFont→encodeRuns→PreparedText.draw. It is an actual heap failure, not proof of a timeout or environmental cause. The baseline diagnostic narrowed suite passed once; an isolated current method also reportedly passed. These observations do not identify which change, if any, caused the heap excess.
- Unshaped preparation still runs the original scalar selection, mapping validation, publication checks and subset preflight. Exact source comparisons confirm unchanged stageAndParse, select, validateMappings, preflightSubsets, PreparedText.draw, encodeRuns, loadedFont, replacementFont, closePrograms and closeKeys paths. The FontProgramKey, ParsedProgram, SourceProgram and LoadedFont class blocks are byte-identical source. Parsed-font closure, session-retained font keys and loaded-font cache replacement therefore have no identified new source-level lifetime in this path.
- PdfBoxPositionedTextOperations.java:174 adds an empty shaping-font map; the additional limits and fallback-counter references do not point to font programs. PreparedText.close at 269 remains closePrograms(programs). The new outer shapedFonts list and subset-name reserved set remain empty when shaping is not used; PdfBoxSubsetNames construction does not scan document objects or copy source fonts.
- PdfBoxParagraphOperations.java:1000 explicitly stores null in Atom.shapingSource when PreparedText.shapes() is false; no new Atom→PreparedText retention occurs. Added Atom fields contain primitive boundary/direction data and null shaping references in this path. The measured unshaped line uses the original atom list and advance-array allocation pattern; the extracted nextUnit helper allocates no font or fragment copy. retainCandidate/releaseCandidate are no-ops for lines with null shapingMemory.
- Layout at PdfBoxParagraphOperations.java:223–231 now remains referenced through its close after painting/finalization. This is a concrete liveness change, but the T28 producer uses version-1 flows: advanced content/table collections are empty, its shapingPlans scope stays empty, and command/prepared/lines are already live through execute/paint. No additional large object retained solely by the later Layout close was identified. This review does not infer JVM/JIT liveness or GC timing from source alone.
- The unchanged T28 fixture contains 15–47 text scalars per profile and uses seven successive version-1 ComposeParagraphs commands. No buffered relayout or table path is exercised at this failure. The observed new per-Atom fields and empty collection/scope objects establish some allocation overhead but do not establish a mandatory font-sized retention regression or explain the FontBox allocation failure.

Disposition: preserve the failing JDK 8 evidence and continue the controlled diagnosis. No source repair is requested on the basis of an unproved retention hypothesis, and no JDK/platform signoff is given by this review.

### Diagnostic review identities

- `pdf-document/src/main/java/net/zerocloud/pdf/PdfBoxParagraphOperations.java` unshaped-review SHA-256 `0f3aeed1fed4ec924c39c7ca9905e4c48579a425025025c25e4a29df3c717d04`
- `pdf-document/src/main/java/net/zerocloud/pdf/PdfBoxPositionedTextOperations.java` unshaped-review SHA-256 `18fffbdd2de144975fed842577a51fa0a7709c52b3b1925c2b2d9093ab24de0f`
- `pdf-document/src/main/java/net/zerocloud/pdf/PdfBoxSubsetNames.java` unshaped-review SHA-256 `810f3d34cd00bb303920d3bdb36faab70bb253a0f7aac8de637cd5c7949a0b26`
- `pdf-acceptance/src/main/java/net/zerocloud/pdf/acceptance/T28UnicodeProducts.java` unshaped-review SHA-256 `594693a8b4aceda23610f158cff8ff0a867c32a3152e3438debd46c6c9dae1f2`
- `pdf-acceptance/src/test/java/net/zerocloud/pdf/acceptance/T28UnicodeEvidenceCommandTest.java` unshaped-review SHA-256 `7aa9820c463b10e98dc1620d5797e778a835cb7e56b032b0ef73b30675526403`
- `/tmp/folio-t29/final-jdk8-acceptance-diagnostic.log` unshaped-review SHA-256 `a1554868eaff237ed32ed2b6cee54bdbf230142480422c9c11939b5e8dd59f55`
- `/tmp/folio-t29/final-jdk8-baseline-diagnostic.log` unshaped-review SHA-256 `d7e8c7191bcafd1b02431091459241d0d5d81407064c0d524e66ee51084b1a25`


Diagnostic-only liveness hypothesis, not a finding or proposed production repair: compose could retain and close Layout.shapingPlans directly in try-with-resources, leaving Layout as an ordinary local with no later close call after its paint arguments are extracted. Layout.close currently only delegates to shapingPlans.close, so the scope would still remain live through painting/finalization and close on failure. OwnedMemoryScope has no Layout backlink. This isolates the possible effect of keeping Layout itself live after its last data use. It does not establish that the current Layout retains an additional large version-1 object or that JVM/JIT liveness caused the OOME; evidence is required before adopting a repair.

The repeat narrowed-current diagnostic log again records Java heap OOME at the same FontBox allocation/PreparedText.draw path. Its retained identity is:
- `/tmp/folio-t29/final-jdk8-acceptance-diagnostic-repeat.log` SHA-256 `0dce5c05d0d23333d0279d870d3f9708d0da80d5325894a0da0ebcc25ecf1ef6`


## Independent content-canonicalized one-shot font snapshot repair review

Reviewed UTC: 2026-09-06T16:30:10.409108+00:00

Result: no mandatory Standards finding and no new material heuristic smell. The repair is suitably bounded to resolving the required #30 font/Unicode/JDK gate without changing font selection, borrowed ownership, GC settings, resource defaults, profile contents or tolerances. It addresses a demonstrated pre-existing duplicate snapshot cache; this review does not claim T29 introduced that cache or establish why the pre-repair failure frequency changed.

Scope and source identities:

- Compared PdfBoxPositionedTextOperations.java against the preserved matrix-17-21 copy whose SHA-256 matches the prior frozen snapshot. The production delta is only a private content map for one-shot snapshots, the staging canonicalization block and a borrowed-byte constructor path for the existing FontProgramKey.
- Reviewed the new FontLoadingWorkflowTest method/helper against BASE. Rechecked all prior frozen compilation/reference paths: the only differences are these two files and the two already-approved getter-Javadoc files. No diagnostic instrumentation or production GC/resource-setting change was introduced by this repair.

Ownership and equality:

- PdfBoxPositionedTextOperations.java:856–879 still consults stagedOneShotSources by FontSource declaration identity before reading. Reused declarations reuse their snapshot; each distinct stream/channel is still read to EOF, and neither handle is closed. PATH and BYTES staging remain unchanged.
- A new declaration's temporary owned buffer remains alive while its content key is hashed/compared. On equality, only the existing private canonical byte array is retained; the new staged buffer closes its temporary reservation. On a new program, the code charges 128 plus its full length before publishing the content-map and identity-map entries, then closes only the old temporary reservation. The retained canonical array remains privately reachable for the same Session lifetime as the previous cache. OwnedBytes.close releases accounting without mutating the array.
- The new borrowed FontProgramKey overload at 1653–1674 does not close or transfer caller-owned state; its raw array comes only from private staged bytes. The original owned-key path still closes OwnedBytes on constructor failure. Stored bytes are not mutated, keeping keys stable.
- The existing equals at 1691–1710 compares length and every byte after hash routing, with periodic resource checkpoints. Different content with the same hash cannot be merged. Hashing retains its checkpoints; resource exceptions propagate through the existing stage failure mapping. The metadata charge covers the additional unique content key/map entry; no duplicate full-buffer charge remains.
- stageAndParse at 788–796 still applies aggregate maximumSourceBytes to every source occurrence, and declaration/source-count admission is unchanged. Storage deduplication therefore does not weaken the explicit font limits documented in docs/font-loading.md:40–43 and 284–295.

Public regression and evidence:

- FontLoadingWorkflowTest.java:320–387 compares 128 commands reusing one declaration with 128 distinct alternating borrowed stream/channel declarations carrying equal bytes, using the control's public peak plus 65536 bytes as a finite policy. It checks committed publication, the bounded peak, exactly one full read per handle, preserved borrowed ownership, one reopened embedded font and the expected ToUnicode mapping. The existing unequal-font, exact source-count/byte-boundary and ownership tests remain in the refactor suite.
- font-snapshot-canonicalization-policy-red.log records the meaningful old-production failure: owned-memory limit exceeded on the distinct-declaration case. The earlier font-snapshot-canonicalization-red.log failed because the test omitted required policy limits and is explicitly not Red proof. Green records one passing public case.
- The completed current-source refactor log reports FontLoadingWorkflowTest 22 pass, ShapingCompositionWorkflowTest 34 pass and T28UnicodeEvidenceCommandTest 2 pass, zero failures/errors/skips, BUILD SUCCESS: 58 focused tests total. The T28 producer still exercises both modes with its original 1 GiB Worker heap; Worker modeled peak is 1294493250 bytes. This focused pass does not replace the pending complete matrix/root/acceptance reruns.
- The diagnostic-only content-cache probe previously reported all 41 selected acceptance tests passing. The failed Layout-liveness probe was not adopted. Saved jhat owner views tie complete cached font arrays to stagedOneShotSources, and the retained heap identity JSON matches the large CJK source-font hashes.
- Independently counted the saved cache query: 19 snapshot values, including 11 full CJK arrays (5 JP and 2 each SC/TC/KR), seven Sans-sized arrays and one Hebrew-sized array. The duplicate-byte reduction is 155144368; subtract six unique metadata charges of 128 gives 155143600, exactly the recorded modeled-peak decrease from 1449636850 to 1294493250. The parent's earlier statement of 12 full CJK arrays is corrected by the saved query's 11. The heap is diagnostic material, not a distributed artifact or new acceptance oracle.

No new test/build/native process was executed by this reviewer. Only read-only source/diff/log/hash checks were performed. The current full root/JDK gates and post-repair acceptance source binding remain open; absent platforms remain indeterminate.

### Font snapshot repair review identities

- `pdf-document/src/main/java/net/zerocloud/pdf/PdfBoxPositionedTextOperations.java` font-snapshot-review SHA-256 `cf7a26613fc6420dc489081e02ef912e52e9503e230a1cd559edb875ddab5a12`
- `pdf-document/src/test/java/net/zerocloud/pdf/consumer/FontLoadingWorkflowTest.java` font-snapshot-review SHA-256 `c320c99dda02e055c3d0a2fee79563598b1bff244ea51cdf9eaa2bf14b6cbcba`
- `/tmp/folio-t29/font-snapshot-canonicalization-red.log` font-snapshot-review SHA-256 `2e93cb236f15b143a0208ac23c552bd89c26e10dddad0649ba13ca3327c56e51`
- `/tmp/folio-t29/font-snapshot-canonicalization-policy-red.log` font-snapshot-review SHA-256 `9baaed2384a6f8a214053567bb1f2dfc9dd359ba5abe7d9b668ed2c082e09c0d`
- `/tmp/folio-t29/font-snapshot-canonicalization-green.log` font-snapshot-review SHA-256 `11329c9d80f9efbad2a5eec4027e185db26aa2b1a1e96681a3b80e290e364097`
- `/tmp/folio-t29/font-snapshot-canonicalization-refactor.log` font-snapshot-review SHA-256 `d6b10a40009bb3c9158e1fec4c411052255144f04c45fcc57d38a72167bc736f`
- `/tmp/folio-t29/final-jdk8-font-cache-probe.log` font-snapshot-review SHA-256 `e334bd9d50f98d21841fd14fed051f792fcdcdfee6a7f17732818e70505fa344`
- `/tmp/folio-t29/final-jdk8-heap-font-identities.json` font-snapshot-review SHA-256 `ee4581720534bf0101dba9bb7c230fd3ae5d7c7df12f8d1fb8669341025d1219`
- `/tmp/folio-t29/jhat-cached-font-identities.html` font-snapshot-review SHA-256 `51827c506b89df21f89842f44e9950ad84cf752a88a18848f42dac4127784184`
- `/tmp/folio-t29/jhat-large-byte-arrays.html` font-snapshot-review SHA-256 `eade05b6eaa0ecf665a3fbeeae7e7c49980034e12de3edd703078e5c50688a9f`
- `/tmp/folio-t29/jhat-large-array-owner.html` font-snapshot-review SHA-256 `d63c92af39782c9348f66f20c01a16c8b2767f1b51141ab5aaa7b2b63286bda5`
- `/tmp/folio-t29/jhat-identity-owner.html` font-snapshot-review SHA-256 `ff02e59a97d1d7c84505738413c6b7e27c1e2fc6c8bd77fe7ad2af489f2168f9`


## Independent per-declaration snapshot metadata correction review

Reviewed UTC: 2026-09-06T16:34:52.035179+00:00

The independent Spec review identified a mandatory accounting gap missed in the preceding Standards repair review: sharing canonical program bytes did not remove the retained stagedOneShotSources identity entry for every distinct borrowed declaration. Charging only a unique content key and bytes left those additional entries absent from modeled retained usage. The current correction closes that issue; no further mandatory finding or material smell is identified in this narrow delta.

- PdfBoxPositionedTextOperations.java:873–875 now retains an additional 128 bytes immediately before inserting each first-use declaration identity entry. It is inside the cache-miss branch and outside the content-cache-miss branch, so it applies to both unique and already-canonical content while repeated use of the identical declaration adds no entry charge. This separates declaration metadata from the unique content-key/byte charge and retains both for their actual Session lifetime. Failure to reserve stops before the declaration entry is inserted; temporary staged ownership still closes on every exit. Font/source admission and borrowed-handle behavior remain unchanged.
- FontLoadingWorkflowTest.java:338–340 adds a public lower-bound observation: 128 distinct declarations must have a greater owned-memory peak than one reused declaration, while the existing upper-bound, open/read-once handles, publication and reopened mapping checks remain. It does not assert an internal size formula or object identity.
- font-declaration-accounting-red.log records one actual assertion failure on the previous implementation: distinct borrowed declarations were not visible in the public owned-memory peak. Green records one passing case and BUILD SUCCESS after the retained entry charge. The full 58-case focused refactor is still running at this review (FontLoading 22 tests have passed; Shaping/T28 completion is not claimed). No Maven or source edits were performed by this reviewer.

The preceding exact 155143600-byte modeled savings calculation describes the first canonicalization probe before this declaration charge. Final current-source usage and acceptance receipts must use the rerun values including the additional identity-entry metadata. Earlier root/JDK/acceptance receipts remain historical to their recorded inputs. No full gate or platform signoff is supplied here.

### Declaration accounting review identities

- `pdf-document/src/main/java/net/zerocloud/pdf/PdfBoxPositionedTextOperations.java` declaration-accounting-review SHA-256 `be977f475dcb93b8965bce990c991817c02e7da1d5d54cb9e5e73bd8321162a5`
- `pdf-document/src/test/java/net/zerocloud/pdf/consumer/FontLoadingWorkflowTest.java` declaration-accounting-review SHA-256 `eb83e2d8b4e3069bf510f7cf54b5c3070fcc4b0f3762df0c87a2a2dbe394521d`
- `/tmp/folio-t29/font-declaration-accounting-red.log` declaration-accounting-review SHA-256 `cbe3287e5e648737cace36321caaa36406fbda92a2c0a984c888083171f11ed0`
- `/tmp/folio-t29/font-declaration-accounting-green.log` declaration-accounting-review SHA-256 `911b946e04f3d1cce71efeb24ed4439582a06b6dc9e11d10c8ee60594f07930b`


## Independent post-font-repair source, evidence and ownership documentation review

Reviewed UTC: 2026-09-06T16:48:15.267096+00:00

Scope: bounded follow-up of the final declaration-accounted production/test state and its new archival receipts, ownership documentation, provenance and delivery narrative. Read-only inspection and independent byte/hash/log checks; no Maven, native execution, source edits or active-gate interference. No new mandatory Standards violation or material heuristic smell was found. The earlier raw reference-row judgment remains nonblocking. This section supersedes earlier pending focused/Linux-run statements, but does not supply a full root/JDK or missing-platform signoff.

### Exact source and clone identities

- Recomputed the sorted `SHA-256 + two spaces + repository-relative path + newline` canonical digest for all 663 entries in T29-verified-source-snapshot.json. Every current file length and SHA-256 matches; canonical identity is `94b628481751378665a40a3b033430b1224ec0adc817f811386bc44db59ef24f`. The file itself matches the current Linux manifest's `6521fd111a3f69bec58ec024ace436e4c48f0a827fb2599142c5000bf541751a` hash and 139143-byte size.
- Independently compared all entries with the original 39b76199 snapshot: the only four changed inputs are PdfBoxPositionedTextOperations.java, FontLoadingWorkflowTest.java, ShapingRequest.java and ShapingResult.java. These correspond exactly to the reviewed private snapshot/declaration accounting repair, its public regression, and the documented getter-only revision. Runtime/source semantics outside those reviewed changes remain bound to the original review.
- Both `/tmp/folio-t29/matrix-font-cache-8-11` and `/tmp/folio-t29/matrix-font-cache-17-21` have the fixed baseline HEAD and all 663 matching current source inputs. Independently enumerated their tracked plus relevant untracked delivery files: each has 1609 files and canonical content digest `14e63c5e45c1572dd959dbf793a1a99aacda12bffc42650c639f538206fd8b06`, exactly matching T29-verified-clone-snapshots.json. This verifies the delivered clone bytes, not completion of their active gates. Later root narrative/evidence changes are correctly excluded from the source-identity scope.

### Public focused run and diagnosis archive

- Verified all 15 linked hash/length observations in T29-font-cache-repair.json: 12 development/diagnostic logs, the final focused log and two saved heap-observation reports. Each of the 13 archived log files is byte-identical to its original /tmp log. The heap dump itself is absent from these archived observations.
- Re-summed the final class results: FontLoadingWorkflowTest 22, ShapingCompositionWorkflowTest 34, T28UnicodeEvidenceCommandTest 2; 58 total, zero failures/errors/skips, BUILD SUCCESS at 2026-09-06T16:36:03Z. The archived log SHA-256 is `1a56928fbda9b0169eecbca64f08937b214d581be313248ea9b6394ab43df8e1`. Its current Worker modeled peak is 1294495682 bytes, including the declaration entry charge. The 2432-byte difference from the first 1294493250-byte prototype equals the 19 retained declaration entries times the separately reviewed 128-byte charge.
- The repair record correctly distinguishes the invalid incomplete-policy fixture failure from the meaningful bounded-memory Red, and separately preserves the declaration-accounting Red/Green. It retains the failed liveness hypothesis as failed, does not label a one-run baseline/isolated success as causal proof, and states that the existing identity cache predates T29. The snapshot repair remains a narrow response to the required gate failure without changing Worker heap, collectors, font limits or resource defaults.

### Current Linux acceptance and preservation of historical evidence

- T29-linux-run.json binds the exact post-repair `acceptance-t29-record` command, explicit helper/Python environment, source snapshot and command log. The archived log hash/length match and it records BUILD SUCCESS at 2026-09-06T16:38:23Z. This command deliberately skips tests and is documented separately from the complete root gate.
- Independently verified every hash and byte length for all 136 current archived run files, and compared each against its original file under `/tmp/folio-t29/evidence-linux-font-cache-repair`: all exact. All four recorded reactor JAR hashes and lengths also matched the local artifacts at inspection.
- The historical ZIP has the recorded SHA-256 `651865271754e8c09f9e7eb6dfa860139659ac22643e11eb49a8ed5afbea02a9` and 363344-byte length. Its 138 entries comprise all 136 prior run files plus the exact prior source and run manifests. Every old artifact matches the original manifest; the embedded old run manifest equals the preservation JSON's copy. The embedded source snapshot matches both its old file hash and the preserved repository file.
- Compared all 80 current PNGs with their historical ZIP entries: byte-identical. The earlier explicit visual inspection therefore applies to the same image bytes. The current/old PDFs in both modes each contain one trailer ID pair. Replacing only the two hexadecimal values with same-length ASCII zeroes, while retaining all surrounding bytes, makes all four PDFs byte-identical with SHA-256 `a6c1522148d25ec716119791bdc682b9c3b7682b0b9ebfcbbc69a8823964318c`. This is precisely the recorder's normalization, not a claim of complete raw PDF byte identity.
- The six archived Linux chains remain PASS in both supported modes where applicable; all 16 primary raster observations retain zero changed RGB pixels and the secondary maximum remains 1437 within the predeclared 3000 bound. These verified archive identities establish the current local evidence binding; they do not substitute for required Windows/macOS executions or the active full gates.

### Documentation and provenance

- docs/font-loading.md:47–51 and docs/zh-CN/getting-started.md:236–239 accurately describe private content sharing, read-once declaration identity, per-declaration memory accounting, unchanged per-occurrence source/aggregate byte limits and caller ownership. The text does not promise sharing across Sessions or equate font-byte identity with declaration identity.
- PROVENANCE.md:1752–1765 records the diagnostic/public-format source, project-authored repair, absence of distributed heap/debugger implementation or product diagnostic instrumentation, unchanged resource/Worker settings, and limits on the causal claim. Its final paragraph separates the earlier root/Javadoc evidence from current Linux acceptance and pending full gates. No disallowed implementation source, native bundle or Reference Suite oracle is introduced by this delta.
- capabilities/evidence/T29-shaping.md:69–106 and T29-delivery.md:185–218 correctly distinguish current source, original source, intermediate Javadoc-only equivalence and historical artifacts. The delivery report retains experimental/INDETERMINATE whole-task status, all required platforms, explicit installation and parent-brokered native-call limitations, with no completion checkboxes or publishing authorization inferred.
- All local Markdown link targets in the two evidence pages, ownership documents and PROVENANCE.md resolve. The previously reviewed capability/facade authorities continue to distinguish actual local records from compatibility/promotion requirements. The active root and four-JDK gates remain pending at this review.

### Post-repair archival review identities

- `capabilities/evidence/artifacts/T29-verified-source-snapshot.json` post-repair-archive-review SHA-256 `6521fd111a3f69bec58ec024ace436e4c48f0a827fb2599142c5000bf541751a`
- `capabilities/evidence/artifacts/T29-verified-clone-snapshots.json` post-repair-archive-review SHA-256 `fc32d7be4870acdbfc2804522b46b661e4c09ae8364b861744431be35ec658f1`
- `capabilities/evidence/artifacts/T29-font-cache-repair.json` post-repair-archive-review SHA-256 `3552f65d9ca0d940889909ce9bffd97e6cc16be154e8db117882597788f077aa`
- `capabilities/evidence/artifacts/T29-linux-run.json` post-repair-archive-review SHA-256 `867bb6b246dbf47cfd1d9f785ca26c3a15cb5208c1b511c8342b999b36573dd7`
- `capabilities/evidence/artifacts/T29-linux-before-font-cache-repair.json` post-repair-archive-review SHA-256 `db277cd09281579a883d22f9be16fc9570b0e18ccc6bfd45dc1b5164f55a4ab0`
- `capabilities/evidence/artifacts/T29-linux-before-font-cache-repair.zip` post-repair-archive-review SHA-256 `651865271754e8c09f9e7eb6dfa860139659ac22643e11eb49a8ed5afbea02a9`
- `capabilities/evidence/T29-delivery.md` post-repair-archive-review SHA-256 `9a476f07a5405c2437571273fa9e3c970885b20d9f03f94c96c7813d9bb9db81`
- `capabilities/evidence/T29-shaping.md` post-repair-archive-review SHA-256 `1fc8fb396f7ef440c8cda2ad2106599478747c1843c6fe11e42670bfa2ac1fc0`
- `docs/font-loading.md` post-repair-archive-review SHA-256 `c35f231534699f162add4905d4c1db2da99121126cf13918b92e03ceb18fb5d5`
- `docs/zh-CN/getting-started.md` post-repair-archive-review SHA-256 `23948ea7406b2a1336b299d5696dd8ccae87f1a05d548bc0b7638acbf70c9e36`
- `PROVENANCE.md` post-repair-archive-review SHA-256 `f6767583fcddd2a48d7bcbc72b4d5557037ea65fb0cecffb13c238f7ecd7b338`


## Independent current-source root, JDK 8, JAR and inventory receipt review

Reviewed UTC: 2026-09-06T17:05:10.362157+00:00

Bounded read-only follow-up; no builds, native calls or repository edits. No new mandatory finding or material heuristic smell. The full root and JDK 8 results below supersede their earlier pending status; JDK 11/17/21 and absent Windows/macOS platforms remain open.

- Rechecked all 663 frozen source inputs in the original worktree and both verification clones: all still match source identity `94b628481751378665a40a3b033430b1224ec0adc817f811386bc44db59ef24f`. No later source changes are inferred from the growing narrative/evidence set.
- T29-verified-root-verification.json binds the exact explicit-helper root `./mvnw -B -ntp verify` command, environment, successful result and source. Its archived raw log is byte-identical to `/tmp/folio-t29/verified-root-verify.log`, matches SHA-256 `75d5350e31f8c36625aedbe5c597207d3692b1914722c8dc97758495f102b55e` and size 55053, and records BUILD SUCCESS at 2026-09-06T16:57:03Z after 16:38 minutes. Independently re-summed 58 per-class records: 1019 tests, zero failures/errors, three skips. The environment receipt hash and size match; it records Linux x86-64, the original GraalVM JDK 17.0.9 and Maven 3.9.16.
- T29-verified-jdk8.json accurately reports one completed JDK block, not completion of the enclosing 8/11 script. The archived 67214-byte block is exactly the combined `/tmp/folio-t29/verified-matrix-8-11.log` prefix ending before the actual JDK 11 banner, with SHA-256 `e62012ee722edc0ff663d033a3c6dc943d6db47c9661cfe2ff3a642f469ea860`. BUILD SUCCESS is recorded at 2026-09-06T16:56:11Z after 18:16 minutes. Independently re-summed its 58 class records: 1019/0/0/3. The unchanged `set -euo pipefail` matrix script invokes the complete Maven verify directly; progression to the observed next banner corroborates successful return of JDK 8. The recorded JDK 8 image ID matches the pre-run observed image receipt; the preserved Java version observation is 1.8.0_502-b07. The current T28 Worker modeled peak includes the repaired declaration charge.
- Both gates' only skipped class is HardenedWorkerScaleProfileTest with its three optional scale cases. Its source is byte-identical to the fixed baseline. No newly disabled test, weakened profile or raised default accounts for the successful full gates.
- Independently opened all five runtime JARs named in T29-verified-runtime-jar-inspection.json and verified their byte lengths and hashes. Their list exactly matches the root receipt's embedded inspection and the current source identity. Scanning every entry for ELF/PE/Mach-O signatures (excluding Java class magic), native suffixes/executable names and all documented known-wrapper prefixes finds no matching entry. There are no nested JAR/ZIP entries in these artifacts. This corroborates the explicit-installation/no-default-native-bundle contract; as the receipt correctly states, the package scan supplements the earlier full source/dependency review and is not represented as a comprehensive source audit by itself.
- T29-verified-inventory-check.json's log is byte-identical to `/tmp/folio-t29/verified-inventory-check.log`, with matching hash/size. It records successful `./scripts/inventory check`, 21 capabilities, 12 facade surfaces, 20 exclusions and current generated documentation. Both inventory authority files and both generated documents still exactly match the previously reviewed verification-clone copies. No matrix promotion or facade mapping is smuggled through the new gate receipts.
- The current delivery receipt, evidence hub and PROVENANCE.md distinguish the post-repair root/JDK 8 passes from historical runs and still state JDK 11/17/21 pending. The root gate is not confused with the acceptance command that skips tests. The documented active commands are `8 11` and `17`, with `21` explicitly future work in the next available clone. Overall status remains INDETERMINATE with all three absent required platforms named. All local Markdown links in these three documents resolve, including the current gate and independent review archives.

### Current gate review identities

- `capabilities/evidence/artifacts/T29-verified-root-verification.json` root/JDK8-review SHA-256 `de0402440596c44572de5d603badb2e242f5129ebea201122e0467e5b441b41b`
- `capabilities/evidence/artifacts/T29-verified-root-verify.txt` root/JDK8-review SHA-256 `75d5350e31f8c36625aedbe5c597207d3692b1914722c8dc97758495f102b55e`
- `capabilities/evidence/artifacts/T29-verified-root-environment.json` root/JDK8-review SHA-256 `c0df111b20b860a845068b2f5089e8246f9e56cbd34a25ac33541cf10cdb882f`
- `capabilities/evidence/artifacts/T29-verified-jdk8.json` root/JDK8-review SHA-256 `d28f0c31a4ebea4d4c8f46d717887bf9a1ed53eb4571661dbbbf659bc5bb7151`
- `capabilities/evidence/artifacts/T29-verified-jdk8.txt` root/JDK8-review SHA-256 `e62012ee722edc0ff663d033a3c6dc943d6db47c9661cfe2ff3a642f469ea860`
- `capabilities/evidence/artifacts/T29-verified-runtime-jar-inspection.json` root/JDK8-review SHA-256 `4ac865e6f085aca443596d3c8b36b971aeb9f4e22491dbe0af9faedecc60ca11`
- `capabilities/evidence/artifacts/T29-verified-inventory-check.json` root/JDK8-review SHA-256 `cfe9c215e145763e17392483041c5954544582b2e93d34b11a5afdb6869d7519`
- `capabilities/evidence/artifacts/T29-verified-inventory-check.txt` root/JDK8-review SHA-256 `b879e991a261e6fa2bb50281738b60822bc99eef4afe7cbf001a0ce3bd711b24`
- `capabilities/capability-matrix.yaml` root/JDK8-review SHA-256 `e0e98ccd29c33d89c22400355d2687b38972af68c714c0ebb2fc898683767a47`
- `capabilities/facade-surface.yaml` root/JDK8-review SHA-256 `ac6ffebf4536f5082d779c26467ff5609816a61a6504b1d986c18f1793a86d52`
- `docs/generated/capability-matrix.md` root/JDK8-review SHA-256 `50d57b93f99b146be949bc49228a33a1dcd584cc6674ea09cdab2d3f53d170cf`
- `docs/generated/facade-surface.md` root/JDK8-review SHA-256 `d7f8f6f892f9db6fa2cf9bcb9b2e60d902f9a60f8627e8c0fd4ad896966cc58f`
- `capabilities/evidence/T29-delivery.md` root/JDK8-review SHA-256 `c4176162c5f087af2e96c28a6f12b759dcaef0a82d8cf445797a7094e2caae54`
- `capabilities/evidence/T29-shaping.md` root/JDK8-review SHA-256 `50b4d1407085b51eaa5d2d81475caf38f7f927125000d95c28d238663f5e9674`
- `PROVENANCE.md` root/JDK8-review SHA-256 `2ec13c0b7498d1a3adc93e070e8e3de48d0379b7772ca589b5bbfb95b2b1a1d0`


## Final independent full-gate, source/image linkage and delivery review

Reviewed UTC: 2026-09-06T17:42:13.553536+00:00

Final mandatory Standards disposition: **no unresolved source or archival finding**. The three original source findings and the later snapshot/declaration-accounting corrections remain closed. No new material heuristic smell was found in the final receipt/documentation delta. The sole remaining judgment call is the nonblocking Primitive Obsession/Mysterious Name concern at T29ShapingReference.java:12–17: raw String[] reference rows expose column positions to native and semantic assertion consumers. This does not invalidate the independent expectations and is not a documented-rule violation.

Mandatory delivery distinction: **#30 remains unfinished/INDETERMINATE** because actual Windows x86-64, macOS x86-64 and macOS arm64 evidence is absent. This review supplies current-source/local-gate assurance, not those missing executions or whole-platform compatibility. No passing build was repeated; this final supplement used only source/log/archive reads, hashing, Markdown target checks and read-only image inspection.

### Completed local gates and linkage

- Recomputed the current source canonical SHA-256 as `94b628481751378665a40a3b033430b1224ec0adc817f811386bc44db59ef24f`. All 663 recorded file hashes and lengths match the original worktree and both verification clones after all gates. The aggregate's linked source manifest file hash/length also matches exactly. The final source therefore remains the source already reviewed and tested, including the font snapshot repair and declaration charge.
- Independently verified every linked command-receipt/log hash and size in T29-verified-jdk-matrix.json. All three archived command logs are byte-identical to their originals: verified-matrix-8-11.log, verified-matrix-17.log and verified-matrix-21.log. The receipts record actual enclosing exit zero for the respective `8 11`, `17` and `21` matrix commands. JDK 21 correctly used the 8/11 clone after that command finished; the two execution streams did not rewrite each other's source.
- Split the actual logs at JDK banners and independently re-summed each block's 58 per-class results. Each of JDK 8, 11, 17 and 21 reports 1019 tests, zero failures/errors and three skips, with exactly one BUILD SUCCESS and no BUILD FAILURE. All report the current T28 Worker modeled peak of 1294495682 bytes. The only skipped class is the previously baseline-verified, unchanged HardenedWorkerScaleProfileTest. There is no weakened profile or new skip explaining these passes.

| Gate | Actual finish UTC | Elapsed | Independently verified totals (tests/failures/errors/skips) |
| --- | --- | --- | --- |
| Full root | 2026-09-06 16:57:03 | 16:38 | 1019/0/0/3 |
| JDK 8 | 2026-09-06 16:56:11 | 18:16 | 1019/0/0/3 |
| JDK 11 | 2026-09-06 17:14:22 | 18:08 | 1019/0/0/3 |
| JDK 17 | 2026-09-06 17:15:26 | 17:28 | 1019/0/0/3 |
| JDK 21 | 2026-09-06 17:29:27 | 14:16 | 1019/0/0/3 |

- The root result retains its independently verified log/environment linkage; those hashes still match. Earlier root/JDK passes and failed JDK 8 attempts remain preserved with their original source identities, rather than being substituted for these complete post-repair gates.
- T29-verified-jdk-images.json matches the independently retained pre-run image observations and the aggregate's image IDs/version outputs. Before/after identities agree for all four Linux amd64 images. Each version observation invokes its immutable image ID and records exit zero. Read-only `podman image inspect` independently confirms the currently retained tag IDs, operating system and architecture still match all four records. Actual versions are Temurin 1.8.0_502-b07, 11.0.32+9, 17.0.20+8 and 21.0.12+8-LTS. No new container, Maven or native engine was executed by this reviewer.
- Rechecked all 136 current Linux acceptance artifact hashes/lengths and the five inspected runtime JAR identities: all still match their recorded bytes. The earlier independently checked image/PDF equality and no-native/no-wrapper inspection therefore still apply to these same artifacts. Inventory authority/generated document bytes also remain exactly the reviewed clone copies, retaining experimental status, no certified platforms and explicit facade exclusion.

### Final workspace and documentation

- The workspace check records fixed-baseline diff-check exit zero, 46 Markdown documents with no missing checked local targets, no diagnostic marker hits, an empty index and no commits since the fixed baseline. Independently checked the present HEAD/index/range and compared the actual changed/untracked path sets: all 42 changed tracked paths match; the only addition to the recorded 268 untracked paths is the workspace receipt itself, making 269. This is the expected observation-time boundary and introduces no unrelated task material. The parent will refresh the brief workspace observation after copying final review receipts.
- An independent current Markdown scan across all 46 changed/relevant untracked documents resolved 758 inline local file targets with no missing targets. The parent receipt's earlier 755-count scan has its own recorded observation time/scope; no missing link or invalid source identity was found. Copying this final review receipt does not change compilation/reference inputs.
- T29-delivery.md:3–7 clearly states all local gates pass while the three required platforms remain missing. Its per-profile PASS table is explicitly Linux-only, and its gate/command sections link the complete current-source root and four-JDK results. T29-shaping.md:51–67 preserves the platform distinction and Linux-only observer/Worker scope. PROVENANCE.md's final paragraph accurately links the current root/matrix passes and retains missing-platform status. Historical Javadoc equivalence still excludes debug LineNumberTable differences from byte identity, and the subsequent complete source gates now cover the documented getters and font repair together.
- The fresh T29-verified-platform-availability.json records the 2026-09-06T17:26:09Z Linux observation and successful repository self-hosted runner query returning zero runners, with no supplied non-Linux endpoint. It explicitly does not infer universal unavailability of GitHub-hosted runners, does not count installation recipes as execution, and does not convert Linux-only live-engine observation into other-platform evidence. Actual non-Linux native/IN_PROCESS requirements remain open even though they are outside the current Linux Worker envelope.
- No completion checkbox, commit, push, PR, merge, tracker mutation, issue closure or publication is inferred from this review. No further local gate rerun is requested: the completed gates bind to the unchanged reviewed source, and no new concern requiring repetition was found.

### Final gate/receipt review identities

- `capabilities/evidence/artifacts/T29-verified-jdk-matrix.json` final-gate-review SHA-256 `bd58e1c6b8de05338e72d82ff72f559a6a0d5999944d2a943319ba7e2294a1c8`
- `capabilities/evidence/artifacts/T29-verified-jdk8-11.json` final-gate-review SHA-256 `09d10f804393889d29023b28e98c9c93ef9e163df3b7ab8cc50b4eb2d3696e66`
- `capabilities/evidence/artifacts/T29-verified-jdk8-11.txt` final-gate-review SHA-256 `d727830f0292af38dcfe3b7d568acb514a0e54ea64a08ebd6beeddcbf1e83ca4`
- `capabilities/evidence/artifacts/T29-verified-jdk17.json` final-gate-review SHA-256 `b25aaddac659da97d731540ddeaeb4cac7840372cf01576fa106673e28369b28`
- `capabilities/evidence/artifacts/T29-verified-jdk17.txt` final-gate-review SHA-256 `51982869ca142a8cdca32bf275079e118b04a64cd8eec0f687678c3b4db6eb98`
- `capabilities/evidence/artifacts/T29-verified-jdk21.json` final-gate-review SHA-256 `7c576b6bf3b1512fe870fd0f6ca2aa1df4532ead60984f10ed462d0a3206c457`
- `capabilities/evidence/artifacts/T29-verified-jdk21.txt` final-gate-review SHA-256 `8c490e20356f9d766ab282a763dbbe281791899ec2a11ecfd314e5313b539783`
- `capabilities/evidence/artifacts/T29-verified-jdk-images.json` final-gate-review SHA-256 `a33f6937b094ca35411cb532ca7994320d6b731cebb8ac0563374e88aa9a7600`
- `capabilities/evidence/artifacts/T29-verified-workspace-check.json` final-gate-review SHA-256 `72890f977541d6b3cbeb16421029956012a4d8eebfa459fa36f5e96a9d490a6b`
- `capabilities/evidence/artifacts/T29-verified-platform-availability.json` final-gate-review SHA-256 `7a960b8681d97649ace78aa31af03efbfd68594f4ebc216e361d342ff1f4de62`
- `capabilities/evidence/artifacts/T29-linux-run.json` final-gate-review SHA-256 `d86a618d2783443fd7516b3e05fe66cfdee45d753079338c88eebc55e8971d31`
- `capabilities/evidence/T29-delivery.md` final-gate-review SHA-256 `c46fe8c27afa856ae2de9fc71117bc9baf8370d8b3956cebb4f42c70daff90d9`
- `capabilities/evidence/T29-shaping.md` final-gate-review SHA-256 `e2e91982690415849e2b584ab301a8d53351271206d05e2166234211ab709101`
- `PROVENANCE.md` final-gate-review SHA-256 `ee9415eb04a15a6edc56bd29ccb57a9d1db396ff5d1088b357c94768354912d2`
