# Flow Objective
## 程式碼

## 流程
1. 將onos開起來
```
$ ok clean
```
2. 將mininet拓樸建起來
```
$ sudo mn --topo=linear,2 --controller=remote,ip=127.0.0.1:6653
```
3. 打開proxy arp 
```
$ onos localhost app activate proxyarp
```
4. 進到flowobjective app資料夾編譯
```
$ cd ~/demo-for-flowob && maven clean install -DskipTests
```
5. 將app裝在onos上
```
$ onos-app localhost install! target/demo-for-flowob-1.0-SNAPSHOT.oar
```
6. 進onos web gui查看flow是否有下成功
```
網址列輸入: http://localhost:8181/onos/ui/#/flow?devId=of:0000000000000001
```
7. 將app移除
```
$ onos localhost app deactivate org.flowob.app
```
8. 進onos web gui查看flow是否有被刪掉
```
網址列輸入: http://localhost:8181/onos/ui/#/flow?devId=of:0000000000000001
```
## install flow function
``` java=
private void installFlow(){
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder(); // match field for inport = 1
        TrafficSelector.Builder selectorBuilder2 = DefaultTrafficSelector.builder();// match field for inport = 2
        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();// action field for outport = 2
        TrafficTreatment.Builder treatmentBuilder2 = DefaultTrafficTreatment.builder();// action field for outport = 1
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
        // for sw 1
        flowObjectiveService.forward(DeviceId.deviceId("of:0000000000000001"), forwardingObjective);
        flowObjectiveService.forward(DeviceId.deviceId("of:0000000000000001"), forwardingObjective2);
        // for sw 2
        flowObjectiveService.forward(DeviceId.deviceId("of:0000000000000002"), forwardingObjective);
        flowObjectiveService.forward(DeviceId.deviceId("of:0000000000000002"), forwardingObjective2);
        }
```

## delelte flow 
* 將install flow function中，第16及21行的add()換成remove，其餘程式碼一樣
``` java=
private void installFlow(){
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder(); // match field for inport = 1
        TrafficSelector.Builder selectorBuilder2 = DefaultTrafficSelector.builder();// match field for inport = 2
        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();// action field for outport = 2
        TrafficTreatment.Builder treatmentBuilder2 = DefaultTrafficTreatment.builder();// action field for outport = 1
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
        // for sw 1
        flowObjectiveService.forward(DeviceId.deviceId("of:0000000000000001"), forwardingObjective);
        flowObjectiveService.forward(DeviceId.deviceId("of:0000000000000001"), forwardingObjective2);
        // for sw 2
        flowObjectiveService.forward(DeviceId.deviceId("of:0000000000000002"), forwardingObjective);
        flowObjectiveService.forward(DeviceId.deviceId("of:0000000000000002"), forwardingObjective2);
        }
```
