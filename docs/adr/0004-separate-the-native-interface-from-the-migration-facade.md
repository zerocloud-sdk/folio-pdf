# Separate the Native Interface from the Migration Facade

The project will expose a Native Interface as its primary long-term interface and a separate, optional Migration Facade for familiar iText 7-era concepts and call patterns. Both use project-owned namespaces: the facade lowers migration cost without promising unchanged imports or binary compatibility, while the Native Interface remains free to develop a coherent model of its own.
