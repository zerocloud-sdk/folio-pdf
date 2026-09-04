# Capability Providers

Capability Providers are explicitly registered adapters for boundary
capabilities such as OCR, Office conversion, rendering, or native shaping.
The T05 contract does not ship any of those downstream engines. The default
Workflow Environment contains no registration and remains fully offline.

## Artifacts and metadata

Use `net.zerocloud:pdf-provider-contract` for the Provider seam and
`net.zerocloud:pdf-conversion` for the generic subprocess adapter. Both are
managed by `net.zerocloud:pdf-bom` on the shared Release Train.

Every registration has immutable `ProviderMetadata`:

- stable Provider ID and supported capability IDs;
- external engine version;
- `IN_PROCESS`, `NATIVE`, `SUBPROCESS`, or `REMOTE` execution mode;
- `AVAILABLE` or `UNAVAILABLE` registration state;
- maximum request bytes, result bytes, and execution duration;
- engine SPDX identifier and license name; and
- bundled, separately installed, caller-supplied, or remote-service
  distribution.

`REMOTE` execution and `REMOTE_SERVICE` distribution must be declared
together; contradictory metadata is rejected when it is built.

Execution mode is not a Document Workflow execution profile. Either an
in-process or Hardened Worker workflow can broker an eligible Provider in the
parent, while selecting the T21 profile remains a separate isolation decision.

## Registration and selection

`WorkflowEnvironment.builder().provider(provider)` captures registrations in
declaration order. The built environment and the list returned by
`getProviderMetadata()` are immutable and safe to share when every registered
Provider follows the contract's thread-safety requirement.

`ProviderPreference.any(capabilityId)` selects the first available eligible
registration in declaration order. `ProviderPreference.prefer(capabilityId,
providerId)` requires that exact Provider. Repeating selection against the
same immutable environment and request produces the same result. A preference
on `WorkflowRequest` asks `DocumentWorkflow.execute` to validate and report a
selection in `WorkflowOutcome.getProviderSelections()`; T05 does not add a
generic Provider lookup or execution method to Document Session.

## Remote disclosure

Remote disclosure is denied by default. Registration, metadata discovery, and
preference do not authorize network use. A Workflow Request must call
`authorizeRemoteDisclosure(capabilityId)` before a remote Provider can be
selected for that capability. A direct `ProviderRequest` must call
`authorizeRemoteDisclosure()` before remote adapter code can execute.

Missing authorization produces the stable
`REMOTE_DISCLOSURE_NOT_AUTHORIZED` Provider Failure, or the corresponding
Document Failure at the workflow seam. The check runs before Provider adapter
code. Folio PDF registers no default remote Provider and performs no discovery
scan or implicit network call.

## Provider requests and results

`ProviderRequest` and `ProviderResult` contain only project-owned values and
Java 8 platform types. Byte arrays are copied on input and output. Each request
selects one capability, supplies a positive timeout no greater than the
Provider's declared maximum duration, and is checked against the declared
input limit before adapter code runs. Results are checked against the declared
output limit before they return to the caller. Each execution-mode adapter is
responsible for enforcing elapsed time; the shipped subprocess adapter keeps
process and I/O waits inside the request timeout.

Provider Failures use fixed safe diagnostics and retain neither engine or
transport causes nor suppressed exceptions. Checked adapter failures are
rebuilt at the public boundary from their stable code and the registered
Provider identity.

## Subprocess protocol and lifecycle

`SubprocessCapabilityProvider` receives immutable SUBPROCESS metadata, a fixed
JDK `List<String>` command, and a staging root. The command is passed directly
to `ProcessBuilder`; it is never joined into a shell command. Each invocation
gets a private child directory below the staging root, exposed to the child as
`FOLIO_PDF_PROVIDER_STAGING` and removed after every tested exit.

The version 1 stdin request frame is:

1. 32-bit magic `OPDQ` (`0x4f504451`);
2. 32-bit protocol version `1`;
3. Java `DataOutput.writeUTF` capability ID;
4. signed 64-bit payload length; and
5. exactly that many payload bytes.

The stdout response frame is:

1. 32-bit magic `OPDR` (`0x4f504452`);
2. 32-bit protocol version `1`;
3. signed 64-bit payload length; and
4. exactly that many payload bytes followed by end-of-stream.

The adapter reads stdout and discards stderr concurrently so bounded children
cannot deadlock on pipe capacity. It rejects negative, oversized, truncated,
trailing, or wrongly versioned output; terminates a child on output-policy
failure or timeout; requires exit status zero; closes every process stream;
confirms direct-child termination; and removes per-run staging. Failure to
confirm termination or remove owned staging becomes a stable execution
failure. Startup, non-zero exit, crash, malformed output, input/output limit,
and deadline categories are normalized to stable Provider Failures.

This adapter is not the Hardened Worker Profile. It does not claim hard memory,
CPU, filesystem, network, hostile-PDF, pixel, page, object, decompression,
temporary-storage, or concurrency isolation. T20 bounds the surrounding
workflow and the opt-in T21 Hardened Worker supplies the documented hostile-
input process boundary. The Java 8 Provider adapter still supervises its
direct child only; T21 process-tree containment applies to the PDF Worker and
does not retroactively turn this generic integration seam into a sandbox.
