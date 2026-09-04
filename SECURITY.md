# Security model

Folio PDF treats every PDF as potentially malicious. Public limits cover input size, pages, objects, nesting, decoded streams and pixels, memory, temporary storage, processing time, and concurrency; limit failures use stable Document Failure codes.

## Execution profiles

- In-process execution is for trusted desktop and controlled batch workloads.
- The opt-in Hardened Worker Profile is the T21 boundary for hostile
  multi-tenant uploads on its supported Linux/JDK envelope. It uses a
  separately limited local JVM, authenticated length-bounded pipes, a closed
  Command/Query schema, private transaction files, hard parent termination,
  an inventory/hash-checked dependency-only runtime class path, contained
  process creation, and denied INET and Unix-domain network access. The
  original environment Clock and deadline remain caller-thread-enforced; an
  independent monotonic watchdog hard-stops an overlong Worker and observes
  the explicitly thread-safe cancellation token. It never falls back to
  in-process execution,
  and inability to remove its private transaction root prevents a successful
  outcome.
- External Capability Providers are separately installed adapters. Remote disclosure of document data requires explicit caller authorization.

The current Worker requires Linux `/usr/bin/prlimit` and a JDK 8, 11, 17, or
21 runtime with the legacy Java Security Manager available. The project makes
no certified-platform, whole-process RSS/native-memory, physical secure-
erasure, arbitrary-bytecode sandbox, or post-crash retry claim. See the
[Hardened Worker guide](docs/hardened-worker.md) before production deployment.
The launcher also requires separate, unshaded Folio PDF production artifacts
or project-only exploded production class directories; it refuses mixed
application or test code sources instead of adding them to the Worker class
path. First-party class-name inventories and exact dependency-JAR hashes are
recorded in the [Hardened Worker guide](docs/hardened-worker.md) and
[dependency record](DEPENDENCIES.md). Classpath paths containing a platform
delimiter or JVM wildcard syntax are also refused.

## Capability Providers

The default Workflow Environment has no Provider registrations, so the core
cannot select a remote service or perform implicit network access. Provider
registration and preference are not disclosure permission. A caller must
authorize remote disclosure for the same capability on each immutable
Workflow Request or Provider Request; the contract rejects the request before
remote adapter code runs when authorization is absent.
Provider metadata also rejects any mismatch between `REMOTE` execution and
`REMOTE_SERVICE` distribution so contradictory declarations cannot bypass the
authorization boundary.

Provider metadata declares maximum input bytes, output bytes, and execution
duration. The common contract enforces byte bounds and validates that a
request timeout does not exceed that maximum; every execution-mode adapter is
responsible for enforcing elapsed time. The generic subprocess adapter checks
input before launch, keeps process and I/O waits deadline-bounded, reads only
the bounded framed result, confirms direct-child termination on timeout or
policy failure, discards stderr, and removes its private transaction staging
directory on all tested exits. Failure to confirm termination or remove owned
staging becomes a stable execution failure. Its fixed command does not use
shell expansion. Stable Provider Failures contain only code-owned diagnostics,
retain no raw engine or transport cause or suppressed exception, and rebuild
checked adapter failures with the registered Provider identity.

The subprocess adapter is an integration seam, not the Hardened Worker
Profile. It does not enforce the comprehensive memory, CPU, filesystem,
network, decoded-pixel, page, object, nesting, decompression, temporary-storage,
or concurrency controls required for hostile multi-tenant input.
It also does not contain descendant process trees; that hard-isolation boundary
is supplied only by the separate T21 Hardened Worker profile, not by the
generic Provider adapter.

## Sensitive data

Transaction temporary data is isolated and removed with the worker. Passwords
and private keys are not accepted as public Strings or written to logs.
`PasswordCredential` immediately copies caller characters, returns no array,
and idempotently overwrites its owned array on close. A workflow uses separate
execution-local copies, clears every project-owned temporary array on success
and failure, and never closes the caller's credential. Destroyed credentials
fail before caller work and publication.

The Hardened Worker initialization message carries password-security
descriptors and credential-presence flags, never credential characters. The
child requests required Source, output-owner, and output-user credentials in
protocol order through authenticated on-demand messages. Every character and
protocol-buffer copy is charged to the aggregate owned-memory policy and
cleared at its transfer boundary; child-local destroyable credentials close at
transaction teardown. The parent clears the descriptor-only initialization
payload immediately after authenticated `READY`. Normal active-context failure
envelopes are also accounted. Bootstrap and post-context failures have no live
transaction ledger and use only a fixed eight-byte allowlisted descriptor,
independently bounded by the message limit like the fixed eight-byte post-
context finished control. The same bounded codec is permitted as an emergency
exception path
when a primary failure has already exhausted or poisoned an active modeled
budget, so the stable cause is not silently replaced by connection loss. Fixed
12-byte authenticated memory reserve, release, and grant payloads remain outside
the ledger to prevent recursive accounting; their length and message bound are
checked before allocation.

Apache PDFBox's public password loader and protection policy require temporary
immutable Java `String` values. Folio PDF minimizes their lifetime and closes
the containing document, but cannot erase backend or JVM String copies and
makes no physical secure-erasure claim. Output rejects empty, equal,
non-printable-ASCII, or over-limit credentials rather than relying on backend
fallback, normalization, or truncation. A missing credential is never treated
as an empty password.

Default diagnostics omit document content, names, metadata, credentials,
private keys, secret-derived values, and raw backend failures. Acceptance
evidence supplies encrypted products to qpdf through a temporary password file,
redacts password-valued tool output and the temporary path, and deletes the
file before the command returns. Evidence hashes exclude randomized
credential-derived entries and ciphertext and do not expose private security
state.

## Vulnerability handling

Until a dedicated private reporting service is established, report suspected
vulnerabilities to **mabaiqiu@gmail.com** with the subject `Folio PDF security`.
Do not open a public issue or include document content, credentials, private
keys, or exploit details in public discussion. The maintainer will acknowledge
receipt and coordinate a private follow-up channel.

Formal releases require an SBOM, dependency and license review, vulnerability
scanning, reproducibility evidence, artifact signatures, and checksums. A
high-severity parsing, cryptography, isolation, or required-dependency
vulnerability blocks release unless the Lead Maintainer publishes an explicit
security exception.

## Release supply-chain gate

Pull-request CI has read-only repository contents permission and contains no
Central or GPG secret reference. Production credentials are referenced only by
the manually dispatched release job, which is bound to the externally managed
`maven-central` GitHub Environment and serialized with every other release
execution.

The local release rehearsal generates a new isolated, explicitly
non-production signing identity on every invocation. Its public key and full
fingerprint are evidence; its temporary private keyring is deleted and never
enters the repository or output bundle. The rehearsal also supplies Central
Publisher with an isolated temporary Maven settings file containing dummy
server id `central` credentials, never the operator's real Central username or
password. Production staging separately requires the approved full fingerprint
`C5149FD6B5EF7C2126F1FD0FCC1A12E348E171D8` and the protected Environment
secrets documented in [RELEASING.md](RELEASING.md).

OWASP Dependency-Check 12.2.2 scans the selected Release Train dependencies
against current public vulnerability data and emits HTML, JSON, and XML
reports. A scanner error or a finding at CVSS 7.0 or above fails the release.
This blocks every unresolved high-severity required-dependency finding and
therefore covers the parsing, cryptography, and worker-isolation categories in
ADR-0032. The public suppression authority is
`release/dependency-check-suppressions.xml`; hosted suppressions are disabled,
the checked-in file is currently empty, and unused rules are errors. A future
suppression is invalid without an advisory, coordinate, expiry, technical
justification, and linked public Lead Maintainer acceptance.

The Central staging configuration never auto-publishes. It waits for Central
validation and leaves the immutable publication decision to a separate human
review. Rehearsal logs and reports contain no production credential or private
key material.
