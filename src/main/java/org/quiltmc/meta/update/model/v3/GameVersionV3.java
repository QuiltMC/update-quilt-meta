package org.quiltmc.meta.update.model.v3;

import org.quiltmc.meta.update.util.mojang.MinecraftMeta;

public record GameVersionV3(String version, boolean stable) {

    public static GameVersionV3 fromMojang(MinecraftMeta.MinecraftVersion version) {
        return new GameVersionV3(version.id(), version.isRelease());
    }
}
