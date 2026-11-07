package com.eventslogger.gson;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.eventslogger.event.EventData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.Test;

public class DetailedPositionAdapterTest {

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(EventData.DetailedPosition.class, new DetailedPositionAdapter())
            .create();

    @Test
    public void writesDetailedPositionWithoutRealWorldPoint() {
        EventData.DetailedPosition position = new EventData.DetailedPosition(
                new EventData.WorldPoint(3200, 3400, 0, 12800), new EventData.LocalPoint(50, 60, 0), false, null);

        String json = gson.toJson(position);

        assertEquals("\"w3200,3400,0,12800|l50,60,0|e0\"", json);
    }

    @Test
    public void writesDetailedPositionWithRealWorldPoint() {
        EventData.DetailedPosition position = new EventData.DetailedPosition(
                new EventData.WorldPoint(3200, 3400, 0, 12800),
                new EventData.LocalPoint(50, 60, 1),
                true,
                new EventData.WorldPoint(3100, 3300, 1, 12400));

        String json = gson.toJson(position);

        assertEquals("\"w3200,3400,0,12800|l50,60,1|r3100,3300,1,12400|e1\"", json);
    }

    @Test
    public void readsDetailedPositionWithoutRealWorldPoint() {
        String json = "\"w3200,3400,0,12800|l50,60,0|e0\"";

        EventData.DetailedPosition result = gson.fromJson(json, EventData.DetailedPosition.class);

        assertNotNull(result);
        assertEquals(3200, result.getWorldPoint().getX());
        assertEquals(3400, result.getWorldPoint().getY());
        assertEquals(0, result.getWorldPoint().getPlane());
        assertEquals(12800, result.getWorldPoint().getRegionId());

        assertEquals(50, result.getLocalPoint().getX());
        assertEquals(60, result.getLocalPoint().getY());
        assertEquals(0, result.getLocalPoint().getWorldView());

        assertFalse(result.isInstance());
        assertNull(result.getRealWorldPoint());
    }

    @Test
    public void readsDetailedPositionWithRealWorldPoint() {
        String json = "\"w3200,3400,0,12800|l50,60,1|r3100,3300,1,12400|e1\"";

        EventData.DetailedPosition result = gson.fromJson(json, EventData.DetailedPosition.class);

        assertNotNull(result);
        assertTrue(result.isInstance());
        assertNotNull(result.getRealWorldPoint());
        assertEquals(3100, result.getRealWorldPoint().getX());
        assertEquals(3300, result.getRealWorldPoint().getY());
        assertEquals(1, result.getRealWorldPoint().getPlane());
        assertEquals(12400, result.getRealWorldPoint().getRegionId());
    }

    @Test
    public void roundTripsDetailedPosition() {
        EventData.DetailedPosition original = new EventData.DetailedPosition(
                new EventData.WorldPoint(3210, 3420, 2, 12850),
                new EventData.LocalPoint(45, 55, 0),
                true,
                new EventData.WorldPoint(1200, 2400, 0, 4800));

        String json = gson.toJson(original);
        EventData.DetailedPosition roundTripped = gson.fromJson(json, EventData.DetailedPosition.class);

        assertEquals(original, roundTripped);
    }

    @Test
    public void writesNullAsJsonNull() {
        String json = gson.toJson(null, EventData.DetailedPosition.class);

        assertEquals("null", json);
    }

    @Test
    public void readsJsonNullAsNull() {
        assertNull(gson.fromJson("null", EventData.DetailedPosition.class));
    }

    @Test
    public void ignoresUnknownComponentsForForwardCompatibility() {
        String json = "\"w3200,3400,0,12800|x99,88|l50,60,0|v1,2,3|e0\"";

        EventData.DetailedPosition result = gson.fromJson(json, EventData.DetailedPosition.class);

        assertNotNull(result);
        assertEquals(3200, result.getWorldPoint().getX());
        assertEquals(50, result.getLocalPoint().getX());
        assertFalse(result.isInstance());
    }

    @Test
    public void handlesMissingFieldsWithSensibleDefaults() {
        String json = "\"w3200,3400\"";

        EventData.DetailedPosition result = gson.fromJson(json, EventData.DetailedPosition.class);

        assertNotNull(result);
        assertEquals(3200, result.getWorldPoint().getX());
        assertEquals(3400, result.getWorldPoint().getY());
        assertEquals(0, result.getWorldPoint().getPlane());
        assertEquals(0, result.getWorldPoint().getRegionId());
        assertEquals(0, result.getLocalPoint().getX());
        assertEquals(0, result.getLocalPoint().getY());
        assertEquals(0, result.getLocalPoint().getWorldView());
        assertFalse(result.isInstance());
        assertNull(result.getRealWorldPoint());
    }
}
