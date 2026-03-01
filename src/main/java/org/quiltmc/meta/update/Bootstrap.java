package org.quiltmc.meta.update;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;
import java.util.concurrent.atomic.AtomicBoolean;

public class Bootstrap {

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    public static synchronized void initialize() {
        if(!INITIALIZED.get()) {
            Security.addProvider(new BouncyCastleProvider());
            INITIALIZED.set(true);
        }
    }
}
