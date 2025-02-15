package org.quiltmc.meta.update.storage.backblaze;

import com.backblaze.b2.client.contentSources.B2ByteArrayContentSource;
import com.backblaze.b2.client.contentSources.B2ContentSource;
import com.backblaze.b2.util.B2Sha1;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class B2PathContentSource implements B2ContentSource {

    protected final Path path;

    protected B2PathContentSource(Path path) {
        this.path = path;
    }

    @Override
    public long getContentLength() throws IOException {
        return Files.size(path);
    }

    @Override
    public String getSha1OrNull() throws IOException {
        try (var stream = Files.newInputStream(path)) {
            return B2Sha1.hexSha1OfInputStream(stream);
        }
    }

    @Override
    public Long getSrcLastModifiedMillisOrNull() throws IOException {
        return Files.getLastModifiedTime(path).toInstant().toEpochMilli();
    }

    @Override
    public InputStream createInputStream() throws IOException {
        return Files.newInputStream(path);
    }

    @Override
    public B2ContentSource createContentSourceWithRangeOrNull(long start, long length) throws IOException {
        if (length < 0 || length > Integer.MAX_VALUE) {
            throw new IndexOutOfBoundsException("Tried to read more than max allowed bytes in one operation: %s@%d[%d]".formatted(path, start, length));
        }

        var buffer = new byte[(int) length];

        if (length > 0) {
            try (var channel = Files.newByteChannel(path, StandardOpenOption.READ)) {
                channel.position(start);
                channel.read(ByteBuffer.wrap(buffer));
            }
        }

        return B2ByteArrayContentSource.build(buffer);
    }

    public static B2PathContentSource of(Path path) {
        return new B2PathContentSource(path);
    }
}
