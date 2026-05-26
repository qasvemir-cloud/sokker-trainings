package org.velja.app.old.stillstrom.platform;

public interface EmulatorListener {
    void onDataUpdated(boolean chargerAvailable, double batteryLevel);
    void onLogMessageEmulator(String message); // Nova metoda za logove
    void onLogMessageServer(String message); // Nova metoda za logove

}
