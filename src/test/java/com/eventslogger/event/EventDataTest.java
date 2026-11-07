package com.eventslogger.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import net.runelite.api.Client;
import net.runelite.api.WorldView;
import org.junit.Test;
import org.mockito.MockedStatic;

public class EventDataTest {

    @Test
    public void worldPointFromCopiesCoordinates() {
        net.runelite.api.coords.WorldPoint rlWorldPoint = new net.runelite.api.coords.WorldPoint(3200, 3200, 1);

        EventData.WorldPoint result = EventData.WorldPoint.from(rlWorldPoint);

        assertEquals(3200, result.getX());
        assertEquals(3200, result.getY());
        assertEquals(1, result.getPlane());
        assertEquals(rlWorldPoint.getRegionID(), result.getRegionId());
    }

    @Test
    public void localPointFromCopiesCoordinates() {
        net.runelite.api.coords.LocalPoint rlLocalPoint = new net.runelite.api.coords.LocalPoint(128, 256, 0);

        EventData.LocalPoint result = EventData.LocalPoint.from(rlLocalPoint);

        assertEquals(1, result.getX());
        assertEquals(2, result.getY());
        assertEquals(0, result.getWorldView());
    }

    @Test
    public void detailedPositionFromNonInstance() {
        Client client = mock(Client.class);
        WorldView worldView = mock(WorldView.class);
        when(worldView.isInstance()).thenReturn(false);
        when(client.getWorldView(0)).thenReturn(worldView);

        net.runelite.api.coords.WorldPoint rlWorldPoint = new net.runelite.api.coords.WorldPoint(3000, 3000, 0);
        net.runelite.api.coords.LocalPoint rlLocalPoint = new net.runelite.api.coords.LocalPoint(64, 64, 0);

        try (MockedStatic<net.runelite.api.coords.WorldPoint> staticMock =
                mockStatic(net.runelite.api.coords.WorldPoint.class)) {
            staticMock
                    .when(() -> net.runelite.api.coords.WorldPoint.fromLocalInstance(client, rlLocalPoint))
                    .thenReturn(null);

            EventData.DetailedPosition position = EventData.DetailedPosition.from(client, rlWorldPoint, rlLocalPoint);

            assertFalse(position.isInstance());
            assertNull(position.getRealWorldPoint());
            assertEquals(3000, position.getWorldPoint().getX());
            assertEquals(rlLocalPoint.getSceneX(), position.getLocalPoint().getX());
        }
    }

    @Test
    public void detailedPositionFromInstanceConvertsRealWorldPoint() {
        Client client = mock(Client.class);
        WorldView worldView = mock(WorldView.class);
        when(worldView.isInstance()).thenReturn(true);
        when(client.getWorldView(0)).thenReturn(worldView);

        net.runelite.api.coords.WorldPoint rlWorldPoint = new net.runelite.api.coords.WorldPoint(3000, 3000, 0);
        net.runelite.api.coords.LocalPoint rlLocalPoint = new net.runelite.api.coords.LocalPoint(64, 64, 0);
        net.runelite.api.coords.WorldPoint realWorldPoint = new net.runelite.api.coords.WorldPoint(1234, 5678, 0);

        try (MockedStatic<net.runelite.api.coords.WorldPoint> staticMock =
                mockStatic(net.runelite.api.coords.WorldPoint.class)) {
            staticMock
                    .when(() -> net.runelite.api.coords.WorldPoint.fromLocalInstance(client, rlLocalPoint))
                    .thenReturn(realWorldPoint);

            EventData.DetailedPosition position = EventData.DetailedPosition.from(client, rlWorldPoint, rlLocalPoint);

            assertTrue(position.isInstance());
            assertEquals(1234, position.getRealWorldPoint().getX());
            assertEquals(5678, position.getRealWorldPoint().getY());
        }
    }

    @Test
    public void detailedPositionEqualityDependsOnFields() {
        EventData.WorldPoint worldPoint = new EventData.WorldPoint(1, 2, 0, 100);
        EventData.LocalPoint localPoint = new EventData.LocalPoint(10, 20, 0);

        EventData.DetailedPosition a = new EventData.DetailedPosition(worldPoint, localPoint, false, null);
        EventData.DetailedPosition b = new EventData.DetailedPosition(worldPoint, localPoint, false, null);
        EventData.DetailedPosition different =
                new EventData.DetailedPosition(new EventData.WorldPoint(9, 9, 0, 100), localPoint, false, null);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, different);
    }
}
