package org.quiltmc.meta.update.util.hash;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.quiltmc.meta.update.Bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

public enum HashFunction {

    SHA1("sha1", "SHA-1"),
    SHA256("sha256", "SHA-256"),
    SHA512("sha512", "SHA-512");

    static {
        // ensure hash algorithms are initialized
        Bootstrap.initialize();
    }

    private final String name;
    private final String messageDigestName;

    HashFunction(String name, String messageDigestName) {
        this.name = name;
        this.messageDigestName = messageDigestName;
    }

    public String getName() {
        return name;
    }

    public Hash createHash(byte[] input) {
        try {
            var digest = MessageDigest.getInstance(messageDigestName, BouncyCastleProvider.PROVIDER_NAME);

            return new Hash(this, digest.digest(input));
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new IllegalStateException("Hash algorithm not supported: " + messageDigestName, e);
        }
    }

    public Hash createHash(InputStream input) throws IOException {
        try {
            var digest = MessageDigest.getInstance(messageDigestName, BouncyCastleProvider.PROVIDER_NAME);

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }

            return new Hash(this, digest.digest());
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new IllegalStateException("Hash algorithm not supported: " + messageDigestName, e);
        }
    }
}
