# T04 lifecycle mappings, promoted by T70

The 12 lifecycle entries originally introduced in Preview now belong to Stable.
`pdf-migration-itext7` and `pdf-migration-itext7-preview` compile the same first-party
sources and public consumer tests, with separate edition markers and unchanged
Automatic-Module-Name values. Preview includes every Stable mapping; equality
is allowed. No additional mapping or stub was added.

`BlankDocumentFacadeTest` covers publication, reopen/page count, safe Native failure
mapping, reader Path release, layout close ownership, closed-state rejection and
idempotent close after both success and failure. `JarContractIT` loads each actual
jar separately, compares constructors and methods to its own Facade Surface
Manifest entries, and checks exact types, returns, generics, exceptions, Java 8
bytecode, module names and license resources. `ClasspathExclusivityIT` starts
separate JVMs for all six public types in both jar orders and requires the explicit
conflict diagnostic.

Stable now depends on the existing first-party `pdf-document` module. Both source
and Javadoc jars contain the lifecycle API. All public signatures remain within
the existing 12 entries, and no acceptance tool enters the product dependency graph.

The current independent evidence is indexed by [Foundation Evidence](../foundation-evidence.yaml)
and [the T03 contract](T03-document-workflow-transaction.md). The prior T04/T06/T07
implementation and partial-chain observations do not pre-certify a changed candidate.

Historical T04 implementation/review observations are retained in the repository's
[pre-T70 record](https://github.com/zerocloud-sdk/folio-pdf/blob/9418b472c99aa87692a0f1ba7f31808e64c5af77/capabilities/evidence/T04-migration-facades.md).
