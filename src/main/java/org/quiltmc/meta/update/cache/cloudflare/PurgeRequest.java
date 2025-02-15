package org.quiltmc.meta.update.cache.cloudflare;

import java.net.URL;
import java.util.List;

public record PurgeRequest(List<URL> files) {
}
