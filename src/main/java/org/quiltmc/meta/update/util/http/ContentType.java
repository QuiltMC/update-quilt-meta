package org.quiltmc.meta.update.util.http;

public enum ContentType {
    APPLICATION_GZIP("application/gzip", "gz"),
    APPLICATION_JSON("application/json", "json"),
    IMAGE_X_ICON("image/x-icon", "ico"),
    TEXT_CSS("text/css", "css"),
    TEXT_HTML("text/html", "html"),
    TEXT_JAVASCRIPT("text/javascript", "js"),
    TEXT_PLAIN("text/plain", "txt"),
    TEXT_YAML("text/yaml", "yaml");

    private final String name;
    private final String fileExtension;

    ContentType(String name, String fileExtension) {
        this.name = name;
        this.fileExtension = fileExtension;
    }

    @Override
    public String toString() {
        return name;
    }

    public String value() {
        return name;
    }

    public String getFileExtension() {
        return fileExtension;
    }
}
