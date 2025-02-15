package org.quiltmc.meta.update;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;

public class ConstantsTest {

    @Test
    void dateTimeTest() {
        Assertions.assertEquals("2020-06-24T17:52:03+00:00", Constants.MOJANG_TIME_FORMAT.format(Instant.ofEpochSecond(1593021123L)));
        Assertions.assertEquals(Instant.ofEpochSecond(1593021123L), Constants.MOJANG_TIME_FORMAT.parse("2020-06-24T17:52:03+00:00", Instant::from));
    }

    @Test
    void jsonTest() {
        Assertions.assertEquals("\"2020-06-24T17:52:03+00:00\"", Constants.GSON.toJson(Instant.ofEpochSecond(1593021123L)));
        Assertions.assertEquals(Instant.ofEpochSecond(1593021123L), Constants.GSON.fromJson("\"2020-06-24T17:52:03+00:00\"", Instant.class));
    }

}
