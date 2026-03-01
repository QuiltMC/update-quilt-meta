package org.quiltmc.meta.update.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public abstract class AbstractCacheService {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    public abstract void purgeCache(List<String> paths) throws Exception;
}
