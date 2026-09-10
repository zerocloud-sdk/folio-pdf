# T72 first final review attempt (superseded)

Fixed baseline: `e5053749e35e517fe74f81bd3ebb39d8b41e28ce`.

Two clean-context independent reviewers inspected the complete tracked and
untracked working tree. The Standards review reported no findings. The Spec
review reported one High finding: `ClasspathExclusivityIT` omitted the newly
mapped `PdfMerger` and `PdfSplitter` types, and both types initialized
successfully from mixed Stable/Preview jars in either jar order.

The finding was accepted. `development/classpath-utils-red.txt` reproduces the
failure before the correction. The two utility views are now owner-bound
abstract types, matching the Reference Suite's class form while retaining the
previous mapped operation shapes. Their class initializers use one
package-private helper to activate the existing edition guard. Their protected
owner constructors are declared in the exact Facade surface. The focused JAR
contract and both Stable/Preview consumer suites pass after refactoring; see
`development/classpath-utils-green.txt`,
`development/classpath-utils-refactor.txt`,
`development/facade-contract-after-review.txt`, and
`development/facade-pages-after-review.txt`.

This correction changed shipped code and contract inputs. The first staged
candidate, its Foundation certifications, root verification, JDK matrix, and
review verdicts are therefore superseded. A fresh candidate and a second final
two-axis clean-context review are required before completion.

The first full root verification after that correction then found stale exact
inventory counts in `InventoryCommandTest`: the authority correctly contained
105 Facade surfaces, while four assertions still expected 103 or derived page
counts from that old total. The failure is retained in
`development/root-after-review-red.txt`. The assertions were updated to the
declared 105 total and 17 page mappings, and the focused seven-test inventory
suite, validation, generation, and drift check now pass. Because the test source
is itself a staged candidate input, corrected candidate identity
`69948a3c5679ea9333fe725903c01d7e55fe85455c4c437340ef14d2bf11988`
and its 24 otherwise-passing certifications are also superseded. They cannot be
used by the final verification or final review.
