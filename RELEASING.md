# Releasing

Open PDF uses one Release Train version for all first-party modules during `0.x` and publishes canonical artifacts under `net.zerocloud` to Maven Central.

## Release gates

A formal release requires:

- every capability promised by the release at `compatible` with complete Acceptance Evidence;
- every required platform profile passing;
- generated Capability Matrix and Facade Surface documentation;
- source and Javadoc artifacts;
- pinned dependencies and build plugins;
- a CycloneDX SBOM, license report, and known-vulnerability report;
- reproducible-build evidence;
- signed artifacts and published checksums;
- a private vulnerability-reporting channel;
- clean-room provenance for every included contribution and fixture.

The Foundation Release is `0.1.0`. During `0.x`, source-breaking changes require migration notes; from `1.0`, public compatibility follows semantic versioning.
