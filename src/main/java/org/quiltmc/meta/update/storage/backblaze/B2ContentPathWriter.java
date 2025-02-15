package org.quiltmc.meta.update.storage.backblaze;

import com.backblaze.b2.client.contentHandlers.B2ContentOutputStreamWriter;
import com.backblaze.b2.client.contentHandlers.B2ContentSink;
import com.backblaze.b2.client.contentHandlers.B2ContentWriter;
import com.backblaze.b2.client.contentSources.B2Headers;
import com.backblaze.b2.client.exceptions.B2Exception;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class B2ContentPathWriter implements B2ContentSink {

    protected final B2ContentWriter delegate;

    protected B2ContentPathWriter(Path outputPath) {
        delegate = B2ContentOutputStreamWriter.builder(() -> new BufferedOutputStream(Files.newOutputStream(outputPath))).build();
    }

    @Override
    public void readContent(B2Headers responseHeaders, InputStream in) throws B2Exception, IOException {
        delegate.readContent(responseHeaders, in);
    }

    public static B2ContentPathWriter of(Path path) {
        return new B2ContentPathWriter(path);
    }
}
