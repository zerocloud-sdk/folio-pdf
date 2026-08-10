# Stabilize public interfaces at version 1

During `0.x`, public interfaces may change between minor releases under a documented migration policy so the Foundation Release can teach the project what its model must be. A Stable Migration Facade in `0.x` contains only behaviorally `compatible` mappings but may still make documented source-breaking changes. From `1.0`, semantic versioning governs public compatibility; the facade remains fixed to the iText 7.2.6 Reference Suite rather than tracking later iText majors.
