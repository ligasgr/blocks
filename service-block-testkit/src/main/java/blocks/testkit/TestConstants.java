package blocks.testkit;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class TestConstants {

    public static final Instant NOW = Instant.now();
    public static final ZoneId ZONE_ID = ZoneId.systemDefault();
    public static final ZonedDateTime ZONED_NOW = NOW.atZone(ZONE_ID);
    public static final Duration IN_AT_MOST_THREE_SECONDS = Duration.ofSeconds(3L);

    private TestConstants() {
    }
}
