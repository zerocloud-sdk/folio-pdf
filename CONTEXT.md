# Folio PDF Domain Context

This document defines shared language for the complete product program and collects the ownership and vocabulary of its eight capability contexts in one place.

ADR-0014 continues to define the capability contexts as distinct architectural concepts. This single-file layout changes only where their domain documentation is stored.

## Program Language

**Folio PDF**:
The public name of this independent project. Public discovery and migration material uses the disambiguated form “Folio PDF by ZeroCloud” and states that the project is unrelated to LibrePDF OpenPDF or Apryse iText.
_Avoid_: OpenPDF, iText Folio PDF

**Native Interface**:
The primary public interface of this project, designed around its own model rather than around compatibility with historical iText signatures.
_Avoid_: native API, compatibility interface, iText API

**Foundation Release**:
Version `0.1.0`, the first usable release, covering PDF creation and persistence, manipulation and extraction, the full Foundation Composition Profile, page rendering, password encryption, recognition and protection of existing signatures, and matching Migration Facade coverage. Every required capability and certified platform must be `compatible` before release.
_Avoid_: skeleton release, Core parity, production-complete release

**Dependency Gate**:
A Capability Matrix prerequisite that must be `compatible` before dependent work can claim compatibility. Only verified technical dependencies are hard gates; sequencing preferences remain advisory.
_Avoid_: phase date, team preference, issue order

**Release Train**:
One version shared by every first-party module and coordinated through the BOM. During `0.x`, all modules move together even when contexts progress independently.
_Avoid_: add-on version, independent module release

**Release Gate**:
A mandatory evidence checkpoint for a formal release. A failed Release Gate blocks the Release Train unless the documented exception authority accepts the risk publicly.
_Avoid_: best-effort check, advisory validation

**Release Rehearsal**:
A non-publishing proof that a Release Train can be assembled and validated with release-shaped metadata, artifacts, signatures, checksums, and evidence without production credentials or Central publication.
_Avoid_: staging release, dry-run publish, mock release

**Central Staging**:
The protected Maven Central handoff that submits a Release Train for Central validation while leaving publication as a separate human decision.
_Avoid_: automatic publication, release rehearsal, publish step

## Document Engine

Owns the logical PDF document, its pages and resources, and the lifecycle by which a document is read, changed, and published.

**Document Workflow**:
The reusable Native Interface entry point that executes one isolated document transaction and owns its resource, error, and publication lifecycle.
_Avoid_: document service, PDFBox wrapper

**Document Session**:
The thread-confined interaction scope supplied inside a Document Workflow. Queries observe earlier changes in the same session, and the session is invalid after the workflow returns.
_Avoid_: PDF document object, backend document

**PDF Value**:
A backend-neutral representation of a low-level PDF null, boolean, number, string, name, array, dictionary, stream, or indirect reference.
_Avoid_: COS object, PdfObject, backend value

**Object Reference**:
A stable, backend-neutral identity for one indirect PDF object inside a Document Session. It is not a live mutable handle and cannot be dereferenced after its Session ends.
_Avoid_: object pointer, COS reference

**Document Patch**:
An ordered request to inspect or change low-level PDF Values through validation owned by the Document Engine.
_Avoid_: direct object mutation, COS edit

**Document Command**:
A versioned, project-defined request that changes the current Document Session. Commands are ordered and batchable and never contain caller code or backend objects.
_Avoid_: callback, remote method, custom command class

**Document Query**:
A versioned, project-defined request for information from the current Document Session. A query is an ordering barrier that observes every earlier Document Command.
_Avoid_: live view, direct backend query

**Annotation**:
A page-associated interactive or descriptive PDF object with project-owned geometry, identity, and subtype data.
_Avoid_: comment object, backend annotation

**Annotation Appearance**:
The resource-free normal visual program for an Annotation, expressed independently of its interactive behavior.
_Avoid_: annotation renderer, arbitrary content stream

**Navigation Target**:
A destination within the same document, identified either by an explicit page view or by a named destination.
_Avoid_: URL, external destination, executable target

**GoTo Action**:
Inert document data that binds an event to one local Navigation Target. A GoTo Action is never executed by Folio PDF.
_Avoid_: callback, script, command execution

**Annotation Flattening**:
The irreversible incorporation of a non-form Annotation Appearance into page content followed by removal of that Annotation.
_Avoid_: form flattening, annotation deletion

**Save Mode**:
The explicit publication strategy for a changed document: `REWRITE` serializes a replacement document, while `INCREMENTAL` appends a new revision under stricter preservation rules.
_Avoid_: auto-save, smart save

**Publication Receipt**:
The result of publishing one workflow's outputs, recording whether each destination was committed, failed, or not attempted. It does not imply a transaction spanning multiple destinations.
_Avoid_: transaction result, global commit

**Hardened Worker Profile**:
The process-isolated execution profile required for hostile multi-tenant input, with enforced memory, CPU, time, temporary-storage, and network limits.
_Avoid_: safe mode, background thread

**Document Failure**:
A checked operational failure with a stable code, capability identifier, and safe diagnostics. It never exposes a backend or worker implementation exception as its public contract.
_Avoid_: PDFBox exception, generic runtime exception

## Composition

Owns how semantic content becomes positioned page content, including layout, typography, graphics, and barcodes.

**Foundation Composition Profile**:
The Foundation Release contract covering full paragraph and table behavior selected for the first release, representative major writing systems, and every barcode generation mode in the Reference Suite core.
_Avoid_: basic layout, experimental layout, simple text support

**Reference Font Set**:
The versioned, hash-pinned static Noto fonts used to make Foundation typography evidence reproducible across certified platforms.
_Avoid_: system font, online font, any Noto version

## Forms

Owns interactive fields, form data, appearances, AcroForm behavior, and XFA processing.

## Trust

Owns document confidentiality, authenticity, integrity, timestamps, trust chains, and signature validation.

**Legacy Security Mode**:
An explicit opt-in that permits writing obsolete Reference Suite password-encryption algorithms for migration needs. It is never selected by default.
_Avoid_: compatible encryption, default encryption

**FIPS Distribution**:
A future Trust distribution tied to an explicitly certified cryptographic provider, JVM, platform, and approved operating mode, with no ordinary-provider coexistence or silent fallback.
_Avoid_: FIPS-compatible algorithm, FIPS mode flag

## Conformance

Owns claims and evidence that documents satisfy archival, accessibility, and related PDF standards.

**Reference Conformance Set**:
The Reference Suite baseline comprising PDF/A-1A/1B, PDF/A-2A/2B/2U, PDF/A-3A/3B/3U, and assisted PDF/UA-1 creation. It excludes PDF/A-4, PDF/UA-2, and a public PDF/UA validator.
_Avoid_: all PDF/A, full PDF/UA, current conformance

**Creation Check**:
A check performed while constructing or changing a document to prevent known conformance violations. It is not independent proof that the finished document conforms.
_Avoid_: validator, certification

**Independent Validation**:
Acceptance Evidence produced against a finished document by tools and review independent of the implementation path.
_Avoid_: creation check, self-validation

## Conversion

Owns transformations between PDF and external representations such as HTML, SVG, images, recognized text, and office documents.

**Capability Provider**:
A replaceable implementation of a conversion capability. Providers may use different local or remote technologies while the default distribution remains usable offline.
_Avoid_: built-in converter, mandatory cloud service

**Provider Registration**:
An immutable Workflow Environment entry that makes one Capability Provider eligible for deterministic selection. Registration does not imply that its external engine is installed, available, or authorized to receive document content.
_Avoid_: plugin scan, service locator entry

**Provider Preference**:
A capability-scoped caller choice that either accepts the first eligible registered Provider or names one preferred Provider explicitly.
_Avoid_: backend hint, global provider default

**Provider Execution Mode**:
The location and mechanism used by a Capability Provider engine: in-process Java, local native linkage, a local subprocess, or a remote service. It is distinct from the execution profile of the enclosing Document Workflow.
_Avoid_: workflow profile, backend type

**Provider Operating Limits**:
The immutable input, output, and maximum-duration policy declared for one Capability Provider. The common contract enforces byte bounds and validates the requested timeout; each execution-mode adapter is responsible for enforcing elapsed time, and the generic subprocess adapter does so for its direct child.
_Avoid_: best-effort limits, unlimited provider

**Remote Disclosure Authorization**:
An explicit, capability-scoped caller permission to send request content to a remote Capability Provider. It is absent by default and never inferred from Provider registration or preference.
_Avoid_: remote opt-in default, network permission

## Sanitization

Owns irreversible removal of sensitive content and transformations that reduce or optimize document resources.

## Migration

Defines and measures migration from the fixed iText 7.2.6-era product set to Folio PDF.

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
