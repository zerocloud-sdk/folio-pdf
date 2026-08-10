# Migration

This context defines and measures migration from the fixed iText 7.2.6-era product set to Open PDF.

## Language

**Reference Suite**:
The fixed Compatibility Matrix product set comprising iText Core 7.2.6, pdfSweep 3.0.2, pdfCalligraph 3.0.2, pdfXFA 3.0.4, pdfHTML 4.0.5, pdfOCR 2.0.2, pdfOptimizer 2.0.2, pdfRender 2.0.4, and pdfOffice 2.0.5. “Complete” refers to this matrix-defined Java suite, not every separate Apryse product or tool from the same era.
_Avoid_: iText 7, current iText, Core

**Capability Parity**:
The eventual state in which every agreed capability of the Reference Suite has a supported counterpart. It does not mean unchanged imports, binary compatibility, or byte-identical output.
_Avoid_: identical, clone, 100% compatible

**Migration Facade**:
An optional public interface that maps familiar iText 7-era concepts and call patterns onto the Native Interface. It reduces source-migration effort but does not preserve imports or binary linkage.
_Avoid_: drop-in replacement, native interface

**Behavioral Parity**:
Equivalent document semantics together with pagination, geometry, and rendered appearance inside explicitly defined tolerances. It does not require identical internal exceptions, performance characteristics, or bytes.
_Avoid_: identical behavior, identical output

**Capability Matrix**:
The machine-readable source of truth mapping every Reference Suite capability to its Native Interface, Migration Facade coverage, limitations, acceptance evidence, and status.
_Avoid_: feature list, roadmap, issue tracker

**Facade Surface Manifest**:
The machine-readable inventory of Migration Facade types, constructors, methods, generic contracts, constants, and exception mappings. It complements rather than replaces behavioral capability evidence.
_Avoid_: capability matrix, generated Javadoc

**Acceptance Profile**:
The capability-specific semantic, structural, geometric, visual, and conformance checks used to decide whether an implementation is compatible.
_Avoid_: global pixel threshold, manual approval

**Stable Migration Facade**:
The migration artifact containing only mappings whose Capability Matrix status is `compatible`. During `0.x`, “stable” describes evidence status; minor releases may still make documented source-breaking changes.
_Avoid_: complete facade, compatibility stubs

**Experimental Migration Facade**:
A mutually exclusive preview artifact containing the Stable Migration Facade plus mappings that are still `experimental`, never silently included in the stable artifact.
_Avoid_: stable facade, preview hidden in stable

**Acceptance Evidence**:
Versioned proof produced by independent syntax, standards, semantic, visual, or required human checks for one Acceptance Profile.
_Avoid_: golden PDF, tool exit code, maintainer opinion

**Compatibility Curator**:
The authorized role that produces legally approved black-box differential evidence for closed Reference Suite products and gives implementers only neutral Acceptance Profiles and project-owned fixtures.
_Avoid_: reverse engineer, implementation maintainer
