# Separate behavioral and facade inventories

Compatibility is tracked by two repository-versioned YAML authorities: the Capability Matrix records behavior, limitations, Dependency Gates, Acceptance Profiles, evidence, and status, while the Facade Surface Manifest records migration types, constructors, methods, generics, constants, and exception mappings. Neither inventory can substitute for the other; generated documentation must link corresponding entries in both directions.
