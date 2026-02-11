package org.velja.app.stillstrom.platform;

import lombok.Getter;
import lombok.Setter;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.server.Lifecycle;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.DataItem;
import org.eclipse.milo.opcua.sdk.server.api.ManagedNamespaceWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.dtd.DataTypeDictionaryManager;
import org.eclipse.milo.opcua.sdk.server.model.nodes.objects.BaseEventTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.nodes.objects.ServerTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.*;

@Getter
@Setter
public class PlatformUPCNamespace extends ManagedNamespaceWithLifecycle {
    public static final String NAMESPACE_URI = "urn:platform:opcua";

    private static final Logger logger = LoggerFactory.getLogger(PlatformUPCNamespace.class);

    private volatile Thread eventThread;
    private volatile boolean keepPostingEvents = true;
    private EmulatorListener listener; // Dodaj listener

    private final Random random = new Random();

    private final DataTypeDictionaryManager dictionaryManager;

    private final SubscriptionModel subscriptionModel;

    PlatformUPCNamespace(OpcUaServer server) {
        super(server, NAMESPACE_URI);

        subscriptionModel = new SubscriptionModel(server, this);
        dictionaryManager = new DataTypeDictionaryManager(getNodeContext(), NAMESPACE_URI);

        getLifecycleManager().addLifecycle(dictionaryManager);
        getLifecycleManager().addLifecycle(subscriptionModel);

        getLifecycleManager().addStartupTask(this::createAndAddNodes);

        getLifecycleManager().addLifecycle(new Lifecycle() {
            @Override
            public void startup() {
                startBogusEventNotifier();
            }

            @Override
            public void shutdown() {
                try {
                    keepPostingEvents = false;
                    eventThread.interrupt();
                    eventThread.join();
                } catch (InterruptedException ignored) {
                    // ignored
                }
            }
        });
    }

    private void startBogusEventNotifier() {
        // Set the EventNotifier bit on Server Node for Events.
        UaNode serverNode = getServer()
                .getAddressSpaceManager()
                .getManagedNode(Identifiers.Server)
                .orElse(null);

        if (serverNode instanceof ServerTypeNode) {
            ((ServerTypeNode) serverNode).setEventNotifier(ubyte(1));

            // Post a bogus Event every couple seconds
            eventThread = new Thread(() -> {
                while (keepPostingEvents) {
                    try {
                        BaseEventTypeNode eventNode = getServer().getEventFactory().createEvent(
                                newNodeId(UUID.randomUUID()),
                                Identifiers.BaseEventType
                        );

                        eventNode.setBrowseName(new QualifiedName(1, "foo"));
                        eventNode.setDisplayName(LocalizedText.english("foo"));
                        eventNode.setEventId(ByteString.of(new byte[]{0, 1, 2, 3}));
                        eventNode.setEventType(Identifiers.BaseEventType);
                        eventNode.setSourceNode(serverNode.getNodeId());
                        eventNode.setSourceName(serverNode.getDisplayName().getText());
                        eventNode.setTime(DateTime.now());
                        eventNode.setReceiveTime(DateTime.NULL_VALUE);
                        eventNode.setMessage(LocalizedText.english("event message!"));
                        eventNode.setSeverity(ushort(2));

                        //noinspection UnstableApiUsage
                        getServer().getEventBus().post(eventNode);

                        eventNode.delete();
                    } catch (Throwable e) {
                        logger.error("Error creating EventNode: {}", e.getMessage(), e);
                    }

                    try {
                        //noinspection BusyWait
                        Thread.sleep(2_000);
                    } catch (InterruptedException ignored) {
                        // ignored
                    }
                }
            }, "bogus-event-poster");

            eventThread.start();
        }
    }

    private void createAndAddNodes() {
        NodeId folderNodeId = newNodeId("Platform");
        UaFolderNode folderNode = createAndAddNode(folderNodeId);

        addBooleanVariable(folderNode, "chargerAvailable");
        addBooleanVariable(folderNode, "chargingInProgress");
        addDoubleVariable(folderNode, "chargingPower");
        addDoubleVariable(folderNode, "chargingVoltage");
        addDoubleVariable(folderNode, "chargingCurrent");
        addDoubleVariable(folderNode, "batteryLevel");
        addStringVariable(folderNode, "errorState");

        // CMS variables
        addDoubleVariable(folderNode, "tension");
        addDoubleVariable(folderNode, "cablePaidOut");
        addDoubleVariable(folderNode, "distanceToCharger");
        addDoubleVariable(folderNode, "catenary");

        // Crane variables
        addStringVariable(folderNode, "craneState");
        addBooleanVariable(folderNode, "craneExtended");
    }

    private UaFolderNode createAndAddNode(NodeId folderNodeId) {
        UaFolderNode folderNode = new UaFolderNode(
                getNodeContext(),
                folderNodeId,
                newQualifiedName(folderNodeId.toParseableString()),
                LocalizedText.english(folderNodeId.toParseableString())
        );
        getNodeManager().addNode(folderNode);
        return folderNode;
    }

    private void addBooleanVariable(UaFolderNode parent, String name) {
        addVariable(parent, name, Identifiers.Boolean, false);
    }

    private void addDoubleVariable(UaFolderNode parent, String name) {
        addVariable(parent, name, Identifiers.Double, 0.0);
    }

    private void addIntegerVariable(UaFolderNode parent, String name) {
        addVariable(parent, name, Identifiers.Int32, 0);
    }

    private void addStringVariable(UaFolderNode parent, String name) {
        addVariable(parent, name, Identifiers.String, "None");
    }

    private void addVariable(UaFolderNode parent, String name, NodeId type, Object value) {
        NodeId nodeId = newNodeId("Platform/" + name);
        UaVariableNode node = UaVariableNode.builder(getNodeContext())
                .setNodeId(nodeId)
                .setAccessLevel(AccessLevel.READ_WRITE)
                .setUserAccessLevel(AccessLevel.READ_WRITE)
                .setBrowseName(newQualifiedName(name))
                .setDisplayName(LocalizedText.english(name))
                .setDataType(type)
                .setTypeDefinition(Identifiers.BaseDataVariableType)
                .build();
        node.setValue(new DataValue(new Variant(value), StatusCode.GOOD, DateTime.now()));
        getNodeManager().addNode(node);
        parent.addOrganizes(node);
        logMessage("{} variable created with NodeId: {} "+" "+ name+" "+ nodeId);
    }
    private void logMessage(String message)
    {
        logger.info(message); // i dalje šalje u log
        if (listener != null) {
            listener.onLogMessageServer("[Server] " + message); // šalje log u VesselBE
        }
    }
    @Override
    public void onDataItemsCreated(List<DataItem> dataItems) {
        subscriptionModel.onDataItemsCreated(dataItems);
    }

    @Override
    public void onDataItemsModified(List<DataItem> dataItems) {
        subscriptionModel.onDataItemsModified(dataItems);
    }

    @Override
    public void onDataItemsDeleted(List<DataItem> dataItems) {
        subscriptionModel.onDataItemsDeleted(dataItems);
    }

    @Override
    public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {
        subscriptionModel.onMonitoringModeChanged(monitoredItems);
    }

}
