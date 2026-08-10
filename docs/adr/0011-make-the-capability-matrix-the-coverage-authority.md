# Make the Capability Matrix the coverage authority

A repository-versioned YAML Capability Matrix, rather than a prose feature list or issue labels, is the authority for Reference Suite coverage and generates human-readable documentation. Each entry maps a reference capability to the Native Interface, Migration Facade, limitations, Acceptance Profile, evidence, and one of `planned`, `experimental`, `compatible`, or `limited`; automated Acceptance Profile evidence is required to enter `compatible`, and Capability Parity may be claimed only when every required entry is `compatible`.
