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

**Text Rendering Mode**:
The shared PDF value describing the fill, stroke, invisible, and/or clipping treatment applied to a positioned glyph. The Document Engine observes it and Composition declares it; it is not a font-selection or text-layout policy.
_Avoid_: font style, text effect, paint default

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

**Resource Inventory**:
A detached, declaration-ordered account of document resources reachable from effective page Resources and nested Form resources, including their page usage and backend-neutral identity where the PDF supplies one.
_Avoid_: rendered-content scan, resource cache dump

**Image Resource**:
An image XObject or related image-mask stream described by dimensions, sample metadata, filters, color information, explicit, subsidiary, or embedded masks, and explicitly selected bounded byte data.
_Avoid_: rendered image, BufferedImage, image file

**Page Usage**:
The one-based pages whose effective Resources can reach a resource directly or through nested Forms. It describes declaration reachability, not proof that a page content stream executes the resource.
_Avoid_: rendered usage, content occurrence count

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

**Page Text**:
A detached page-ordered record of text-showing content in content-stream execution order, including geometry and character-mapping evidence. It is not inferred reading order, layout reconstruction, or OCR output.
_Avoid_: plain text, reading order, OCR text

**Character Mapping Confidence**:
The explicit strength of the evidence connecting a PDF character code to Unicode: explicit, inferred from a standard mapping, contradictory, or missing. Contradictory and missing mappings remain uncertain rather than becoming guessed characters.
_Avoid_: encoding quality, guessed character

**Marked Content**:
Page content bracketed by PDF marked-content operators and identified by a tag and optional marked-content identifier. Its association with Logical Structure is explicit document data, not implied by operator nesting alone.
_Avoid_: tag node, structure element

**Logical Structure**:
The Tagged PDF hierarchy of structure elements and ordered content references, with role resolution, language inheritance, alternate text, and replacement text kept distinct from Page Text.
_Avoid_: document outline, bookmark tree

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

**PDF Version**:
An exact major/minor PDF specification declaration. A document has a required header version, an optional catalog version, and an effective version equal to the later supported declaration.
_Avoid_: backend float version, parser default

**PDF Output Policy**:
The immutable, request-scoped version and password-security choices applied to every product published by one Document Workflow.
_Avoid_: writer setting, global output default

**Existing Signature**:
A structurally recognized signature value already present in a Source. It is protected whether or not Folio PDF has established its cryptographic validity.
_Avoid_: valid signature, verified signature

**Signature Permission**:
The narrow mutation authority established from every Existing Signature and every applicable document-modification restriction. Missing, contradictory, malformed, or unsupported evidence grants no authority.
_Avoid_: signature validity, signer permission

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

**Canvas Program**:
A versioned, immutable sequence of explicitly positioned path, graphics-state, and text instructions for one page, with every external resource declared by a project-owned reference. It is document data, not caller code or an unvalidated PDF content stream.
_Avoid_: drawing callback, raw content stream, backend canvas

**Canvas Font**:
An explicit Canvas Program declaration that identifies an existing Font resource in the current Document Session. It positions already encoded glyphs but does not discover, load, embed, subset, map, or fall back fonts.
_Avoid_: default font, system font, font loader

**Canvas Color Space**:
An immutable Canvas Program declaration for one supported device, calibrated, or ICC-based color interpretation, reusable by colors, images, and transparency groups.
_Avoid_: backend color space, color profile handle

**Canvas Image**:
An immutable Canvas Program declaration for bounded encoded image input, raw samples, or a borrowed existing Image Resource, together with any explicit or soft mask relationship.
_Avoid_: BufferedImage, image file, image callback

**Canvas Transparency State**:
An immutable Canvas Program declaration of fill alpha, stroke alpha, and a supported blend mode that can be reused across drawing instructions.
_Avoid_: opacity flag, backend graphics state

**Canvas Transparency Group**:
An immutable bounded Canvas Program isolated as a reusable PDF transparency group with an explicit box, color space, isolation choice, and knockout choice.
_Avoid_: layer, page canvas, arbitrary Form stream

**Foundation Composition Profile**:
The Foundation Release contract covering full paragraph and table behavior selected for the first release, representative major writing systems, and every barcode generation mode in the Reference Suite core.
_Avoid_: basic layout, experimental layout, simple text support

**Reference Font Set**:
An immutable, declaration-ordered set of explicitly supplied reusable Font Sources. A Foundation Reference Font Set additionally pins the approved Noto versions and hashes used to make Foundation typography evidence reproducible across certified platforms.
_Avoid_: system font, online font, default font search

**Font Source**:
An explicit caller-supplied byte array, path, stream, or channel from which one font program is staged under declared ownership and limits. It is never an installed-font lookup, URI, or backend font object.
_Avoid_: system font, font URL, backend font

**Positioned Unicode Text**:
An unshaped Unicode scalar sequence placed with an explicit text matrix, size, Text Rendering Mode, and deterministic Font Source selection. It is not bidi, shaping, line breaking, or paragraph layout.
_Avoid_: paragraph, text layout, encoded glyph run

## Forms

Owns interactive fields, form data, appearances, AcroForm behavior, and XFA processing.

## Trust

Owns document confidentiality, authenticity, integrity, timestamps, trust chains, and signature validation.

**Password Credential**:
A caller-owned, destroyable defensive copy of password characters used to authenticate one Source or define one output owner/user role. It is never a public String.
_Avoid_: password string, encryption key

**Password Security Policy**:
The immutable owner credential, user credential, encryption algorithm, encryption scope, and Document Permissions requested for protected published products.
_Avoid_: password flag, backend protection policy

**Password Encryption Scope**:
The declared coverage of password encryption: all content, all content except document-level metadata, or embedded files only. Modeling a scope does not imply that every backend can write it.
_Avoid_: encryption boolean, metadata permission

**Document Permissions**:
The eight Standard-handler user permission choices represented by the PDF permission word and enforced by the Document Workflow after authentication and before mapped operations.
_Avoid_: DRM, owner rights, Signature Permission

**Credential Authority**:
The detached authority established while opening a document: none, restricted user, independently proven owner, or unrestricted permission without owner proof.
_Avoid_: valid password, access level

**Legacy Security Mode**:
An explicit request-scoped opt-in that permits supported obsolete password-encryption output for migration needs. It is absent by default, selects no algorithm by itself, and cannot alter another Document Workflow.
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
