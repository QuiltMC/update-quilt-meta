package org.quiltmc.meta.update.util;

import java.time.Duration;

public class DurationFormat {

    public static String prettyPrintDuration(Duration duration) {
        StringBuilder result = new StringBuilder();

        var days = duration.toDays();
        if(days > 0) {
            result.append(days).append('d');
        }

        var hours = duration.toHoursPart();
        if (hours > 0) {
            result.append(hours).append('h');
        }

        var minutes = duration.toMinutesPart();
        if (minutes > 0) {
            result.append(minutes).append('m');
        }

        var seconds = duration.toSecondsPart();
        if (seconds > 0) {
            result.append(seconds).append('s');
        }

        var millis = duration.toMillisPart();
        if (millis > 0) {
            result.append(millis).append("ms");
        }

        if(result.isEmpty()) {
            var nanos = duration.toNanos();
            if (nanos > 0) {
                result.append(nanos).append("ns");
            }
        }

        return result.toString();
    }
}
