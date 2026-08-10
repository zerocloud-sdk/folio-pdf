# Defer FIPS to a separate Trust distribution

The Foundation Release makes no FIPS claim. Trust preserves a cryptographic-provider seam so a future FIPS Distribution can pin a certified provider version, Java runtime, platform, startup configuration, approved mode, and deployment evidence; ordinary and FIPS providers may not coexist in that distribution, and failure may not silently fall back to an uncertified provider.
