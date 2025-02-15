package org.quiltmc.meta.update.collector.v3;

import org.quiltmc.meta.update.util.http.ContentType;
import org.quiltmc.meta.update.upload.FileInfo;
import org.quiltmc.meta.update.upload.InputCollector;
import org.quiltmc.meta.update.util.Pair;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

public class StaticFileCollectorV3 extends InputCollector {

    private final Map<String, Pair<String, ContentType>> fileInfos = new LinkedHashMap<>();

    public StaticFileCollectorV3() {
        add("index.html", "index.html", ContentType.TEXT_HTML);
        add("openapi.yaml", "openapi.yaml", ContentType.TEXT_YAML);
        add("favicon.ico", "favicon.ico", ContentType.IMAGE_X_ICON);
        add("dark-swagger.css", "dark-swagger.css", ContentType.TEXT_CSS);
        add("swagger.css", "swagger.css", ContentType.TEXT_CSS);
        add("swagger-ui-bundle.js", "swagger-ui-bundle.js", ContentType.TEXT_JAVASCRIPT);
    }

    @Override
    public String getName() {
        return "Static Files";
    }

    @Override
    public CompletableFuture<Void> gatherData(BiConsumer<String, FileInfo> output, Executor executor) {
        return CompletableFuture.runAsync(() -> {
            this.fileInfos.forEach((apiPath, pair) -> {
                try (var stream = StaticFileCollectorV3.class.getClassLoader().getResourceAsStream("static/" + pair.first())) {
                    if(stream == null) {
                        throw new FileNotFoundException("static://" + pair.first());
                    }

                    output.accept(apiPath, FileInfo.ofInputStream(stream, pair.second()));
                } catch (IOException e) {
                    throw new RuntimeException("Error while reading static resource: static://" + pair.first(), e);
                }
            });

            LOGGER.info("Found {} static files", fileInfos.size());
        }, executor);
    }

    private void add(String apiPath, String resourcesPath, ContentType contentType) {
        this.fileInfos.put(apiPath, Pair.of(resourcesPath, contentType));
    }
}
