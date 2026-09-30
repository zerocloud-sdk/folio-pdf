# Retained text timeout diagnosis

The first text certification attempt stopped on three timing failures in the JDK 8 hardened-worker contract suite. Its original evidence remains unchanged under `T79-final/text/`; it was not added to the certification index.

The exact staged suite, artifacts, pinned environment, execution mode, and time limits were preserved. A focused replay passed all three methods. The complete 126-test suite then passed in 317 seconds under the original 600-second command timeout. Ten repeats of each of the two short methods passed (20 executions). Current source, artifact, harness, Java executable, and OS identities match the frozen candidate.

The original failure did not reproduce. A transient runtime delay is consistent with the observations, but its exact cause was not established. No product or test source was changed, and no timeout or evidence gate was relaxed. These diagnostic runners are retained here to make the replay reproducible.

`disposition.json` binds commands, raw logs, results, identity checks, hypotheses, and their limits. A fresh complete text route at `T79-final/text-r2/` is still required; these diagnostic runs do not substitute for certification.

Commands: `python3 capabilities/evidence/T79-delivery/validation/diagnosis-text-timeouts/replay.py 1 unrelatedCMapMetadataDoesNotConsumeMappingEntries textualToUnicodeInheritanceResolvesTheDeclaredEmbeddedParent everyStandardPredefinedEncodingAcceptsItsIndependentlyCountedBudget`; `python3 capabilities/evidence/T79-delivery/validation/diagnosis-text-timeouts/full-replay.py`. Set a new `T79_REPLAY_LABEL` for an additional run so historical logs cannot be overwritten.
