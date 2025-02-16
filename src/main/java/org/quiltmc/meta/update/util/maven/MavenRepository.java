package org.quiltmc.meta.update.util.maven;

import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.quiltmc.meta.update.Constants;
import org.quiltmc.meta.update.util.function.ThrowingConsumer;
import org.quiltmc.meta.update.util.hash.Hash;
import org.quiltmc.meta.update.util.hash.HashFunction;
import org.quiltmc.meta.update.util.http.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

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
        this.client = HttpUtils.newClient().cache(cache).build();
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
        if (cache != null) {
            cache.close();
        }

        if (cacheDir != null) {
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
            private CompletableFuture<FileInfo> fileInfo;

            public Artifact(String version) {
                this.version = version;
            }

            public String mavenId() {
                return String.format("%s:%s:%s", ArtifactMetadata.this.group, ArtifactMetadata.this.name, this.version);
            }

            public URI url() {
                return MavenRepository.this.url.resolve(String.format("%s/%s/%s/%s-%s.jar", ArtifactMetadata.this.group.replace('.', '/'), ArtifactMetadata.this.name, this.version, ArtifactMetadata.this.name, this.version));
            }

            public synchronized CompletableFuture<FileInfo> fileInfo(Executor executor) {
                return Objects.requireNonNullElseGet(this.fileInfo, () -> this.fileInfo = CompletableFuture.supplyAsync(() -> {
                    Map<HashFunction, Hash> hashes = new EnumMap<>(HashFunction.class);
                    AtomicLong fileSize = new AtomicLong(0L);
                    boolean needsManualDownload = false;

                    try {
                        // first try gradle module
                        var moduleRequest = new Request.Builder().get().url(url().toString() + ".module").build();
                        makeFileRequest(moduleRequest, body -> {
                            try (var reader = body.charStream()) {
                                var gradleFileInfo = Optional.ofNullable(Constants.GSON.fromJson(reader, GradleModuleMetadata.class)).flatMap(GradleModuleMetadata::mainFileInfo).orElse(null);
                                if (gradleFileInfo != null) {
                                    fileSize.set(gradleFileInfo.size());
                                    hashes.putAll(gradleFileInfo.hashes());
                                }
                            }
                        });

                        for (HashFunction hashFunction : HashFunction.values()) {
                            if (hashes.containsKey(hashFunction)) {
                                continue;
                            }
                            if (hashFunction.getMavenFileSuffix() == null) {
                                needsManualDownload = true;
                                continue;
                            }

                            var request = new Request.Builder().url(url().toString() + hashFunction.getMavenFileSuffix()).get().build();
                            makeFileRequest(request, body -> {
                                try (var stream = body.byteStream()) {
                                    hashes.put(hashFunction, hashFunction.createHash(stream));
                                }
                            });
                        }
                    } catch (IOException e) {
                        LOGGER.warn("unable to download one or more hash files for {}, falling back to manual download", url(), e);
                        needsManualDownload = true;
                    }

                    // if we dont need the full file, only make a HEAD request
                    if (!needsManualDownload) {
                        var request = new Request.Builder().url(url().toString()).head().build();
                        try {
                            makeFileRequest(request, body -> fileSize.set(body.contentLength()));
                        } catch (IOException e) {
                            LOGGER.warn("Failed HEAD request for {}, retrying GET request...", url(), e);
                            needsManualDownload = true;
                        }
                    }

                    // catch cases where server sends an "unknown" content length
                    if (fileSize.get() <= 0L) {
                        needsManualDownload = true;
                    }

                    if (needsManualDownload) {
                        var request = new Request.Builder().url(url().toString()).get().build();
                        try {
                            makeFileRequest(request, body -> {
                                // maven jars are probably small enough that we can read the whole file into memory,
                                // even for mappings files
                                var bytes = body.bytes();
                                fileSize.set(bytes.length);

                                for (HashFunction hashFunction : HashFunction.values()) {
                                    hashes.computeIfAbsent(hashFunction, key -> key.createHash(bytes));
                                }
                            });
                        } catch (IOException e) {
                            throw new UncheckedIOException("unable to manually retrieve file metadata for " + url(), e);
                        }
                    }

                    return new FileInfo(fileSize.get(), Collections.unmodifiableMap(hashes));
                }, executor));
            }

            public record FileInfo(long fileSize, Map<HashFunction, Hash> hashes) {

                public Hash getHash(HashFunction type) {
                    return hashes.get(type);
                }

                @ApiStatus.Internal
                public Map<String, String> formatHashesForJson() {
                    return hashes().values().stream().collect(Collectors.toMap(hash -> hash.algorithm().getName(), Hash::asHexString));
                }
            }
        }
    }

    public void makeFileRequest(Request request, ThrowingConsumer<ResponseBody, IOException> callback) throws IOException {
        try (var response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                StringBuilder message = new StringBuilder("HTTP ").append(response.code());
                if (!response.message().isBlank()) {
                    message.append(" ").append(response.message());
                }
                throw new IOException(message.toString());
            }

            try (var body = response.body()) {
                if (body == null) {
                    throw new IOException("Received empty response body");
                }

                callback.accept(body);
            }
        }
    }

    public record MavenFileResponse(byte[] content, long contentLength) {
    }
}
