# T08 secure Maven Central rehearsal evidence

- Ticket: T08 / GitHub issue #9
- Parent release story: GitHub issue #1
- Release train: `0.1.0-SNAPSHOT`
- Rehearsed release version: `0.1.0`
- Execution date: 2026-08-31

## Scope

T08 adds a repository release gate and protected production staging path. It
does not add a PDF behavioral capability, Native Interface member, Acceptance
Profile, stable Migration Facade surface, or unsupported stable stub.

## Rehearsal output

The authoritative local command was:

```text
./scripts/release-rehearsal target/release-rehearsal 0.1.0
```

It produced `target/release-rehearsal/central-bundle.zip` and audit reports
under `target/release-rehearsal/audit/`.

Recorded audit values:

- `source-commit=3faa5b7d5cce9566c520d350651e7ddbcb67d71a`
- `source-worktree-status=dirty`
- `source-tree-sha256=ae8fe93a821afd3049a75b9317a340c3b57c389041b4f80d02a3ecaa4e052e1a`
- `test-signing-fingerprint=378B9A213E771A3AE70D20D7DE149A2EC783BFD2`
- `central-server-id=central`
- `central-credentials=TEMPORARY_DUMMY_REHEARSAL`
- `central-base-url=http://127.0.0.1:1`
- `central-uploaded=false`
- `central-bundle.sha256=14db525272a7473c01b1394ed9b9145c478bffc8137c1fba73118a7f66ba357a`

The `dirty` source status is expected for this implementation session: the
rehearsal ran against the uncommitted T08 working tree. The source-tree digest
records the exact copied source tree used by the clean builds before this
Markdown evidence record and the later glossary-only `CONTEXT.md` update were
added. Those documentation-only records are part of this WIP, but they are not
release artifacts and do not affect the validated Central-layout bundle.

## Bundle validation evidence

`./scripts/release-rehearsal validate target/release-rehearsal` passed after
the clean rehearsal. The structural validator reported:

```text
Release rehearsal bundle validation passed: /home/ubuntu/IdeaProjects/open-pdf/target/release-rehearsal (verified-signatures=24, validated-checksums=192)
```

The bundle contains 240 Central-layout entries:

- 24 primary artifacts;
- 24 detached ASCII-armored OpenPGP signatures;
- 192 checksum files covering every primary artifact and signature with MD5,
  SHA-1, SHA-256, and SHA-512.

Primary artifacts cover the publishable authority in
`release/modules.properties`: `pdf-parent`, `pdf-bom`,
`pdf-provider-contract`, `pdf-document`, `pdf-conversion`,
`pdf-migration-itext7`, and `pdf-migration-itext7-preview`.
Repository-only modules `pdf-acceptance`, `pdf-inventory-tool`, and
`pdf-release-tool` are excluded.

The signed parent coordinate also carries CycloneDX SBOM attachments:

- `pdf-parent-0.1.0-cyclonedx.xml`
- `pdf-parent-0.1.0-cyclonedx.json`

The stable `pdf-migration-itext7` artifact remains resource-only and contains
the `META-INF/folio-pdf/migration-itext7.edition` marker with value `stable`.
It has source and javadoc classifier artifacts for Central completeness, but
no public Java facade classes.

## Security and supply-chain evidence

- The rehearsal generated a fresh one-day non-production GPG identity in an
  isolated temporary home. Only the public key was copied to the audit output.
- The emitted test fingerprint is not the production fingerprint
  `C5149FD6B5EF7C2126F1FD0FCC1A12E348E171D8`.
- Central Publisher ran with `skipPublishing=true`, an unreachable loopback
  base URL, and a temporary Maven settings file containing only dummy server
  id `central` credentials.
- Production staging separately requires the protected GitHub Environment
  `maven-central`, full approved GPG fingerprint, Central server id `central`,
  `waitUntil=validated`, and `autoPublish=false`.
- Pull-request CI has read-only contents permission and no `secrets.`
  references.
- Release workflow Actions are pinned by full commit SHA and the release job is
  serialized by the `maven-central-release` concurrency group.
- OWASP Dependency-Check 12.2.2 emitted HTML, JSON, and XML reports. The
  structural validator found no unresolved high-severity vulnerability element
  in the XML report.
- Hosted Dependency-Check suppressions are disabled; the only suppression
  authority is `release/dependency-check-suppressions.xml`, which is empty.

## Reproducibility evidence

The release tool compared two clean copied worktrees. Build A produced the
signed bundle. Build B skipped tests, GPG signing, and the vulnerability scan
to compare deterministic artifact material without reproducing intentionally
time-dependent signatures.

`target/release-rehearsal/audit/reproducibility.txt` records `result=PASS`.
All deterministic entries matched byte-for-byte by SHA-256. The only
exclusions were OpenPGP signatures and checksums derived from OpenPGP
signature material; the structural validator verified the signatures and
checksums separately.

## Test and validation evidence

The T08 release-tool tests passed:

```text
./mvnw -B -ntp -pl build-tools/release test
```

Those tests assert the externally visible release contract:

- missing bundle failure through the repository command;
- complete bundle validation and all test signatures;
- corruption failures for a missing artifact, invalid signature, invalid
  checksum, incomplete POM metadata, unexpected module, unresolved
  high-severity vulnerability, and missing audit report;
- reproducibility comparison output and OpenPGP-only exclusions;
- Central-layout bundle assembly from signed reactor outputs;
- protected release workflow shape, PR-CI secret isolation, production Central
  configuration, full fingerprint pinning, SHA-pinned Actions, release module
  authority, dependency-check suppression policy, and version placeholder
  policy.

Final repository validation in this T08 session passed:

- `./scripts/inventory validate`
- `./scripts/inventory generate`
- `./scripts/inventory check`
- `./mvnw -B -ntp verify`
- `./scripts/verify-jdk-matrix.sh`
- `bash -n scripts/release-rehearsal scripts/release-preflight scripts/release-central`
- `git diff --check`
- `./scripts/release-rehearsal validate target/release-rehearsal`

The final secret scan found only expected documentation, variable names, and
test strings; no Central credential, passphrase, private key, or token material
was present.
