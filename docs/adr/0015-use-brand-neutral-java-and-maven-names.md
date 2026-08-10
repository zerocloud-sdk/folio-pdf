# Use brand-neutral Java and Maven names

Public Java packages use the `net.zerocloud.pdf.*` root and Maven artifacts use the `net.zerocloud:pdf-*` coordinate pattern. Keeping the disputed public name out of durable code identifiers avoids ecosystem collision and permits a future brand change without forcing consumers to rewrite imports.
