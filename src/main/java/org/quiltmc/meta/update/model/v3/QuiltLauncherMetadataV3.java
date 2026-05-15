package org.quiltmc.meta.update.model.v3;

import org.jspecify.annotations.Nullable;
import org.quiltmc.meta.update.util.maven.QuiltLoaderMetadataJson;

public record QuiltLauncherMetadataV3(QuiltLoaderVersionV3 loader, @Nullable HashedMojmapVersionV3 hashed, @Nullable IntermediaryVersionV3 intermediary, QuiltLoaderMetadataJson launcherMeta) {
}
