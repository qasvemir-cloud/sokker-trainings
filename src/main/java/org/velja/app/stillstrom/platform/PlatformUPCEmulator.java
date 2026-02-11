package org.velja.app.stillstrom.platform;

import lombok.Getter;
import lombok.Setter;
import org.bouncycastle.asn1.eac.UnsignedInteger;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.types.builtin.*;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseResultMask;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

import static org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseResultMask.ReferenceTypeId;

@Setter
@Getter
public class PlatformUPCEmulator
{
    private static final Logger logger = LoggerFactory.getLogger(PlatformUPCEmulator.class);
    private static final String ENDPOINT_URL = "opc.tcp://localhost:12686/milo";
    private static final Map<String, Object> VARIABLES = new HashMap<>();
    private OpcUaClient client;
    private EmulatorListener listener; // Dodaj listener

    static {
       // VARIABLES.put("Platform/chargerAvailable", true);
        VARIABLES.put("Platform/batteryLevel", 50.0);
    }

    public static void main(String[] args) {
        PlatformUPCEmulator emulator = new PlatformUPCEmulator();
        emulator.start();
    }

    private boolean keepRunning = false;  // Kontrolna promenljiva za zaustavljanje

    // Metoda koja pokreće emulator
    public void start() {
        keepRunning = true;  // Postavljamo da se pokrene
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(this::run, 1, 5, TimeUnit.SECONDS);
    }

    // Metoda koja obavlja posao
    private void run() {
        while (keepRunning) {
            try {
                connect();
                updateVariables();
                client.disconnect().get();
                sleep(2000);
                break;
            } catch (Exception e) {
                logMessage("Connection failed. Retrying in 5 seconds..." + e);
                sleep(5000);
            }
        }
    }

    // Metoda za povezivanje
    private void connect() throws Exception {
        client = OpcUaClient.create(ENDPOINT_URL);
        client.connect().get();
    }

    // Metoda za zaustavljanje emulatora
    public void stop() {
        keepRunning = false;  // Postavi na false kako bi zaustavio petlju
    }

    // Ažuriranje promenljivih
    private void updateVariables() {
        sleep(2000);
        double batteryLevel = 0.0;

        for (Map.Entry<String, Object> entry : VARIABLES.entrySet()) {
            try {
                NodeId nodeId = new NodeId(2, entry.getKey());
                UaVariableNode variableNode = client.getAddressSpace().getVariableNode(nodeId);

                if (entry.getKey().contains("batteryLevel") && (double) entry.getValue() < 100.0) {
                    batteryLevel = Math.min(100.0, (double) entry.getValue() + 1.0);
                    VARIABLES.put(entry.getKey(), batteryLevel);
                    variableNode.writeValue(new DataValue(new Variant(batteryLevel)));
                    logMessage(String.valueOf(batteryLevel));
                }
            } catch (Exception e) {
                logMessage("Failed to update variable: " + entry.getKey() + " - " + e.getMessage());
            }
            sleep(2000);
        }
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void logMessage(String message) {
        logger.info(message); // i dalje šalje u log
        if (listener != null) {
            listener.onLogMessageEmulator("[Emulator] Battery level " +"updated " + " to: "+ message);
            boolean checker=true;
            try
            {
                Double.parseDouble(message);
            }
            catch(NumberFormatException e)
            {
                checker=false;
            }
            if (checker)
            {
                listener.onDataUpdated(true, Double.parseDouble(message));// šalje log u VesselBE

            }
        }
    }
}