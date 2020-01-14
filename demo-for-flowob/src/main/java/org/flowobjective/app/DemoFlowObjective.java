/*
 * Copyright 2020-present Open Networking Foundation
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
package org.flowobjective.app;

import com.google.common.collect.ImmutableSet;
import org.onosproject.cfg.ComponentConfigService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ComponentPropertyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.onlab.packet.Ethernet;
import org.onlab.packet.ICMP;
import org.onlab.packet.IPv4;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.IpAddress;
import org.onlab.packet.Ip4Prefix;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.MacAddress;
import org.onlab.packet.TCP;
import org.onlab.packet.TpPort;
import org.onlab.packet.UDP;
import org.onlab.util.Tools;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.event.Event;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.criteria.PortCriterion;
import org.onosproject.net.flow.criteria.EthCriterion;
import org.onosproject.net.flow.criteria.IPCriterion;
import org.onosproject.net.flow.criteria.IPProtocolCriterion;
import org.onosproject.net.flow.criteria.TcpPortCriterion;
import org.onosproject.net.flow.criteria.UdpPortCriterion;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.FlowRuleListener;
import org.onosproject.net.flow.FlowRuleEvent;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flow.instructions.Instructions;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.host.HostService;
import org.onosproject.net.link.LinkEvent;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.topology.TopologyEvent;
import org.onosproject.net.topology.TopologyListener;
import org.onosproject.net.topology.TopologyService;
import org.onosproject.store.service.StorageService;
import org.onosproject.core.GroupId;
import org.onosproject.net.group.*;
import org.onosproject.net.flow.DefaultFlowRule;

import static org.slf4j.LoggerFactory.getLogger;
import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import static org.onlab.util.Tools.get;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class DemoFlowObjective {

    private final Logger log = getLogger(getClass());
    private static final int FORWARD_TIMEOUT = 20;
	private static final int FORWARD_PRIORITY = 20;
	private static final int DEFAULT_PRIORITY = 40001;
    ForwardingObjective forwardingObjective;
    ForwardingObjective forwardingObjective2;
	@Reference(cardinality = ReferenceCardinality.MANDATORY)
	protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;
    
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected GroupService groupService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;
    ApplicationId appId;

    @Activate
    protected void activate() {

        appId = coreService.getAppId("org.flowob.app");
        flowRuleObjectiveDemo();
        log.info("Demo FlowObjective App Started");
    }
    private void installGroupForSW2(int outPort,String swid, int myGroupId)
    {
        TrafficTreatment treatmentBuilder = DefaultTrafficTreatment.builder()
                .setOutput(PortNumber.portNumber(outPort))
                .build();
        GroupBucket bucket = DefaultGroupBucket.createIndirectGroupBucket(treatmentBuilder);
        GroupBuckets buckets = new GroupBuckets(List.of(bucket));
        GroupKey key = new DefaultGroupKey(ByteBuffer.allocate(4).putInt(myGroupId).array());
        GroupDescription groupDesc = new DefaultGroupDescription(DeviceId.deviceId(swid), GroupDescription.Type.INDIRECT, buckets, key, myGroupId, appId);
        groupService.addGroup(groupDesc);
    }
    public void flowRuleObjectiveDemo() {
        installInterfaceGroup(1, "of:0000000000000002", 1);
        installInterfaceGroup(2, "of:0000000000000002", 2);

        installFlowRule(Ethernet.TYPE_ARP, "10.0.0.2", 2, "of:0000000000000001");
        installFlowRule(Ethernet.TYPE_ARP, "10.0.0.2", 1, "of:0000000000000002");
        installFlowRule(Ethernet.TYPE_ARP, "10.0.0.1", 2, "of:0000000000000002");
        installFlowRule(Ethernet.TYPE_ARP, "10.0.0.1", 1, "of:0000000000000001");

        installFlowRule(Ethernet.TYPE_IPV4, "10.0.0.2/32", 2, "of:0000000000000001");
        installFlowRule(Ethernet.TYPE_IPV4, "10.0.0.2/32", 1, "of:0000000000000002");
        installFlowRule(Ethernet.TYPE_IPV4, "10.0.0.1/32", 2, "of:0000000000000002");
        installFlowRule(Ethernet.TYPE_IPV4, "10.0.0.1/32", 1, "of:0000000000000001");
    }

    private void installInterfaceGroup(int outPort, String did, int gid) {
        TrafficTreatment tt = DefaultTrafficTreatment.builder()
                .setOutput(PortNumber.portNumber(outPort))
                .build();
        GroupBucket bucket = DefaultGroupBucket.createIndirectGroupBucket(tt);
        GroupBuckets buckets = new GroupBuckets(List.of(bucket));
        GroupKey key = new DefaultGroupKey(ByteBuffer.allocate(4).putInt(gid).array());
        GroupDescription groupDesc = new DefaultGroupDescription(DeviceId.deviceId(did), GroupDescription.Type.INDIRECT, buckets, key, gid, appId);
        groupService.addGroup(groupDesc);
    }

    private void removeInterfaceGroups() {
        DeviceId did = DeviceId.deviceId("of:0000000000000002");
        GroupKey key = new DefaultGroupKey(ByteBuffer.allocate(4).putInt(1).array());
        groupService.removeGroup(did, key, appId);
        key = new DefaultGroupKey(ByteBuffer.allocate(4).putInt(2).array());
        groupService.removeGroup(did, key, appId);
    }

    private void installFlowRule(short ethType, String dstIp, int outPort, String did) {
        TrafficSelector.Builder tsb = DefaultTrafficSelector.builder().matchEthType(ethType);
        if (ethType == Ethernet.TYPE_ARP) {
            tsb.matchArpTpa(Ip4Address.valueOf(dstIp));
        } else {
            tsb.matchIPDst(IpPrefix.valueOf(dstIp));
        }

        boolean isOfdpa = did.equals("of:0000000000000002");
        TrafficTreatment.Builder ttb = DefaultTrafficTreatment.builder();
        if (isOfdpa) {
            ttb.group(new GroupId(outPort));
        } else {
            ttb.setOutput(PortNumber.portNumber(outPort));
        }
        forwardingObjective = DefaultForwardingObjective.builder()
        .withSelector(tsb.build()).withTreatment(ttb.build())
        .withFlag(ForwardingObjective.Flag.SPECIFIC).withPriority(40001)
        .fromApp(appId).makePermanent().add();
        FlowRule flow = DefaultFlowRule.builder()
                .forDevice(DeviceId.deviceId(did))
                .makePermanent()
                .withPriority(40001)
                .forTable(isOfdpa ? 60 : 0)
                .withSelector(forwardingObjective.selector())
                .withTreatment(forwardingObjective.treatment())
                .fromApp(appId)
                .build();
        flowRuleService.applyFlowRules(flow);
        // flowObjectiveService.forward(DeviceId.deviceId(did), forwardingObjective);
    }
    /*private void installRule() {
        installGroupForSW2(1, "of:0000000000000002", 1);
        installGroupForSW2(2, "of:0000000000000002", 2);
        installFlowForSW1();
        installFlowForSW1();

        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        TrafficSelector.Builder selectorBuilder2 = DefaultTrafficSelector.builder();
        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();
        TrafficTreatment.Builder treatmentBuilder2 = DefaultTrafficTreatment.builder();
        PortNumber Num1 = PortNumber.portNumber(1);
        PortNumber Num2 = PortNumber.portNumber(2);
        selectorBuilder.matchInPort(Num1).matchEthType(Ethernet.TYPE_IPV4);
        selectorBuilder2.matchInPort(Num2).matchEthType(Ethernet.TYPE_IPV4);
        treatmentBuilder.setOutput(Num2);
        treatmentBuilder2.setOutput(Num1);

        forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder.build()).withTreatment(treatmentBuilder.build())
                .withPriority(40001).withFlag(ForwardingObjective.Flag.VERSATILE).fromApp(appId)
                .makeTemporary(20000).add();

        forwardingObjective2 = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder2.build()).withTreatment(treatmentBuilder2.build())
                .withPriority(40001).withFlag(ForwardingObjective.Flag.VERSATILE).fromApp(appId)
                .makeTemporary(20000).add();        
        flowObjectiveService.forward(DeviceId.deviceId("of:0000000000000001"), forwardingObjective);
        flowObjectiveService.forward(DeviceId.deviceId("of:0000000000000001"), forwardingObjective2);
        flowObjectiveService.forward(DeviceId.deviceId("of:0000000000000002"), forwardingObjective);
        flowObjectiveService.forward(DeviceId.deviceId("of:0000000000000002"), forwardingObjective2);
    }
    private void removeFlow()
    {
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        TrafficSelector.Builder selectorBuilder2 = DefaultTrafficSelector.builder();
        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();
        TrafficTreatment.Builder treatmentBuilder2 = DefaultTrafficTreatment.builder();
        PortNumber Num1 = PortNumber.portNumber(1);
        PortNumber Num2 = PortNumber.portNumber(2);
        selectorBuilder.matchInPort(Num1).matchEthType(Ethernet.TYPE_IPV4);
        selectorBuilder2.matchInPort(Num2).matchEthType(Ethernet.TYPE_IPV4);
        treatmentBuilder.setOutput(Num2);
        treatmentBuilder2.setOutput(Num1);

        forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder.build()).withTreatment(treatmentBuilder.build())
                .withPriority(40001).withFlag(ForwardingObjective.Flag.VERSATILE).fromApp(appId)
                .makeTemporary(20000).remove();

        forwardingObjective2 = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder2.build()).withTreatment(treatmentBuilder2.build())
                .withPriority(40001).withFlag(ForwardingObjective.Flag.VERSATILE).fromApp(appId)
                .makeTemporary(20000).remove();        
        flowObjectiveService.forward(DeviceId.deviceId("of:0000000000000001"), forwardingObjective);
        flowObjectiveService.forward(DeviceId.deviceId("of:0000000000000001"), forwardingObjective2);
        flowObjectiveService.forward(DeviceId.deviceId("of:0000000000000002"), forwardingObjective);
        flowObjectiveService.forward(DeviceId.deviceId("of:0000000000000002"), forwardingObjective2);
    }*/
    @Deactivate
    protected void deactivate() {
        // removeFlow();
        flowRuleService.removeFlowRulesById(appId);
        removeInterfaceGroups();
        log.info("Demo FlowObjective App Stopped");
    }


}
