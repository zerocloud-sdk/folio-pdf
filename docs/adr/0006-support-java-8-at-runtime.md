# Support Java 8 at runtime

Java 8 is a runtime compatibility contract, not merely a compiler setting. Core artifacts and their required dependencies must run on Java 8, while the same artifacts will also be compatibility-tested on later supported JDKs; this constraint governs dependency selection and prevents accidental use of newer platform APIs.
