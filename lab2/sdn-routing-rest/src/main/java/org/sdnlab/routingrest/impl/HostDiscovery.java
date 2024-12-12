package org.sdnlab.routingrest.impl;

import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.edge.EdgePortEvent;
import org.onosproject.net.edge.EdgePortListener;
import org.onosproject.net.edge.EdgePortService;
import org.onosproject.net.neighbour.DefaultNeighbourMessageHandler;
import org.onosproject.net.neighbour.NeighbourMessageHandler;
import org.onosproject.net.neighbour.NeighbourResolutionService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true)
public class HostDiscovery {

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected EdgePortService edgeService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected NeighbourResolutionService neighbourResolutionService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    private final Logger log = LoggerFactory.getLogger(getClass());

    private ApplicationId appId;

    private InternalEdgeListener edgeListener = new InternalEdgeListener();
    private NeighbourMessageHandler neighbourMessageHandler = new DefaultNeighbourMessageHandler();

    @Activate
    protected void activate(ComponentContext context) {
        appId = coreService.registerApplication("org.sdnlab.routingrest");

        edgeService.addListener(edgeListener);
        // listen on connected edges
        edgeService.getEdgePoints().forEach(this::addDefault);
        log.info("HostDiscovery Started");
    }

    @Deactivate
    protected void deactivate() {
        edgeService.removeListener(edgeListener);
        neighbourResolutionService.unregisterNeighbourHandlers(appId);
        log.info("HostDiscovery Stopped");
    }

    private void addDefault(ConnectPoint port) {
        neighbourResolutionService.registerNeighbourHandler(port, neighbourMessageHandler, appId);
    }

    private void removeDefault(ConnectPoint port) {
        neighbourResolutionService.unregisterNeighbourHandler(port, neighbourMessageHandler, appId);
    }

    private class InternalEdgeListener implements EdgePortListener {
        @Override
        public void event(EdgePortEvent event) {
            switch (event.type()) {
                case EDGE_PORT_ADDED:
                    addDefault(event.subject());
                    break;
                case EDGE_PORT_REMOVED:
                    removeDefault(event.subject());
                    break;
                default:
                    break;
            }
        }
    }
}
