package org.velja.app.stillstrom.vessel;

import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.velja.app.stillstrom.platform.PlatformUPCEmulator;
import org.velja.app.stillstrom.platform.EmulatorListener;
import org.velja.app.stillstrom.platform.PlatformUPCServer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class VesselBE implements EmulatorListener{

    private static final Logger logger = LoggerFactory.getLogger(VesselBE.class);
    private final PlatformUPCEmulator emulator;
    private final PlatformUPCServer server;
    @SneakyThrows
    public VesselBE() {
        this.emulator = new PlatformUPCEmulator();
        this.emulator.setListener(this); // Registrujemo kao listener
        this.server = new PlatformUPCServer();
        this.server.setListener(this);
    }

    @SneakyThrows
    public void startServer()
    {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(server::startup, 0, 5, TimeUnit.SECONDS);
    }

    @SneakyThrows
    public void startFetching(boolean isCharging) {
        logger.info("Start fetching metoda "+isCharging);
        if (isCharging) {
            // Ako je isCharging true, pokreni emulator
            emulator.start();
        } else {
            // Ako nije, zaustavi emulator
            emulator.stop();
        }
    }

    @Override
    public void onDataUpdated(boolean chargerAvailable, double batteryLevel) {
        logger.info("[VesselBE] Data Updated: Charger: {}, Battery: {}", chargerAvailable, batteryLevel);

        // Ažuriraj UI
        ChargerApp.updateUI(chargerAvailable, batteryLevel);
        //ChargerApp.logEmulator("[Emulator] Charger: " + chargerAvailable + ", Battery: " + batteryLevel);
    }
    @Override
    public void onLogMessageEmulator(String message) {
        logger.info(message); // i dalje piše u BE log
        ChargerApp.logEmulator(message); // Prikazuje u emulator output prozoru
    }
    @Override
    public void onLogMessageServer(String message) {
        logger.info(message); // i dalje piše u BE log
        ChargerApp.logServer(message); // Prikazuje u server output prozoru
    }
}