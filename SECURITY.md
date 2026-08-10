# Security model

Open PDF treats every PDF as potentially malicious. Public limits cover input size, pages, objects, nesting, decoded streams and pixels, memory, temporary storage, processing time, and concurrency; limit failures use stable Document Failure codes.

## Execution profiles

- In-process execution is for trusted desktop and controlled batch workloads.
- The Hardened Worker Profile is mandatory for hostile multi-tenant uploads. It uses a local-only, versioned protocol, explicit resource limits, no arbitrary user code, and no network by default.
- External Capability Providers are separately installed adapters. Remote disclosure of document data requires explicit caller authorization.

## Sensitive data

Transaction temporary data is isolated and removed with the worker. Passwords and private keys are not accepted as Strings or written to logs. Default diagnostics omit document content, names, metadata, credentials, private keys, and raw backend failures.

## Vulnerability handling

Until a dedicated private reporting service is established, report suspected
vulnerabilities to **mabaiqiu@gmail.com** with the subject `Open PDF security`.
Do not open a public issue or include document content, credentials, private
keys, or exploit details in public discussion. The maintainer will acknowledge
receipt and coordinate a private follow-up channel.

Formal releases require an SBOM, dependency and license review, vulnerability
scanning, reproducibility evidence, artifact signatures, and checksums. A
high-severity parsing, cryptography, isolation, or required-dependency
vulnerability blocks release unless the Lead Maintainer publishes an explicit
security exception.
