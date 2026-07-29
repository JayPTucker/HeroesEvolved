package com.jayptucker.heroesevolved.network;

/** Client-only copy of the server's current Eclipse state. */
public final class ClientEclipseState {
    private static boolean eclipseActive;

    private ClientEclipseState() {
    }

    public static boolean isEclipseActive() {
        return eclipseActive;
    }

    public static void setEclipseActive(boolean active) {
        eclipseActive = active;
    }
}
