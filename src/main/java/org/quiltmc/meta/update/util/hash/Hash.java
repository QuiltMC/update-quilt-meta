package org.quiltmc.meta.update.util.hash;

import java.util.Arrays;
import java.util.HexFormat;

public record Hash(HashFunction algorithm, byte[] bytes) {

    public static Hash parseHexValue(HashFunction algorithm, String hexValue) {
        return new Hash(algorithm, HexFormat.of().parseHex(hexValue));
    }

    public String asHexString() {
        return HexFormat.of().formatHex(bytes());
    }

    @Override
    public String toString() {
        return "%s{%s}".formatted(algorithm().getName(), asHexString());
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Hash(
                HashFunction algorithm1, byte[] bytes1
        ) && this.algorithm() == algorithm1 && Arrays.equals(this.bytes(), bytes1);
    }

    @Override
    public int hashCode() {
        return 31 * algorithm().hashCode() + Arrays.hashCode(bytes());
    }
}
