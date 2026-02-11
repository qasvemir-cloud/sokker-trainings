package org.velja.app.stillstrom.client;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.OpcUaSession;
import java.util.concurrent.CompletableFuture;

public class OpcUaEmulator {
    public static void main(String[] args) {
        try {
            String endpointUrl = "opc.tcp://localhost:8443";  // Endpoint servera
            // Kreiranje klijenta
            OpcUaClient client = OpcUaClient.create(endpointUrl);
            // Povezivanje sa serverom
            client.connect().get();
            System.out.println("✅ Successfully connected to the server!");
            // Dohvati dijagnostičke podatke o sesiji
            CompletableFuture<OpcUaSession> sessionDiagnostics = client.getSession();
            if (sessionDiagnostics != null) {
                System.out.println("Session id: " + sessionDiagnostics.toCompletableFuture().get().getSessionId());
            } else {
                System.out.println("No session diagnostics data available.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("❌ Error connecting to OPC UA server: " + e.getMessage());
        }
    }
}