# Retained development failures

Every attempt below is historical and unindexed. Original transcripts,
results, raw observation trees and the applicable candidate archives remain
available. A later passing experiment does not relabel an earlier failure.

| Attempts | Diagnosis and disposition |
| --- | --- |
| [focused-worker-entry-r1](validation/focused-worker-entry-r1.txt), [single-worker-host-r1](validation/single-worker-host-r1.txt) | The copied Java installation was initially under `/tmp`, also the configured transaction parent. Runtime reads were denied by the existing root policy. The same executable bytes passed in the declared Ubuntu image and after relocation outside that parent; the clean sequential Ubuntu focused suite passed. Shipping policy and inventories were preserved. Original Surefire reports are in `validation/focused-worker-entry-r1-reports/`. |
| [focused-worker-ubuntu-r1](validation/focused-worker-ubuntu-r1.txt) | Concurrent development Maven builds changed shared outputs and produced a missing Provider class. This was an invalid build attempt. The clean sequential focused run passed 125 tests with zero failures/skips. Subsequent Maven builds are serialized. |
| [t21-recorder-compile-r1](validation/t21-recorder-compile-r1.txt), [r2](validation/t21-recorder-compile-r2.txt), [focused-r4](validation/t21-recorder-focused-r4.txt) | Acceptance implementation compile errors: the cancellation token is not a functional interface, a witness helper needed checked I/O handling, and the resource-policy accessor name was wrong. Corrected before staging; focused runner tests pass. No shipping source changed. |
| [inventory-focused-r1](validation/inventory-focused-r1.txt), [inventory-validate-development-r1](validation/inventory-validate-development-r1.txt) | Proposed promotion used an unsupported status and retained an open promotion gate. Updated only Worker capability/profile to the established compatible status and removed its completed gate; retained its declared dependencies and all downstream statuses. Generated authorities validate. These were inventory failures, not accepted certifications. |
| [t21-collector-focused-r3](validation/t21-collector-focused-r3.txt), [collector-suite-r1](validation/collector-suite-r1.txt) | A synthetic test mutation still used the old receipt serialization; the tool-catalog expectation omitted the new T21 project producer. Updated the meaningful guard expectations. The fresh full collector suite subsequently passes. |
| [JDK17 production rehearsal r1](validation/t21-production-rehearsal-jdk17-r1.txt) | 134 of 137 mandatory cases passed. Two existing isolation positives assumed exploded test classes and repacked a staged JAR into an empty archive; the acceptance policy positive used a path exceeding the Unix socket length bound. The test-only archive helper now handles exact JAR bytes, and the separate policy positive uses a short private socket path. The staged candidate for this attempt is in `development-candidate-r2/`; all failed raw records remain in `../foundation/T82-development-r1/`. |
| [JDK17 production rehearsal r2](validation/t21-production-rehearsal-jdk17-r2.txt) | All 137 original cases, the unavailable-prlimit control and actual public PDF recording passed. The ticket-local helper lacked the scripts import path when dispatching collection. Corrected that helper; the actual CLI collection then passed fresh tests, replay and independent original-product validation. Candidate bytes remain in `development-candidate-r3/`, raw records in `../foundation/T82-development-r2/`. |
| [JDK8 production rehearsal r1](validation/t21-production-rehearsal-jdk8-r1.txt) | All 137 original cases, prerequisite control and public product recording passed. Collection correctly rejected a vendor comparison that conflated release IMPLEMENTOR (`Eclipse Adoptium`) with actual `java.vendor` (`Temurin`). Both actual properties are now independently observed and retained, while exact release/image/build/executable authority stays unchanged. No historical observation was relabeled. Candidate bytes remain in `candidate-source-freeze-r1/`, raw records in `../foundation/T82-development-jdk8-r1/`. |
| [inventory-worker-witness-focused-r1](validation/inventory-worker-witness-focused-r1.txt) | The negative fixture was correctly rejected, but its assertion expected an invented hash-error phrase. Corrected the assertion to the validator's existing `expected a SHA-256 identity` diagnostic. The fresh focused witness/control test passes. |
| [JDK8 production rehearsal r2](validation/t21-production-rehearsal-jdk8-r2.txt) | Observation stopped before test execution: trimming an entire JVM settings line removed the trailing delimiter when its value was empty. Reused the existing flat-property reader and trimmed keys/values separately; a synthetic integration fixture exercises the actual observer with empty settings, multiline paths and distinct release/runtime vendors. Its focused suite passes. Original raw settings remain in `../foundation/T82-development-jdk8-r2/`; its complete source/artifact archive remains in `candidate-final-r1/`. A new freeze and stage are required. |

The inherited Podman index referenced absent layer payloads. Provisioning used
a new isolated VFS store and pulled the exact declared digests; it did not reset,
prune or modify the inherited storage. Provisioning logs are retained in
`validation/provision-jdk17-image.txt` and `validation/provision-other-images.txt`.

The final predecessor refresh stopped at `password-attachments` after the
delivery wrapper's 10,800-second cap. Its overall attempt remains failed in
[the original ledger](refresh-final-r1/results.json); the original wrapper,
log and [timeout diagnosis](validation/refresh-final-r1-password-attachments-timeout.json)
are retained. No mandatory test or checker failure was reported before that
interruption. The verified owned JDK21 recorder container was stopped, and its
partial output is excluded from reuse. The continuation uses a 21,600-second
delivery budget, without changing the Worker policy, certification commands,
staged source/artifact/harness bytes, tools or profiles. It strictly checks all
original/transitive identities of the six completed JDK8/11/17 tuples,
reobserves their actual environments, and executes both JDK21 tuples in fresh
directories. Seven mutation controls guard the reuse check. The continuation
ledger and reuse receipt record the actual outcome; no failed attempt is
relabeled as passing.

These failures demonstrated setup or repository-only acceptance/tooling gaps.
They did not establish a required Worker product behavior failure. Final evidence
must come from the frozen, freshly staged candidate and complete live certification.
