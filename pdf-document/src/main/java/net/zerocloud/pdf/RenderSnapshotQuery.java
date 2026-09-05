package net.zerocloud.pdf;

import net.zerocloud.pdf.query.RenderPage;

/** Library-only request for the external Rendering Provider boundary. */
final class RenderSnapshotQuery implements DocumentQuery<RenderingSnapshot> {
    final RenderPage render;
    RenderSnapshotQuery(RenderPage render) { this.render = render; }
}
