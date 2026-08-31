# Security model

Folio PDF treats every PDF as potentially malicious. Public limits cover input size, pages, objects, nesting, decoded streams and pixels, memory, temporary storage, processing time, and concurrency; limit failures use stable Document Failure codes.

## Execution profiles

- In-process execution is for trusted desktop and controlled batch workloads.
- The Hardened Worker Profile is mandatory for hostile multi-tenant uploads. It uses a local-only, versioned protocol, explicit resource limits, no arbitrary user code, and no network by default.
- External Capability Providers are separately installed adapters. Remote disclosure of document data requires explicit caller authorization.

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
remains T21 scope.

## Sensitive data

Transaction temporary data is isolated and removed with the worker. Passwords and private keys are not accepted as Strings or written to logs. Default diagnostics omit document content, names, metadata, credentials, private keys, and raw backend failures.

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
