package org.quiltmc.meta.update.model.v3;

// TODO remove 'hashed' field in v4? it seems superfluous
public record QuiltMappingsVersionV3(String maven, String version, String gameVersion, int build, String separator, String hashed) {
}
