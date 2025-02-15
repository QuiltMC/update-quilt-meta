package org.quiltmc.meta.update.storage.backblaze;

import com.backblaze.b2.client.exceptions.B2Exception;

@FunctionalInterface
interface B2Runnable {

    void run() throws B2Exception;
}
