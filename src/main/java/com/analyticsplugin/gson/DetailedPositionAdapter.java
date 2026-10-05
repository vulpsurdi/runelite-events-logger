package com.analyticsplugin.gson;

import com.analyticsplugin.event.AnalyticsData;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class DetailedPositionAdapter extends TypeAdapter<AnalyticsData.DetailedPosition> {

    @Override
    public void write(JsonWriter out, AnalyticsData.@Nullable DetailedPosition value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            // We may want to drop or add information, so we format it in a way that
            // accommodates future changes.
            // w - world
            // l - local
            // r - real world
            // e - extra
            // | separates components

            final var stringBuilder = new StringBuilder();

            stringBuilder.append("w");
            stringBuilder.append(value.getWorldPoint().getX());
            stringBuilder.append(",");
            stringBuilder.append(value.getWorldPoint().getY());
            stringBuilder.append(",");
            stringBuilder.append(value.getWorldPoint().getPlane());
            stringBuilder.append(",");
            stringBuilder.append(value.getWorldPoint().getRegionId());

            stringBuilder.append("|l");
            stringBuilder.append(value.getLocalPoint().getX());
            stringBuilder.append(",");
            stringBuilder.append(value.getLocalPoint().getY());
            stringBuilder.append(",");
            stringBuilder.append(value.getLocalPoint().getWorldView());

            final var realWorldPoint = value.getRealWorldPoint();
            if (realWorldPoint != null) {
                stringBuilder.append("|r");
                stringBuilder.append(realWorldPoint.getX());
                stringBuilder.append(",");
                stringBuilder.append(realWorldPoint.getY());
                stringBuilder.append(",");
                stringBuilder.append(realWorldPoint.getPlane());
                stringBuilder.append(",");
                stringBuilder.append(realWorldPoint.getRegionId());
            }

            stringBuilder.append("|e");
            stringBuilder.append(value.isInstance() ? 1 : 0);

            out.value(stringBuilder.toString());
        }
    }

    /**
     * Get an int but if the part's missing safely return 0. We do this to future-proof
     * in case parts get removed.
     */
    private int parseIntSafely(String[] parts, int index) {
        if (parts.length <= index) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index].trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private AnalyticsData.WorldPoint parseWorldPointSafely(String component) {
        AnalyticsData.WorldPoint worldPosition;
        final var parts = component.trim().split(",");
        final var x = parseIntSafely(parts, 0);
        final var y = parseIntSafely(parts, 1);
        final var plane = parseIntSafely(parts, 2);
        final var regionId = parseIntSafely(parts, 3);
        worldPosition = new AnalyticsData.WorldPoint(x, y, plane, regionId);
        return worldPosition;
    }

    private AnalyticsData.LocalPoint parseLocalPositionSafely(String component) {
        AnalyticsData.LocalPoint localPosition;
        final var parts = component.trim().split(",");
        final var x = parseIntSafely(parts, 0);
        final var y = parseIntSafely(parts, 1);
        final var worldView = parseIntSafely(parts, 2);
        localPosition = new AnalyticsData.LocalPoint(x, y, worldView);
        return localPosition;
    }

    /**
     * Best effort parsing to be resilient to future format changes.
     */
    @Override
    public AnalyticsData.@Nullable DetailedPosition read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        final var unparsedString = in.nextString().trim();
        final var components = unparsedString.split("\\|");
        AnalyticsData.WorldPoint worldPosition = null;
        AnalyticsData.LocalPoint localPosition = null;
        AnalyticsData.WorldPoint realWorldPosition = null;
        boolean isInstance = false;
        for (final var untrimmedComponent : components) {
            final var component = untrimmedComponent.trim();
            if (component.isEmpty()) {
                continue;
            }
            final var componentType = component.charAt(0);
            switch (componentType) {
                case 'w': {
                    worldPosition = parseWorldPointSafely(component.substring(1));
                    break;
                }
                case 'l': {
                    localPosition = parseLocalPositionSafely(component.substring(1));
                    break;
                }
                case 'r': {
                    realWorldPosition = parseWorldPointSafely(component.substring(1));
                    break;
                }
                case 'e': {
                    final var parts = component.substring(1).trim().split(",");
                    isInstance = parseIntSafely(parts, 0) != 0;
                    break;
                }
            }
        }
        return new AnalyticsData.DetailedPosition(
                worldPosition != null ? worldPosition : new AnalyticsData.WorldPoint(0, 0, 0, 0),
                localPosition != null ? localPosition : new AnalyticsData.LocalPoint(0, 0, 0),
                isInstance,
                realWorldPosition);
    }
}
