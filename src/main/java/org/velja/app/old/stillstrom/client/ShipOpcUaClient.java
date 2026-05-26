package org.velja.app.old.stillstrom.client;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.types.builtin.*;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class ShipOpcUaClient implements ClientExample {

    private static final Logger logger = LoggerFactory.getLogger(ShipOpcUaClient.class);
    private static final String VARIABLE_NODE_ID = "ChargerAvailable/Status";

    public static void main(String[] args) throws Exception {
        ShipOpcUaClient example = new ShipOpcUaClient();
        new ClientExampleRunner(example, true).run();
    }

    @Override
    public void run(OpcUaClient client, CompletableFuture<OpcUaClient> future) throws Exception {
        // Connect to the OPC UA server
        client.connect().get();

        NodeId nodeId = new NodeId(2, VARIABLE_NODE_ID);

        try {
            // Read the value of ChargerAvailable

            UaVariableNode variableNode = client.getAddressSpace().getVariableNode(nodeId);
            DataValue value2 = variableNode.readValue();
            logger.info("ChargerAvailable exists: {}", value2.getValue().isNotNull());
            DataValue nodeValue = variableNode.readValue();

            if (nodeValue.getValue().isNotNull()) {
                logger.info("Direct Node Read - ChargerAvailable: {}", nodeValue.getValue().getValue());
            } else {
                logger.warn("Direct Node Read - ChargerAvailable is NULL!");
            }

            List<NodeId> nodeIds = Collections.singletonList(nodeId);
            List<DataValue> values = client.readValues(10.0, TimestampsToReturn.Both, nodeIds).get();
            DataValue value = values.get(0);
            if (!values.isEmpty() && values.get(0).getValue().isNotNull()) {
                logger.info("ReadValues - ChargerAvailable: {}", values.get(0).getValue().getValue());
            } else {
                logger.warn("ReadValues - ChargerAvailable is NULL!");
            }
            logger.info("ChargerAvailable value after write: {}", value.getValue().getValue());
            logger.info("ChargerAvailable value: {}", value.getValue().getValue());
            logger.info("ChargerAvailable value: {}", value.getValue().getValue());
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Failed to read ChargerAvailable", e);
        }

        future.complete(client);
    }
}