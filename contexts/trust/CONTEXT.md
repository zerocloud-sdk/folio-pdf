# Trust

This context owns document confidentiality, authenticity, integrity, timestamps, trust chains, and signature validation.

## Language

**Legacy Security Mode**:
An explicit opt-in that permits writing obsolete Reference Suite password-encryption algorithms for migration needs. It is never selected by default.
_Avoid_: compatible encryption, default encryption

**FIPS Distribution**:
A future Trust distribution tied to an explicitly certified cryptographic provider, JVM, platform, and approved operating mode, with no ordinary-provider coexistence or silent fallback.
_Avoid_: FIPS-compatible algorithm, FIPS mode flag
