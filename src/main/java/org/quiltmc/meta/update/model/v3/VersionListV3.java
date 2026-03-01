package org.quiltmc.meta.update.model.v3;

import java.util.Collection;

public record VersionListV3(Collection<GameVersionV3> game, Collection<QuiltMappingsVersionV3> mappings, Collection<HashedMojmapVersionV3> hashed, Collection<QuiltLoaderVersionV3> loader, Collection<QuiltInstallerVersionV3> installer) {
}
