# Use an offline core with pluggable Capability Providers

The default distribution will remain usable offline, while OCR, rendering, Office conversion, and similar boundary capabilities are exposed through replaceable Capability Providers. Provider adapter artifacts are distributed independently and do not bundle their external engines; users install and select those engines explicitly. Providers may use in-process Java, local native processes, commercial engines, or remote services, allowing deployment and licensing choices without making any one provider part of the core product contract.
