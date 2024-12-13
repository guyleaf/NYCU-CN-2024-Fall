package org.sdnlab.routingrest.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.onlab.packet.VlanId;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.core.IdGenerator;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.host.HostService;
import org.onosproject.net.packet.PacketPriority;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.sdnlab.routingrest.RoutingService;
import org.sdnlab.routingrest.data.ConnectPointDto;
import org.sdnlab.routingrest.data.PathDto;
import org.sdnlab.routingrest.data.RouteDto;
import org.sdnlab.routingrest.exception.InvalidRouteException;
import org.sdnlab.routingrest.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;
import static com.google.common.base.Preconditions.checkNotNull;

// TODO: support thread-safe.
@Component(immediate = true)
public class RoutingManager implements RoutingService {

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    private final Logger log = LoggerFactory.getLogger(getClass());

    private ApplicationId appId;

    private Map<Long, PathDto> routeTable = Maps.newConcurrentMap();
    private Map<Long, Set<FlowRule>> routeToFlows = Maps.newConcurrentMap();

    private IdGenerator generator;

    @Activate
    protected void activate(ComponentContext context) {
        appId = coreService.registerApplication("org.sdnlab.routingrest"); // equal to the name shown in pom.xml file
        generator = coreService.getIdGenerator("routes");
        log.info("RoutingManager Started");
    }

    @Deactivate
    protected void deactivate() {
        flowRuleService.removeFlowRulesById(appId);
        log.info("RoutingManager Stopped");
    }

    private void validatePath(PathDto path) throws InvalidRouteException {
        checkNotNull(path);
        for (int i = 1; i < path.points.size() - 1; i += 2) {
            if (!path.points.get(i).id.equals(path.points.get(i + 1).id)) {
                throw new InvalidRouteException("Each pair of adjacent points should be in the same device.");
            }
        }
    }

    private FlowRule installFlowRule(Host src, Host dst, DeviceId deviceId, PortNumber srcPort, PortNumber dstPort) {
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector
                .builder()
                .matchEthSrc(src.mac())
                .matchInPort(srcPort)
                .matchEthDst(dst.mac());

        if (src.vlan() != VlanId.NONE) {
            selectorBuilder.matchVlanId(src.vlan());
        }

        TrafficTreatment treatment = DefaultTrafficTreatment
                .builder()
                .setOutput(dstPort)
                .build();

        FlowRule flowRule = DefaultFlowRule.builder()
                .withSelector(selectorBuilder.build())
                .withTreatment(treatment)
                .withPriority(PacketPriority.REACTIVE.priorityValue())
                .forDevice(deviceId)
                .makePermanent()
                .fromApp(appId)
                .build();
        flowRuleService.applyFlowRules(flowRule);
        return flowRule;
    }

    private Set<FlowRule> installFlowRules(PathDto path) throws InvalidRouteException {
        Host src = hostService.getHost(path.src().hostId());
        Host dst = hostService.getHost(path.dst().hostId());

        if (src == null || dst == null) {
            throw new InvalidRouteException("Source/Destination host is not found.");
        }

        Set<FlowRule> flows = new HashSet<>();
        List<ConnectPointDto> points = path.points;
        for (int i = 1; i < points.size() - 1; i += 2) {
            DeviceId deviceId = points.get(i).deviceId();
            PortNumber srcPort = points.get(i).portNumber();
            PortNumber dstPort = points.get(i + 1).portNumber();
            FlowRule flowRule = installFlowRule(src, dst, deviceId, srcPort, dstPort);
            flows.add(flowRule);
        }
        return flows;
    }

    private void removeFlowRules(Set<FlowRule> flows) {
        flows.forEach(flowRuleService::removeFlowRules);
    }

    public List<RouteDto> getRoutes() {
        List<RouteDto> routes = new ArrayList<>();
        for (Map.Entry<Long, PathDto> entry : routeTable.entrySet()) {
            routes.add(new RouteDto(entry.getKey(), entry.getValue()));
        }
        return routes;
    }

    public RouteDto addRoute(RouteDto route) throws InvalidRouteException {
        validatePath(route.path);

        // add flow rules before adding to route table to avoid entering corrupted state
        Set<FlowRule> flows = installFlowRules(route.path);
        long id = generator.getNewId();
        routeTable.put(id, route.path);
        routeToFlows.put(id, flows);
        return new RouteDto(id);
    }

    public List<RouteDto> addRoutes(List<RouteDto> routes) throws InvalidRouteException {
        List<RouteDto> ids = new ArrayList<>();
        for (RouteDto route : routes) {
            ids.add(addRoute(route));
        }
        return ids;
    }

    // TODO: return boolean?
    public void deleteRoute(RouteDto route) {
        checkNotNull(route.id);

        // empty-free removal
        routeTable.remove(route.id);
        Set<FlowRule> flows = routeToFlows.remove(route.id);
        if (flows != null) {
            removeFlowRules(flows);
        }
    }

    public void deleteRoutes(List<RouteDto> routes) {
        for (RouteDto route : routes) {
            deleteRoute(route);
        }
    }

    public void updateRoute(RouteDto route) throws InvalidRouteException, NotFoundException {
        validatePath(route.path);

        if (routeTable.containsKey(route.id)) {
            throw new NotFoundException("The route is not found.");
        }
        Set<FlowRule> oldFlows = routeToFlows.get(route.id);

        removeFlowRules(oldFlows);
        Set<FlowRule> flows = installFlowRules(route.path);
        routeTable.replace(route.id, route.path);
        routeToFlows.replace(route.id, flows);
    }

    public void updateRoutes(List<RouteDto> routes) throws InvalidRouteException, NotFoundException {
        for (RouteDto route : routes) {
            updateRoute(route);
        }
    }

    public void clear() {
        routeTable.clear();
        routeToFlows.clear();
        flowRuleService.removeFlowRulesById(appId);
    }
}
