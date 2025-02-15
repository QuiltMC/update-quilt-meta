package org.quiltmc.meta.update.upload;

import com.backblaze.b2.client.contentSources.B2ByteArrayContentSource;
import com.backblaze.b2.client.contentSources.B2ContentSource;
import com.backblaze.b2.util.B2Sha1;
import com.google.gson.reflect.TypeToken;
import org.quiltmc.meta.update.Constants;
import org.quiltmc.meta.update.util.http.ContentType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Objects;

public record FileInfo(byte[] content, ContentType contentType, byte[] manifestSha1Bytes, byte[] sha1Bytes) implements B2ContentSource {

    public static FileInfo ofBytes(byte[] content, ContentType contentType) {
        return withHashValue(content, contentType, new byte[0]);
    }

    public static FileInfo withHashValue(byte[] content, ContentType contentType, byte[] manifestSha1) {
        if (content.length == 0) {
            throw new IllegalStateException("File is empty");
        }

        var sha1 = B2Sha1.binarySha1OfBytes(content);
        return new FileInfo(content, contentType, manifestSha1.length > 0 ? manifestSha1 : sha1, sha1);
    }

    public static FileInfo json(Object json) {
        return ofBytes(Constants.GSON.toJson(json).getBytes(StandardCharsets.UTF_8), ContentType.APPLICATION_JSON);
    }

    public static FileInfo jsonList(Collection<?> collection) {
       return json(Constants.GSON.toJsonTree(collection, new TypeToken<Collection<?>>() {}.getType()));
    }

    public static FileInfo ofInputStream(InputStream stream, ContentType contentType) throws IOException {
        try (stream) {
            return ofBytes(stream.readAllBytes(), contentType);
        }
    }

    public static FileInfo copyFrom(Path path, ContentType contentType) throws IOException {
        try (var stream = Files.newInputStream(path)) {
            return ofInputStream(stream, contentType);
        }
    }

    public static FileInfo text(String text) {
        return ofBytes(text.getBytes(StandardCharsets.UTF_8), ContentType.TEXT_PLAIN);
    }

    public String sha1() {
        return HexFormat.of().formatHex(sha1Bytes());
    }

    public String manifestSha1() {
        return HexFormat.of().formatHex(manifestSha1Bytes());
    }

    @Override
    public long getContentLength() {
        return content().length;
    }

    @Override
    public String getSha1OrNull() {
        return sha1();
    }

    @Override
    public Long getSrcLastModifiedMillisOrNull() {
        return null;
    }

    @Override
    public InputStream createInputStream() {
        return new ByteArrayInputStream(content());
    }

    @Override
    public B2ContentSource createContentSourceWithRangeOrNull(long start, long length) {
        return B2ByteArrayContentSource.build(Arrays.copyOfRange(content(), (int) start, (int) (start + length)));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FileInfo(byte[] content1, ContentType contentType1, byte[] manifestSha1_1, byte[] sha1_1)
               && contentType() == contentType1
               && Arrays.equals(sha1Bytes(), sha1_1)
               && Arrays.equals(content(), content1);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contentType(), Arrays.hashCode(sha1Bytes()));
    }
}
