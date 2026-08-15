package com.videorecorder.app;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class GeoTagInfo {
    private final double latitude;
    private final double longitude;
    private final double altitude;
    private final long timestamp;
    private final String address;

    public GeoTagInfo(double latitude, double longitude, double altitude, long timestamp, String address) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.timestamp = timestamp;
        this.address = address == null ? "" : address;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getAltitude() {
        return altitude;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getAddress() {
        return address;
    }

    public String getCoordinates() {
        return String.format(Locale.getDefault(), "%.6f, %.6f", latitude, longitude);
    }

    public String getSummary() {
        String altitudeText = String.format(Locale.getDefault(), "%.1f m", altitude);
        return getCoordinates() + "  |  " + altitudeText;
    }

    public String getTimestampText() {
        return new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
                .format(new Date(timestamp));
    }

    public String getDisplayText() {
        if (address.isEmpty()) {
            return getSummary();
        }
        return getSummary() + "\n" + address;
    }
}
