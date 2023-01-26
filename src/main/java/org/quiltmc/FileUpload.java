package org.quiltmc;

public record FileUpload(byte[] content, String contentType) {
    public byte[] content() {
        return content;
    }

    public String contentType() {
        return contentType;
    }
}