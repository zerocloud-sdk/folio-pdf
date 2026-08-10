# Preserve unknown content or reject the change

When changing an existing PDF, the Document Engine preserves unknown objects and resources whenever it can do so safely and rejects an operation when preservation cannot be guaranteed. Signed documents are read-only by default; mutation requires an explicit incremental mode whose permitted operations have been proven not to corrupt the existing revision or silently invalidate its signatures.
