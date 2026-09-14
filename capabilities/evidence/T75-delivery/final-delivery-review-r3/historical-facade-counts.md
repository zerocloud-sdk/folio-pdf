# Historical Facade count boundaries

The current [Facade Surface Manifest](../../../facade-surface.yaml) is the
whole-artifact source authority. Both actual r3 Stable and Preview jars are
checked against all 21 public Facade types and 157 declared members. At the
fixed baseline `5b1603c435f11c40368f75b7b9a2777c9c5e9761`, the whole surface
was already 20 types and 151 members. T75 adds `PdfTextExtractor` and six
extraction members. These totals describe the Facade artifacts, not all Native
result types.

The older count statements have precise historical family boundaries:

| Scope | Public types | Declared members | Baseline and current status |
| --- | ---: | ---: | --- |
| Lifecycle and PDF values | 17 | 89 | Unchanged family union; the T09 evidence profile's count remains accurate |
| Lifecycle, PDF values and page manipulation | 19 | 105 | Unchanged family union; this was the whole Facade at the #72 stage |
| Whole Facade at the fixed T75 baseline | 20 | 151 | Historical baseline total |
| Whole Facade on the current r3 candidate | 21 | 157 | Current complete artifact authority and actual jar checks |

The 19/105 prose in [T03](../../../../docs/t03-certification.md) and
[T09](../../../../docs/t09-certification.md) came from commit
`55528893ed7167e103365eb4bff2062f839f2835` for #72. Those lines are byte-identical
to the fixed T75 baseline. Later additions expanded the whole artifact beyond
that historical family. Neither 19/105 nor 17/89 limits the current whole-jar
checks or permits omitting newer members. The current manifest and bound
[Foundation evidence](../../../foundation-evidence.yaml) govern this refresh;
the original historical source and contract bytes remain preserved.

The coverage descriptions also retain their exact boundaries. `JarContractIT`
reflects both current jars against the complete 21/157 manifest.
`ClasspathExclusivityIT` individually probes the 20 historical public classes
in both classpath orders. The new `PdfTextExtractor` explicitly forces
initialization of the already-probed `PdfDocument`; its shared coexistence
rejection follows from that reviewed source delegation. It is not described
as a separately executed twenty-first dual-order class probe.

This addendum clarifies historical counts and actual coverage without changing
the frozen implementation, certification contracts or mandatory checks.
