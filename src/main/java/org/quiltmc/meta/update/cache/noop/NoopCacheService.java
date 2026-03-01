package org.quiltmc.meta.update.cache.noop;

import org.quiltmc.meta.update.cache.AbstractCacheService;

import java.util.List;

public class NoopCacheService extends AbstractCacheService {

    @Override
    public void purgeCache(List<String> paths) {
        // NO-OP
    }
}
