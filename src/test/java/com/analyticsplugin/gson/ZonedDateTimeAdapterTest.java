package com.analyticsplugin.gson;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.junit.Test;

public class ZonedDateTimeAdapterTest {

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter())
            .create();

    @Test
    public void writesIsoOffsetDateTime() {
        ZonedDateTime value = ZonedDateTime.of(2024, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC);

        String json = gson.toJson(value, ZonedDateTime.class);

        assertEquals("\"2024-01-02T03:04:05Z\"", json);
    }

    @Test
    public void readsIsoOffsetDateTime() {
        ZonedDateTime parsed = gson.fromJson("\"2024-01-02T03:04:05Z\"", ZonedDateTime.class);

        assertEquals(ZonedDateTime.of(2024, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC), parsed);
    }

    @Test
    public void roundTripsWithOffset() {
        ZonedDateTime value = ZonedDateTime.of(2024, 6, 15, 12, 30, 45, 0, ZoneOffset.ofHours(2));

        ZonedDateTime result = gson.fromJson(gson.toJson(value, ZonedDateTime.class), ZonedDateTime.class);

        assertEquals(value.toInstant(), result.toInstant());
        assertEquals(value.getOffset(), result.getOffset());
    }

    @Test
    public void writesNullAsJsonNull() {
        String json = gson.toJson(null, ZonedDateTime.class);

        assertEquals("null", json);
    }

    @Test
    public void readsJsonNullAsNull() {
        assertNull(gson.fromJson("null", ZonedDateTime.class));
    }
}
