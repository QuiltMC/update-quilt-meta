package org.quiltmc.meta.update.util.maven;

import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.jetbrains.annotations.NotNull;
import org.quiltmc.meta.update.util.http.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class MavenRepository implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(MavenRepository.class);
    private static final long CACHE_SIZE = 50L * 1024L * 1024L;
    private final URI url;
    private final Path cacheDir;
    private final Cache cache;
    private final OkHttpClient client;

    private MavenRepository(URI url) throws IOException {
        this.url = url;
        this.cacheDir = Files.createTempDirectory("update-quilt-meta-maven-cache");
        var cacheDirFile = this.cacheDir.toFile();
        cacheDirFile.deleteOnExit();
        this.cache = new Cache(cacheDirFile, CACHE_SIZE);
        this.client = HttpUtils.newClient()
                .cache(cache)
                .build();
    }

    public ArtifactMetadata getMetadata(String group, String name) {
        String metadataXmlLocation = "%s/%s/maven-metadata.xml".formatted(group.replace('.', '/'), name);
        Collection<String> versions = readVersionsFromPom(this.url.resolve(metadataXmlLocation));
        return new ArtifactMetadata(group, name, versions);
    }

    public URI url() {
        return url;
    }

    private Collection<String> readVersionsFromPom(URI uri) {
        try {
            var request = new Request.Builder().url(uri.toURL()).get().build();

            try (var response = client.newCall(request).execute()) {
                var body = response.body();
                if (!response.isSuccessful() || body == null) {
                    try {
                        var message = HttpUtils.readBody(body);
                        LOGGER.error("HTTP {} - {}: {}{}", response.code(), response.message(), response.request().url(), message.stream().map(line -> "\n\t" + line));
                    } catch (IOException e) {
                        // ignored
                    }
                    throw new RuntimeException("Failed to make HTTP request for " + response.request().url() + ": " + response.code() + " - " + response.message());
                }

                XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(body.charStream());
                try {
                    List<String> versions = new ArrayList<>();
                    while (reader.hasNext()) {
                        if (reader.next() == XMLStreamConstants.START_ELEMENT && reader.getLocalName().equals("version")) {
                            String text = reader.getElementText();
                            versions.add(text);
                        }
                    }

                    return versions.reversed().stream().distinct().toList();

                } finally {
                    reader.close();
                }
            }
        } catch (IOException | XMLStreamException e) {
            LOGGER.error("Failed to load {}", uri, e);
            throw new RuntimeException("Failed to load " + uri);
        }
    }

    public static MavenRepository of(String url) throws IOException {
        if (!url.endsWith("/")) {
            url += "/";
        }
        return new MavenRepository(URI.create(url));
    }

    @Override
    public void close() throws IOException {
        if(cache != null) {
            cache.close();
        }

        if(cacheDir != null) {
            LOGGER.debug("Deleting cache dir for {}...", this.url);
            Files.walkFileTree(cacheDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    public class ArtifactMetadata implements Collection<ArtifactMetadata.Artifact> {
        public final String group;
        public final String name;
        private final Collection<String> versions;
        private final Collection<Artifact> artifacts;

        ArtifactMetadata(String group, String name, Collection<String> versions) {
            this.group = group;
            this.name = name;
            this.versions = versions;
            this.artifacts = new LinkedHashSet<>();

            for (String version : versions) {
                this.artifacts.add(new Artifact(version));
            }
        }

        @Override
        public int size() {
            return this.artifacts.size();
        }

        @Override
        public boolean isEmpty() {
            return this.artifacts.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return this.artifacts.contains(o);
        }

        @Override
        public Iterator<Artifact> iterator() {
            return this.artifacts.iterator();
        }

        @Override
        public @NotNull Object[] toArray() {
            return this.artifacts.toArray();
        }

        @Override
        public @NotNull <T> T[] toArray(@NotNull T[] a) {
            return this.artifacts.toArray(a);
        }

        @Override
        public boolean add(Artifact artifact) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean containsAll(@NotNull Collection<?> c) {
            return this.artifacts.containsAll(c);
        }

        @Override
        public boolean addAll(@NotNull Collection<? extends Artifact> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAll(@NotNull Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean retainAll(@NotNull Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }

        public boolean containsVersion(String version) {
            return this.versions.contains(version);
        }

        public class Artifact {
            public final String version;

            public Artifact(String version) {
                this.version = version;
            }

            public String mavenId() {
                return String.format("%s:%s:%s", ArtifactMetadata.this.group, ArtifactMetadata.this.name, this.version);
            }

            public URI url() {
                return MavenRepository.this.url.resolve(String.format("%s/%s/%s/%s-%s.jar", ArtifactMetadata.this.group.replace('.', '/'), ArtifactMetadata.this.name, this.version, ArtifactMetadata.this.name, this.version));
            }
        }
    }
}
