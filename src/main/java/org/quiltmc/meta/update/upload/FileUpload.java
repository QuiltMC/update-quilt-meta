package org.quiltmc.meta.update.upload;

public record FileUpload(byte[] content, String sha1, String contentType) {
}
