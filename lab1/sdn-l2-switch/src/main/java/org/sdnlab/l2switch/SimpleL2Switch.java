/*
 * Copyright 2024-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sdnlab.l2switch;

import static org.sdnlab.l2switch.OsgiPropertyConstants.FLOW_TIMEOUT;
import static org.sdnlab.l2switch.OsgiPropertyConstants.FLOW_TIMEOUT_DEFAULT;

import java.util.Dictionary;
import java.util.Map;
import java.util.Optional;
import org.onlab.packet.EthType.EtherType;
import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onlab.util.Tools;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.topology.TopologyService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true, property = { FLOW_TIMEOUT + ":Integer=" + FLOW_TIMEOUT_DEFAULT })
public class SimpleL2Switch {
    // Instantiates the relevant services.

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService configService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    private final Logger log = LoggerFactory.getLogger(getClass());

    private ApplicationId appId;

    /*
     * Defining macTables as a concurrent map allows multiple threads and packets to
     * use the map without an issue.
     */
    // TODO: Support aging time
    private Map<DeviceId, Map<MacAddress, PortNumber>> macTables = Maps.newConcurrentMap();

    private PacketProcessor processor = new SwitchPacketProcessor();

    /** Configure Flow Timeout for installed flow rules; default is 10 sec. */
    private Integer flowTimeout = FLOW_TIMEOUT_DEFAULT;

    /**
     * Create a variable of the SwitchPacketProcessor class using the
     * PacketProcessor defined above.
     * Activates the app.
     *
     * Create code to add a processor
     */
    @Activate
    protected void activate(ComponentContext context) {
        log.info("Started");
        appId = coreService.registerApplication("org.sdnlab.l2switch"); // equal to the name shown in pom.xml file

        // Create and processor and add it using packetService
        packetService.addProcessor(processor, PacketProcessor.director(2));

        /*
         * Restricts packet types to IPV4 and ARP by only requesting those types
         */
        packetService.requestPackets(DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4).build(), PacketPriority.REACTIVE, appId, Optional.empty());
        packetService.requestPackets(DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_ARP).build(), PacketPriority.REACTIVE, appId, Optional.empty());

        configService.registerProperties(getClass());
        readProperty(context);
    }

    @Modified
    protected void modified(ComponentContext context) {
        log.info("Modified");
        readProperty(context);
    }

    /**
     * Deactivates the processor by removing it.
     *
     * Create code to remove the processor.
     */
    @Deactivate
    protected void deactivate() {
        log.info("Stopped");

        configService.unregisterProperties(getClass(), false);
        flowRuleService.removeFlowRulesById(appId);

        // Remove the processor
        packetService.removeProcessor(processor);
        processor = null;
    }

    private void readProperty(ComponentContext context) {
        Dictionary<?, ?> properties = context.getProperties();

        flowTimeout = Tools.getIntegerProperty(properties, FLOW_TIMEOUT);
    }

    /**
     * This class contains pseudo code that you must replace with your own code in
     * actLikeSwitch. Your job is to
     * send the packet out the port previously learned for the destination MAC. If
     * it does not exist,
     * flood the packet out (to all ports).
     */
    private class SwitchPacketProcessor implements PacketProcessor {
        /**
         * Learns the source port associated with the packet's DeviceId if it has not
         * already been learned.
         * Calls actLikeSwitch to process and send the packet.
         * 
         * @param pc PacketContext object containing packet info
         */
        @Override
        public void process(PacketContext pc) {
            /*
             * Puts the packet's source's device Id into the map macTables if it has not
             * previously been added.
             * (learns the output port)
             */
            macTables.putIfAbsent(pc.inPacket().receivedFrom().deviceId(), Maps.newConcurrentMap());

            // This method simply floods all ports with the packet.
            // actLikeHub(pc);

            /*
             * This is the call to the actLikeSwitch method you will be creating. When
             * you are ready to test it, uncomment the line below, and comment out the
             * actLikeHub call above.
             *
             * NOTE: The perk of an actLikeSwitch method over actLikeHub is speed.
             * FlowRule allows much faster processing.
             */
            actLikeSwitch(pc);
        }

        /**
         * Example method. Floods packet out of all switch ports.
         *
         * @param pc the PacketContext object passed through from activate method
         */
        public void actLikeHub(PacketContext pc) {
            pc.treatmentBuilder().setOutput(PortNumber.FLOOD);
            pc.send();
        }

        /**
         * Ensures packet is of required type. Obtain the port number associated with
         * the packet's source ID.
         * If this port has previously been learned (in the process method) build a flow
         * using the packet's
         * out port, treatment, destination, and other properties. Send the flow to the
         * learned out port.
         * Otherwise, flood packet to all ports if out port has not been learned.
         *
         * @param pc the PacketContext object passed through from activate() method
         */
        public void actLikeSwitch(PacketContext pc) {
            InboundPacket pkt = pc.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (log.isDebugEnabled()) {
                String protocolName = EtherType.lookup(ethPkt.getEtherType()).name();
                String srcMAC = ethPkt.getSourceMAC().toString();
                String dstMAC = ethPkt.getDestinationMAC().toString();
                log.debug("{}: {} -> {}", protocolName, srcMAC, dstMAC);
            }

            /*
             * Ensures the type of packet being processed is only of type IPV4 or ARP (not
             * LLDP or BDDP).
             * If it is not, return and do nothing with the packet. actLikeSwitch can only
             * process
             * IPV4 and ARP packets.
             */
            Short type = ethPkt.getEtherType();
            if (type != Ethernet.TYPE_IPV4 && type != Ethernet.TYPE_ARP) {
                return;
            }

            /*
             * Learn the destination, source, and output port of the packet using a
             * ConnectPoint and the
             * associated macTable. If there is a known port associated with the packet's
             * destination MAC Address,
             * the output port will not be null.
             */
            // find the packets connect point
            ConnectPoint cp = pkt.receivedFrom();

            // save the macTables port value for the deviceID
            Map<MacAddress, PortNumber> macTable = macTables.get(cp.deviceId());
            MacAddress srcMAC = ethPkt.getSourceMAC();
            macTable.put(srcMAC, cp.port());

            // save the outPort as a variable
            MacAddress outMAC = ethPkt.getDestinationMAC();
            PortNumber outPort = macTable.getOrDefault(outMAC, null);

            /*
             * If port is known, set output port to the packet's learned output port and
             * construct a
             * FlowRule using a source, destination, treatment and other properties. Send
             * the FlowRule
             * to the designated output port.
             */
            // if outPort isn't null
            if (outPort != null) {
                // construct FlowRule
                FlowRule flowRule = DefaultFlowRule.builder()
                        .withSelector(DefaultTrafficSelector.builder().matchEthDst(outMAC).build())
                        .withTreatment(DefaultTrafficTreatment.builder().setOutput(outPort).build())
                        .withPriority(PacketPriority.REACTIVE.priorityValue())
                        .forDevice(cp.deviceId())
                        .makeTemporary(flowTimeout)
                        .fromApp(appId)
                        .build();
                flowRuleService.applyFlowRules(flowRule);

                // send the packet
                pc.treatmentBuilder().setOutput(outPort);
                pc.send();
            }
            /*
             * else, the output port has not been learned yet. Flood the packet to all ports
             * using
             * the actLikeHub method
             */
            else {
                actLikeHub(pc);
            }
        }
    }
}
