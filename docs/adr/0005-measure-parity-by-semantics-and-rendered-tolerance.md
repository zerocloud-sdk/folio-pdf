# Measure parity by semantics and rendered tolerance

Compatibility will require equivalent PDF semantics and, for visual features, pagination, geometry, and rendered output within explicit tolerances. It will not require byte-identical files or identical internal exceptions and performance characteristics; this keeps the contract meaningful across independent PDF engines while still detecting visible migration regressions.
