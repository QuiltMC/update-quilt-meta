package org.quiltmc.meta.update.model.v3;

import org.quiltmc.meta.update.util.maven.QuiltLoaderMetadataJson;

public record QuiltLauncherMetadataV3(QuiltLoaderVersionV3 loader, HashedMojmapVersionV3 hashed, IntermediaryVersionV3 intermediary, QuiltLoaderMetadataJson launcherMeta) {
}
