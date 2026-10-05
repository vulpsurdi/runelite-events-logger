package com.analyticsplugin.gson;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.analyticsplugin.event.AnalyticsData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.Test;

public class DetailedPositionAdapterTest {

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(AnalyticsData.DetailedPosition.class, new DetailedPositionAdapter())
            .create();

    @Test
    public void writesDetailedPositionWithoutRealWorldPoint() {
        AnalyticsData.DetailedPosition position = new AnalyticsData.DetailedPosition(
                new AnalyticsData.WorldPoint(3200, 3400, 0, 12800),
                new AnalyticsData.LocalPoint(50, 60, 0),
                false,
                null);

        String json = gson.toJson(position);

        assertEquals("\"w3200,3400,0,12800|l50,60,0|e0\"", json);
    }

    @Test
    public void writesDetailedPositionWithRealWorldPoint() {
        AnalyticsData.DetailedPosition position = new AnalyticsData.DetailedPosition(
                new AnalyticsData.WorldPoint(3200, 3400, 0, 12800),
                new AnalyticsData.LocalPoint(50, 60, 1),
                true,
                new AnalyticsData.WorldPoint(3100, 3300, 1, 12400));

        String json = gson.toJson(position);

        assertEquals("\"w3200,3400,0,12800|l50,60,1|r3100,3300,1,12400|e1\"", json);
    }

    @Test
    public void readsDetailedPositionWithoutRealWorldPoint() {
        String json = "\"w3200,3400,0,12800|l50,60,0|e0\"";

        AnalyticsData.DetailedPosition result = gson.fromJson(json, AnalyticsData.DetailedPosition.class);

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

        AnalyticsData.DetailedPosition result = gson.fromJson(json, AnalyticsData.DetailedPosition.class);

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
        AnalyticsData.DetailedPosition original = new AnalyticsData.DetailedPosition(
                new AnalyticsData.WorldPoint(3210, 3420, 2, 12850),
                new AnalyticsData.LocalPoint(45, 55, 0),
                true,
                new AnalyticsData.WorldPoint(1200, 2400, 0, 4800));

        String json = gson.toJson(original);
        AnalyticsData.DetailedPosition roundTripped = gson.fromJson(json, AnalyticsData.DetailedPosition.class);

        assertEquals(original, roundTripped);
    }

    @Test
    public void writesNullAsJsonNull() {
        String json = gson.toJson(null, AnalyticsData.DetailedPosition.class);

        assertEquals("null", json);
    }

    @Test
    public void readsJsonNullAsNull() {
        assertNull(gson.fromJson("null", AnalyticsData.DetailedPosition.class));
    }

    @Test
    public void ignoresUnknownComponentsForForwardCompatibility() {
        String json = "\"w3200,3400,0,12800|x99,88|l50,60,0|v1,2,3|e0\"";

        AnalyticsData.DetailedPosition result = gson.fromJson(json, AnalyticsData.DetailedPosition.class);

        assertNotNull(result);
        assertEquals(3200, result.getWorldPoint().getX());
        assertEquals(50, result.getLocalPoint().getX());
        assertFalse(result.isInstance());
    }

    @Test
    public void handlesMissingFieldsWithSensibleDefaults() {
        String json = "\"w3200,3400\"";

        AnalyticsData.DetailedPosition result = gson.fromJson(json, AnalyticsData.DetailedPosition.class);

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
