# Releasing

Folio PDF uses one Release Train version for all first-party modules during
`0.x` and publishes canonical artifacts under `net.zerocloud` to Maven
Central. The Foundation Release is `0.1.0`. During `0.x`, source-breaking
changes require migration notes; from `1.0`, public compatibility follows
semantic versioning.

## Release gates

A formal release requires:

- every capability promised by the release at `compatible` with complete
  Acceptance Evidence;
- every required platform profile passing;
- generated Capability Matrix and Facade Surface documentation;
- Central-ready POMs, binaries, source artifacts, and Javadoc artifacts;
- pinned dependencies, build plugins, and GitHub Actions;
- a CycloneDX SBOM, license report, and known-vulnerability report;
- reproducible-build evidence from two clean builds;
- detached signatures and published checksums;
- a private vulnerability-reporting channel; and
- clean-room provenance for every included contribution and fixture.

The release rehearsal implements the artifact and supply-chain subset of these
gates. It does not certify the Foundation Release or promote a Capability
Matrix entry.

## Local non-publishing rehearsal

Run the one repository-owned command from the repository root with JDK 11 or
newer; JDK 17 is the production workflow version:

```text
./scripts/release-rehearsal target/release-rehearsal 0.1.0
```

The command uses the hash-pinned Maven 3.9.16 wrapper and requires the pinned
GnuPG 2.4.4 and rsync 3.2.7 executables. It creates an isolated temporary GPG
home and a fresh, one-day, non-production RSA test identity. It performs two
clean builds, signs the first, builds the second without signatures, and
compares every deterministic Central entry. OpenPGP signatures and their
derived checksums are the only permitted reproducibility exclusions and are
cryptographically validated separately.

The Central Publisher defaults to `skipPublishing=true`, and the rehearsal also
passes that guard explicitly together with an unreachable loopback base URL.
It also runs with an isolated temporary Maven settings file that contains only
dummy credentials for server id `central`, because Central Publisher still
requires that server entry before it observes the non-publishing guard. The
rehearsal therefore never consults the operator's real Central credentials and
cannot upload even if the operator has a populated Maven settings file. Only
the protected production script explicitly changes the guard to `false`.
Because `skipPublishing=true` intentionally prevents Central Publisher from
creating an upload deployment, the repository release tool assembles
`central-bundle.zip` locally from the signed reactor outputs and validates that
Central-layout bundle before preserving evidence. Production staging still uses
Central Publisher with `skipPublishing=false`, the protected `central` server
credentials, `waitUntil=validated`, and `autoPublish=false`.
Rehearsal requires no Central username, password, production private key, or
production passphrase. The initial unauthenticated NVD import used by OWASP
Dependency-Check can be slow; scanner, feed, or report failures fail the
rehearsal closed.

The output directory contains:

```text
central-bundle.zip
audit/
  build-a.log
  build-b.log
  central-bundle.sha256
  dependency-check-report.{html,json,xml}
  dependency-check-suppressions.xml
  license-report.txt
  rehearsal.properties
  reproducibility.txt
  sbom.{json,xml}
  structural-validation.txt
  test-signing-public-key.asc
```

Revalidate an existing output without rebuilding it with:

```text
./scripts/release-rehearsal validate target/release-rehearsal
```

Validation resolves the checked-in publishable-module authority, rejects
repository-only or unexpected modules, securely parses effective inherited POM
metadata, inspects product JARs, validates every published checksum, imports
only the emitted public test key, and verifies every detached signature. It
also requires the SBOM, license, known-vulnerability, suppression,
reproducibility, build-log, and tool-version evidence.

The vulnerability gate fails at CVSS 7.0 for every required dependency, which
is stricter than limiting the gate to parsing, cryptography, worker isolation,
and required-dependency findings. The checked-in suppression authority is the
only enabled suppression source; Dependency-Check hosted suppressions are
disabled so an external feed cannot bypass ADR-0032. The checked-in file is
empty. Any future exception must identify the advisory and coordinate, expire,
give a technical justification, and link public Lead Maintainer acceptance as
required by ADR-0032. Unused suppression rules fail the build.

## Protected production environment

Before the release workflow can be used, a maintainer must create the GitHub
Environment named `maven-central`. This repository does not create or weaken
that external protection. Configure required reviewers, prevent unreviewed
deployment, and restrict deployment to the intended protected branch or tag
policy. Store only these values as Environment Secrets:

- `CENTRAL_USERNAME`: Central Portal user-token username;
- `CENTRAL_PASSWORD`: Central Portal user-token password;
- `MAVEN_GPG_PRIVATE_KEY`: the armored existing production private key; and
- `MAVEN_GPG_PASSPHRASE`: its passphrase.

Do not create a replacement signing key. The approved primary fingerprint is
`C5149FD6B5EF7C2126F1FD0FCC1A12E348E171D8`; the preflight requires that exact
secret key and performs a detached-signature round trip. Local Central and GPG
state remains outside the repository with owner-only permissions. Never add
generated Maven settings, keyrings, private keys, passphrases, or Central
tokens to the repository.

After the secret-free rehearsal evidence has been preserved, the workflow
invokes:

```text
./scripts/release-preflight RELEASE_VERSION
```

The protected step supplies the passphrase without placing it on the command
line. Preflight rejects a snapshot or malformed version, any GnuPG version
other than 2.4.4, a missing or different secret-key fingerprint, and a failed
detached-signature round trip. It prints the approved fingerprint but never a
key, passphrase, Central token, or generated Maven settings content.

## Stage, validate, then publish separately

The manual `Stage Maven Central release` workflow is the only versioned
production path. It has read-only GitHub contents permission, one serialized
`maven-central-release` concurrency group, full-SHA Action references, and a
job bound to the protected `maven-central` Environment. It runs production
credential-free rehearsal first, preserves its evidence, then imports the
production identity, runs preflight, and uploads a production-signed
deployment.

The Central configuration fixes server id `central`, waits until the deployment
is `validated`, and explicitly keeps `autoPublish` false. Successful workflow
completion means that Central accepted the staged deployment; it does not mean
the artifacts were published. A human must review the Central Portal findings,
the preserved rehearsal evidence, the bundle hash, the exact version, and the
release gates above, then separately choose Publish in the Central Portal. The
workflow never tags, creates a GitHub Release, or performs that irreversible
publication step.
