# Target bounded server scale

Foundation Release acceptance covers server workloads around 5,000 pages and 1 GiB while allowing controlled spill to temporary storage. The reusable Document Workflow is thread-safe, each Document Session is thread-confined, and separate documents may execute concurrently; memory, temporary storage, decoded pixels, and time remain explicit configurable limits rather than promises of unbounded streaming.
