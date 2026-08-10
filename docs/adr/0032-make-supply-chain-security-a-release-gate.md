# Make supply-chain security a release gate

Every formal release pins build plugins and dependencies, generates a CycloneDX SBOM, checks licenses and known vulnerabilities, verifies reproducibility, signs artifacts and publishes checksums, and provides a private vulnerability-reporting path. A known unresolved high-severity issue in PDF parsing, cryptography, worker isolation, or a required dependency blocks release unless an explicit, public security exception is accepted by the Lead Maintainer.
