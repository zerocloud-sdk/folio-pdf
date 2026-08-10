# Open PDF Program

This context defines language shared by the complete product program. Capability-specific language belongs to the contexts in `CONTEXT-MAP.md`.

## Language

**Open PDF**:
The public name of this independent project. Public discovery and migration material uses the disambiguated form “Open PDF by ZeroCloud” and states that the project is unrelated to LibrePDF OpenPDF or Apryse iText.
_Avoid_: OpenPDF, iText Open PDF

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
