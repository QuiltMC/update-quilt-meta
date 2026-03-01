package org.quiltmc.meta.update.util.hash;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

public class HashFunctionTest {

    /**
     * a random 512 character string, with precomputed hashes provided below.
     */
    private static final String RANDOM_STRING = "0Gp1aSdczQQHDzSgYsEJ0AEfCQZgHoBt5uuXh2KVUrIJaSuYdPEpq5W230ICnhexjUd86ar0O4FdyvEfQCy3t0OVEUCH0lCrEAT663WD6WgMc5XonjGPpD8Kr2xky8v3UhjKxYAa5srLc917ip3tGRlHl65KG31Imb1PiTdP31tD5IfNpQ8ofP6olOBmMTxXE1UcpbMzNcIzyVUtHv2sTcL2ZZPn6k7pZGTgU0EOhTYcz6UCQipQJh62fvSmjvYGezaxqtrda6XcBXKnnUzM8Wu1Kh6N6neKkEYIo7cQpN9U9YGeqS84bBhs8TD0ARKMs64ncELDRogAo8HuBbPrk0wvPMfobGXA2WaEyNxrzb9tJoZJmrxYrNx8iRRS5m5SvfivO2Km5ZGNsPB6tih5TwrI90oD3DDjNqOw8V4sIK07YfqMJbH8f0AJAeMkHJ0TDW1SPGWlcta6UfovjBGTYP3fltnlrOg1foWLlIHG6hJ6fNHOU9oFUlSrvPNRZBuD";
    private static final String RANDOM_STRING_SHA512 = "ab279ba1ef19698b6e8d7b520cfa73dead5a36b9cfe3a702fbe9d07d0a1f6366fa44f57891b96d452bdac2ce13978317a71609c2ee54a372781b4c714384ce60";
    private static final String RANDOM_STRING_SHA256 = "474675943bb98c807422311390151edb595e07b3f9247ddae70d0da83bec8bc3";
    private static final String RANDOM_STRING_SHA1 = "d6fedc693d7a2d763533536add1d39d0a2fff629";

    @Test
    void testSha1() {
        var sha1 = HashFunction.SHA1.createHash(RANDOM_STRING.getBytes(StandardCharsets.UTF_8));
        Assertions.assertEquals(RANDOM_STRING_SHA1, sha1.asHexString());

        Assertions.assertEquals(sha1, Hash.parseHexValue(HashFunction.SHA1, RANDOM_STRING_SHA1));
    }

    @Test
    void testSha256() {
        var sha256 = HashFunction.SHA256.createHash(RANDOM_STRING.getBytes(StandardCharsets.UTF_8));
        Assertions.assertEquals(RANDOM_STRING_SHA256, sha256.asHexString());

        Assertions.assertEquals(sha256, Hash.parseHexValue(HashFunction.SHA256, RANDOM_STRING_SHA256));
    }

    @Test
    void testSha512() {
        var sha512 = HashFunction.SHA512.createHash(RANDOM_STRING.getBytes(StandardCharsets.UTF_8));
        Assertions.assertEquals(RANDOM_STRING_SHA512, sha512.asHexString());

        Assertions.assertEquals(sha512, Hash.parseHexValue(HashFunction.SHA512, RANDOM_STRING_SHA512));
    }
}
