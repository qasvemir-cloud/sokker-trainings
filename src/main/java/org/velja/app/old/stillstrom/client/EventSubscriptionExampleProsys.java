package org.velja.app.old.stillstrom.client;

public class EventSubscriptionExampleProsys extends EventSubscriptionExample {

    public static void main(String[] args) throws Exception {
        EventSubscriptionExampleProsys example = new EventSubscriptionExampleProsys();

        new ClientExampleRunner(example, false).run();
    }

    @Override
    public String getEndpointUrl() {
        return "opc.tcp://localhost:53530/OPCUA/SimulationServer";
    }

}