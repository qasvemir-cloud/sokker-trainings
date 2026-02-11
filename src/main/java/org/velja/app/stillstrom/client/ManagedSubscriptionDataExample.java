package org.velja.app.stillstrom.client;

import java.util.concurrent.CompletableFuture;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.subscriptions.ManagedDataItem;
import org.eclipse.milo.opcua.sdk.client.subscriptions.ManagedSubscription;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ManagedSubscriptionDataExample implements ClientExample {

    public static void main(String[] args) throws Exception {
        ManagedSubscriptionDataExample example = new ManagedSubscriptionDataExample();

        new ClientExampleRunner(example).run();
    }

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void run(OpcUaClient client, CompletableFuture<OpcUaClient> future) throws Exception {
        client.connect().get();

        ManagedSubscription subscription = ManagedSubscription.create(client);

        subscription.addDataChangeListener((items, values) -> {
            for (int i = 0; i < items.size(); i++) {
                logger.info(
                        "subscription value received: item={}, value={}",
                        items.get(i).getNodeId(), values.get(i).getValue()
                );
            }
        });

        ManagedDataItem dataItem = subscription.createDataItem(
                Identifiers.Server_ServerStatus_CurrentTime
        );

        if (dataItem.getStatusCode().isGood()) {
            logger.info("item created for nodeId={}", dataItem.getNodeId());

            // let the example run for 5 seconds before completing
            Thread.sleep(5000);

            dataItem.delete();
        } else {
            logger.warn(
                    "failed to create item for nodeId={} (status={})",
                    dataItem.getNodeId(), dataItem.getStatusCode()
            );
        }

        future.complete(client);
    }

}