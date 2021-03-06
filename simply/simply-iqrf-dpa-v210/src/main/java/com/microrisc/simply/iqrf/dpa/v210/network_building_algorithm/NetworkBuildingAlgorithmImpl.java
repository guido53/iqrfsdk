/*
 * Copyright 2014 MICRORISC s.r.o..
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.microrisc.simply.iqrf.dpa.v210.network_building_algorithm;

import com.microrisc.simply.BaseNetwork;
import com.microrisc.simply.CallRequestProcessingState;
import com.microrisc.simply.DeviceInterfaceMethodId;
import com.microrisc.simply.Network;
import com.microrisc.simply.errors.CallRequestProcessingError;
import com.microrisc.simply.iqrf.dpa.broadcasting.BroadcastResult;
import com.microrisc.simply.iqrf.dpa.broadcasting.services.BroadcastServices;
import com.microrisc.simply.iqrf.dpa.protocol.ProtocolObjects;
import com.microrisc.simply.iqrf.dpa.v210.devices.Coordinator;
import com.microrisc.simply.iqrf.dpa.v210.devices.FRC;
import com.microrisc.simply.iqrf.dpa.v210.devices.LEDR;
import com.microrisc.simply.iqrf.dpa.v210.devices.Node;
import com.microrisc.simply.iqrf.dpa.v210.devices.OS;
import com.microrisc.simply.iqrf.dpa.v210.init.NodeFactory;
import com.microrisc.simply.iqrf.dpa.v210.protocol.DPA_ProtocolProperties;
import com.microrisc.simply.iqrf.dpa.v210.types.BondedNode;
import com.microrisc.simply.iqrf.dpa.v210.types.BondedNodes;
import com.microrisc.simply.iqrf.dpa.v210.types.DPA_Parameter;
import com.microrisc.simply.iqrf.dpa.v210.types.DPA_Request;
import com.microrisc.simply.iqrf.dpa.v210.types.DiscoveredNodes;
import com.microrisc.simply.iqrf.dpa.v210.types.DiscoveryParams;
import com.microrisc.simply.iqrf.dpa.v210.types.DiscoveryResult;
import com.microrisc.simply.iqrf.dpa.v210.types.FRC_Data;
import com.microrisc.simply.iqrf.dpa.v210.types.FRC_Prebonding;
import com.microrisc.simply.iqrf.dpa.v210.types.LED_State;
import com.microrisc.simply.iqrf.dpa.v210.types.RemotelyBondedModuleId;
import com.microrisc.simply.iqrf.dpa.v210.types.RoutingHops;
import com.microrisc.simply.iqrf.types.VoidType;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link NetworkBuildingAlgorithm} interface.
 * 
 * @author Michal Konopa
 */
public final class NetworkBuildingAlgorithmImpl implements NetworkBuildingAlgorithm {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(NetworkBuildingAlgorithmImpl.class);
    
    
    /** Minimal discovery TX power. */
    public static final int DISCOVERY_TX_POWER_MIN = 0;
    
    /** Maximal discovery TX power. */
    public static final int DISCOVERY_TX_POWER_MAX = 7;
    
    /** Default discovery TX. */
    public static final int DISCOVERY_TX_POWER_DEFAULT = 4;
    
    /** Default prebonding interval [ in ms ]. */
    public static final long PREBONDING_INTERVAL_DEFAULT = 10000;
    
    /** Default number of retries to authorize new bonded node. */
    public static final int AUTHORIZE_RETRIES_DEFAULT = 1;
    
    /** Default number of retries to run the discovery process after one iteration of algorithm. */
    public static final int DISCOVERY_RETRIES_DEFAULT = 1;
    
    /** Default timeout for node to hold temporary address [in ms]. */
    public static final long TEMPORARY_ADDRESS_TIMEOUT_DEFAULT = 100000;
    
    /** 
     * Default indicator, wheather to use FRC automatically in checking 
     * the accessibility of new bonded nodes. 
     */
    public static final boolean AUTOUSE_FRC_DEFAULT = true;
    
    
    // TX power for discovery process
    private final int discoveryTxPower;
    
    // time interval [in ms] for prebonding
    private final long prebondingInterval;
    private final int authorizeRetries;
    private final int discoveryRetries;
    
    // timeout [in ms] for holding temporary address
    private final long temporaryAddressTimeout;
    
    // use FRC automatically in checking accessibility of new bonded nodes
    private final boolean autoUseFrc;
    
    
    // checkers
    private static int checkDiscoveryTxPower(int discoveryTxPower) {
        if ( ( discoveryTxPower < DISCOVERY_TX_POWER_MIN ) 
              || ( discoveryTxPower > DISCOVERY_TX_POWER_MAX ) 
        ) {
            throw new IllegalArgumentException(
                "Discovery TX power must be in the " + DISCOVERY_TX_POWER_MIN 
                + ".."  + DISCOVERY_TX_POWER_MAX + "interval."
            );
        }
        return discoveryTxPower;
    }
    private static long checkPrebondingInterval(long prebondingInterval) {
        if ( prebondingInterval < 0 ) {
            throw new IllegalArgumentException("Prebonding interval cannot be negative.");
        }
        return prebondingInterval;
    }
    
    private static int checkAuthorizeRetries(int authorizeRetries) {
        if ( authorizeRetries < 0 ) {
            throw new IllegalArgumentException(
                "Number of retries to authorize new bonded node cannot be negative."
            );
        }
        return authorizeRetries;
    }
    
    private static int checkDiscoveryRetries(int discoveryRetries) {
        if ( discoveryRetries < 0 ) {
            throw new IllegalArgumentException(
                "Number of discovery retries cannot be negative."
            );
        }
        return discoveryRetries;
    }
    
    private static long checkTemporaryAddressTimeout(long temporaryAddressTimeout) {
        if ( temporaryAddressTimeout < 0 ) {
            throw new IllegalArgumentException(
                "Temporary address timeout cannot be negative."
            );
        }
        return temporaryAddressTimeout;
    }
    
    /**
     * Builder for {@code NetworkBuildingAlgorithmImpl} class.
     */
    public static class Builder {
        // required parameters
        private final Network network;
        private final BroadcastServices broadcastServices;
        
        // optional parameters
        private int discoveryTxPower = DISCOVERY_TX_POWER_DEFAULT;
        private long prebondingInterval = PREBONDING_INTERVAL_DEFAULT;
        private int authorizeRetries = AUTHORIZE_RETRIES_DEFAULT;
        private int discoveryRetries = DISCOVERY_RETRIES_DEFAULT;
        private long temporaryAddressTimeout = TEMPORARY_ADDRESS_TIMEOUT_DEFAULT;
        private boolean autoUseFrc = AUTOUSE_FRC_DEFAULT;
       
        
        public Builder(Network network, BroadcastServices broadcastServices) {
            this.network = network;
            this.broadcastServices = broadcastServices;
        }
        
        public Builder discoveryTxPower(int val) {
            this.discoveryTxPower = val;
            return this;
        }
        
        public Builder prebondingInterval(long val) {
            this.prebondingInterval = val;
            return this;
        }
        
        public Builder authorizeRetries(int val) {
            this.authorizeRetries = val;
            return this;
        }
        
        public Builder discoveryRetries(int val) {
            this.discoveryRetries = val;
            return this;
        }
        
        public Builder temporaryAddressTimeout(long val) {
            this.temporaryAddressTimeout = val;
            return this;
        }
        
        public Builder autoUseFrc(boolean val) {
            this.autoUseFrc = val;
            return this;
        }
        
        public NetworkBuildingAlgorithmImpl build() {
            return new NetworkBuildingAlgorithmImpl(this);
        }
    }
    
    
    // network allowing dynamic changes in its structure
    private static class DynamicNetwork implements Network {
        private final String id;
        private final Map<String, com.microrisc.simply.Node> nodesMap;
    
    
        public DynamicNetwork(String id, Map<String, com.microrisc.simply.Node> nodesMap) {
            this.id = id;
            this.nodesMap = nodesMap;
        }
    
        @Override
        public String getId() {
            return id;
        }

        @Override
        public com.microrisc.simply.Node getNode(String nodeId) {
            return nodesMap.get(nodeId);
        }

        @Override
        public Map<String, com.microrisc.simply.Node> getNodesMap() {
            return new HashMap<>(nodesMap);
        }
        
        public void addNode(com.microrisc.simply.Node node) {
            nodesMap.put(node.getId(), node);
        }
        
        public void destroy() {
            nodesMap.clear();
        }
        
    }
    
    /** Network to start the algorithm with. */
    private final Network network;
    
    private static Network checkNetwork(Network network) {
        if ( network == null ) {
            throw new IllegalArgumentException("Initial network cannot be null");
        }
        return network;
    }
    
    /** Result network. */
    private final DynamicNetwork resultNetwork;
    
    /** Synchronization object for resultNetwork. */
    private final Object synchroResultNetwork = new Object();
  
    // returns copy of the specified network
    private Network getNetworkCopy(Network srcNetwork) {
        return new BaseNetwork( srcNetwork.getId(), new HashMap<>(srcNetwork.getNodesMap()) );
    }
    
    // creates dynamic network from source network
    private DynamicNetwork createDynamicNetwork(Network srcNetwork) {
        return new DynamicNetwork( srcNetwork.getId(), new HashMap<>(srcNetwork.getNodesMap()) );
    }
     
    // broadcast services
    private final BroadcastServices broadcastServices;
    
    private static BroadcastServices checkBroadcastServices(BroadcastServices broadcastServices) 
    {
        if ( broadcastServices == null ) {
            throw new IllegalArgumentException("Broadcast services cannot be null");
        }
        return broadcastServices;
    }
    
    
    /**
     * State of the algorithm
     */
    public static enum State {
        PREPARED,
        RUNNING,
        FINISHED,
        CANCELLED,
        ERROR
    }
    
    // actual state of the algorithm
    private State actualState = State.PREPARED;
    
    // synchronization object for actualState
    private final Object synchroActualState = new Object();
    
    // sets the actual state
    private void setState(State newState) {
        synchronized ( synchroActualState ) {
            actualState = newState;
        }
    }
    
    
    // thread running the algorithm
    private class AlgoThread extends Thread {
        @Override
        public void run() {
            runAlgorithm();
        }
    }
    
    // algo thread
    private Thread algoThread = null;
    
    // timeout to wait for worker threads to join
    private static final long JOIN_WAIT_TIMEOUT = 2000;
    
    /**
     * Terminates algo thread.
     */
    private void terminateAlgoThread() {
        logger.debug("terminateAlgoThread - start:");
        
        // termination signal to algo thread
        algoThread.interrupt();
        
        // indicates, wheather this thread is interrupted
        boolean isInterrupted = false;
        
        try {
            if ( algoThread.isAlive( )) {
                algoThread.join(JOIN_WAIT_TIMEOUT);
            }
        } catch ( InterruptedException e ) {
            isInterrupted = true;
            logger.warn("Algo thread terminating - thread interrupted");
        }
        
        if ( !algoThread.isAlive() ) {
            logger.info("Algo thread stopped.");
        }
        
        if ( isInterrupted ) {
            Thread.currentThread().interrupt();
        }
        
        logger.info("algo thread stopped.");
        logger.debug("terminateAlgoThread - end");
    }
    
    
    
    /** Bonded nodes. */
    private BondedNodes bondedNodes = null;
    
    /** Dsicovered nodes. */
    private DiscoveredNodes discoveredNodes = null;
    
    
    // returns next free address
    private int nextFreeAddr(BondedNodes bondedNodes, int from ) throws Exception {
        int origAddr = from;

        for ( ; ; ) {
            if ( ++from > DPA_ProtocolProperties.NADR_Properties.IQMESH_NODE_ADDRESS_MAX ) {
                from = 1;
            }
            
            if ( origAddr == from ) {
                throw new Exception( "NextFreeAddr: no free address" );
            }
            
            if ( !bondedNodes.isBonded(from) ) {
                return from;
            }
        }
    }
    
    
    // updates information about nodes
    private void updateNodesInfo(Coordinator coordinator) throws Exception {
        this.bondedNodes = coordinator.getBondedNodes();
        if ( bondedNodes == null ) {
            throw new Exception("Error while getting bonded nodes.");
        }
        
        logger.info("Bonded nodes: {}", this.bondedNodes.bondedNodesListToString());
        
        this.discoveredNodes = coordinator.getDiscoveredNodes();
        if ( discoveredNodes == null ) {
            throw new Exception("Error while getting discovered nodes.");
        }
        
        logger.info("Discovered nodes: {}", this.discoveredNodes.discoveredNodesListToString());
        
        List<Integer> notDiscovered = new LinkedList<>();
        for ( int bondedNodeId : bondedNodes.getList() ) {
            if ( !discoveredNodes.isDiscovered(bondedNodeId) ) {
                notDiscovered.add(bondedNodeId);
            }
        }
        logger.info("NOT discovered nodes: {}", StringUtils.join(notDiscovered, ','));
    }
    
    // checks, if there are some discovered nodes which are not bonded
    private boolean checkUnbondedNodes () {
        List<Integer> notBonded = new LinkedList<>();
        for ( int nodeAddr = 1; 
              nodeAddr <= DPA_ProtocolProperties.NADR_Properties.IQMESH_NODE_ADDRESS_MAX; 
              nodeAddr++ 
        ) {
            if ( discoveredNodes.isDiscovered(nodeAddr) && !bondedNodes.isBonded(nodeAddr) ) {
                notBonded.add(nodeAddr);
            }
        }
        
        if ( !notBonded.isEmpty() ) {
            logger.warn("Nodes {} are discovered but not bonded. Discover the network!", 
                    StringUtils.join(notBonded, ',')
            );
            return false;
        }
        return true;
    }
    
    // returns value of logarithm of base 2 for the specified value
    private int log2(int value) {
        if ( value == 0 ) {
            return 0;
        }
        return 31 - Integer.numberOfLeadingZeros(value);
    }
    
    // puts together both parts of incomming FRC data
    private static short[] getCompleteFrcData(short[] firstPart, short[] extraData) {
        short[] completeData = new short[firstPart.length + extraData.length];
        System.arraycopy(firstPart, 0, completeData, 0, firstPart.length);
        System.arraycopy(extraData, 0, completeData, firstPart.length, extraData.length);
        return completeData;
    }
    
    // comparing Node Ids
    private static class NodeIdComparator implements Comparator<String> {
        @Override
        public int compare(String nodeIdStr1, String nodeIdStr2) {
            int nodeId_1 = Integer.decode(nodeIdStr1);
            int nodeId_2 = Integer.decode(nodeIdStr2);
            return Integer.compare(nodeId_1, nodeId_2);
        }
    }
    
    // Node Id comparator
    private static final NodeIdComparator nodeIdComparator = new NodeIdComparator();
    
    // sorting specified results according to node ID in ascendent manner
    private static SortedMap<String, FRC_Prebonding.Result> sortFrcResult(
            Map<String, FRC_Prebonding.Result> result
    ) {
        TreeMap<String, FRC_Prebonding.Result> sortedResult = new TreeMap<>(nodeIdComparator);
        sortedResult.putAll(result);
        return sortedResult;
    }
    
    
    // stores information about elements used to send P2P packet to allow prebonding
    private static class P2PPrebondingInfo {
        Class p2pSender;
        DeviceInterfaceMethodId methodId;
        
        P2PPrebondingInfo(Class p2pSender, DeviceInterfaceMethodId methodId) {
            this.p2pSender = p2pSender;
            this.methodId = methodId;
        }
    }
    
    private static final int FIRST_USER_PERIPHERAL = 0x20;
    
    // returns method ID annotated as P2PSenderMethodId 
    private DeviceInterfaceMethodId getP2PSenderMethodId(Class p2pSender) {
        // find annotated Method enum constant
        Class[] pubClasses = p2pSender.getClasses();
        for ( Class pubClass : pubClasses ) {
            if ( !pubClass.isEnum() ) {
                continue;
            }

            // get implemented interfaces
            Class[] ifaces = pubClass.getInterfaces();
            
            for ( Class iface : ifaces ) {
                if ( iface != DeviceInterfaceMethodId.class ) {
                    continue;
                }

                Field[] fields = pubClass.getFields();
                for ( Field field : fields ) {
                    if ( field.getAnnotation(P2PSenderMethodId.class) != null ) {
                        return (DeviceInterfaceMethodId)Enum.valueOf(pubClass, field.getName());
                    }
                }
            }
        }
        return null;
    }
    
    private P2PPrebondingInfo getP2PPrebondingInfo() throws Exception {
       Class p2pSender = ProtocolObjects.getPeripheralToDevIfaceMapper().
               getDeviceInterface(FIRST_USER_PERIPHERAL);
       if ( p2pSender == null ) {
           throw new Exception("Could not found first user peripheral.");
       }
       
       DeviceInterfaceMethodId methodId = getP2PSenderMethodId(p2pSender);
       if ( methodId == null ) {
           throw new Exception("Could not found P2PSenderMethodId.");
       }
       
       return new P2PPrebondingInfo(p2pSender, methodId);
    }
    
    // activates prebonding on coordinator and nodes
    private void prebond(OS coordOs, Coordinator coordinator, P2PPrebondingInfo p2pInfo) 
            throws Exception 
    {
        int bondingMask = (bondedNodes.getNodesNumber() == 0) ? 
            0 : (int)Math.pow( 2, 1 + (int)log2( bondedNodes.getNodesNumber()) ) - 1;
        if ( bondingMask > 0xFF ) {
            bondingMask = 0xFF;
        }

        int wTimeout = (int)( ( (long)temporaryAddressTimeout * 1000 ) / 10 );
        int waitBonding = bondedNodes.getNodesNumber() * 1;

        // how long to wait for prebonding [ in seconds ]
        waitBonding = (int)Math.min( Math.max( 10, waitBonding ), prebondingInterval );
        int waitBonding10ms = waitBonding * 100;

        logger.info(
            "Enable prebonding, mask = {}, time = {}, and LEDR=1 at Nodes and Coordinator",
            Integer.toBinaryString(bondingMask), temporaryAddressTimeout
        );
        
        UUID nodesEnableUid = null;
        if ( bondedNodes.getNodesNumber() > 0 ) {
            String networkId = null;
            synchronized ( synchroResultNetwork ) {
                networkId = resultNetwork.id;
            }
            
            nodesEnableUid = broadcastServices.sendRequest(
                networkId, 
                OS.class, 
                OS.MethodID.BATCH, 
                new DPA_Request[] {
                    new DPA_Request(
                            LEDR.class, 
                            LEDR.MethodID.SET, 
                            new Object[] { LED_State.ON }, 
                            0xFFFF 
                    ),
                    new DPA_Request(
                            Node.class, 
                            Node.MethodID.ENABLE_REMOTE_BONDING, 
                            new Object[] { 
                                bondingMask, 
                                1, 
                                new short[] { 
                                    (short)(wTimeout >> 0), 
                                    (short)(wTimeout >> 8)
                                }
                            }, 
                            0xFFFF 
                    ),
                    new DPA_Request(
                            p2pInfo.p2pSender, 
                            p2pInfo.methodId, 
                            new Object[] { 
                                new short[] { 
                                    0x55, 
                                    (short)(waitBonding10ms >> 0 ), 
                                    (short)(waitBonding10ms >> 8 ), 
                                    (short)(bondedNodes.getNodesNumber() + 2) 
                                } 
                            }, 
                            0xFFFF 
                    )
                }
            );
            
            if ( nodesEnableUid == null ) {
                throw new Exception(
                        "Error while sending request for enabling remote bonding on nodes"
                );
            }
        }

        UUID coordEnableUid = coordOs.call(
                OS.MethodID.BATCH, 
                new Object[] { 
                    new DPA_Request[] { 
                        new DPA_Request(
                                Coordinator.class, 
                                Coordinator.MethodID.ENABLE_REMOTE_BONDING,
                                new Object[] { 
                                    bondingMask, 
                                    1, 
                                    new short[] { 
                                        (short)(wTimeout >> 0), 
                                        (short)(wTimeout >> 8)
                                    }
                                }, 
                                0xFFFF 
                        ),
                        new DPA_Request(
                                p2pInfo.p2pSender, 
                                p2pInfo.methodId, 
                                new Object[] { 
                                    new short[] { 
                                        0x55, 
                                        (short)(waitBonding10ms >> 0 ), 
                                        (short)(waitBonding10ms >> 8 ), 
                                        1 
                                    } 
                                }, 
                                0xFFFF 
                        )
                    }
                } 
        );
        
        if ( coordEnableUid == null ) {
            throw new Exception(
                    "Error while sending request for enabling remote bonding on coordinator"
            );
        }

        logger.info("Waiting for prebonding for {} seconds ...", waitBonding);

        try {
            Thread.sleep(waitBonding * 1000 + 1000);
        } catch ( InterruptedException ex ) {
            logger.error("Prebonding interrupted");
            if ( bondedNodes.getNodesNumber() > 0 ) {
                logger.info("Disable prebonding at nodes");
                
                String networkId = null;
                synchronized ( synchroResultNetwork ) {
                    networkId = resultNetwork.id;
                }
                BroadcastResult nodesDisablingResult = broadcastServices.broadcast(
                    networkId, Node.class, Node.MethodID.ENABLE_REMOTE_BONDING,
                        new Object[] { 0, 0, new short[] { 0 } }
                );
                if ( nodesDisablingResult == null ) {
                    throw new Exception("Error while disabling remote bonding on nodes");
                }
            }

            logger.info("Disable coordinator prebonding");
            VoidType coordDisablingResult = coordinator.enableRemoteBonding(0, 0, new short[] { 0 });
            if ( coordDisablingResult == null ) {
                throw new Exception("Error while disabling remote bonding on coordinator");
            }
        }
        
        // getting results of enabling remote bonding 
        BroadcastResult nodesEnableResult = broadcastServices.getBroadcastResultImmediately(nodesEnableUid);
        if ( nodesEnableResult == null ) {
            throw new Exception(
                    "Result not available for enabling remote bonding on nodes. "
                    + "Current state: " + broadcastServices.getCallRequestProcessingState(nodesEnableUid)
            );
        }
        
        VoidType coordEnableResult = coordOs.getCallResultImmediately(coordEnableUid, VoidType.class);
        if ( coordEnableResult == null ) {
            throw new Exception(
                    "Result not available for enabling remote bonding on coordinator. "
                    + "Current state: " + coordOs.getCallRequestProcessingState(coordEnableUid)
            );
        }
    }
    
    // disables prebonding and returns results of the disabling request
    private Map<String, FRC_Prebonding.Result> disablePrebonding(com.microrisc.simply.Node coordNode) 
            throws Exception 
    {
        FRC coordFrc = coordNode.getDeviceObject(FRC.class);
        if ( coordFrc == null ) {
            throw new Exception("Could not find FRC on coordinator node.");
        }
            
        FRC_Data frcData = coordFrc.send( new FRC_Prebonding( new short[] { 0x01, 0x00 }) );
        if ( frcData == null ) {
            throw new Exception("Error while disabling prebonding.");
        }
        
        short[] extraData = coordFrc.extraResult();
        if ( extraData == null ) {
            throw new Exception("Error while disabling prebonding - getting extra data.");
        }

        return FRC_Prebonding.parse(getCompleteFrcData(frcData.getData(), extraData));
    }
    
    // returns list of nodes, which provided prebonding
    private List<Integer> getPrebondingNodes(Map<String, FRC_Prebonding.Result> frcResult) {
        List<Integer> prebondingNodes = new LinkedList<>();
        for ( int nodeAddr = 1;
              nodeAddr <= DPA_ProtocolProperties.NADR_Properties.IQMESH_NODE_ADDRESS_MAX;
              nodeAddr++
        ) {
            FRC_Prebonding.Result nodeResult = frcResult.get(Integer.toString(nodeAddr));
            if ( nodeResult.getBit1() == 0x01 ) {
                prebondingNodes.add(nodeAddr);
            }
        }
        return prebondingNodes;
    }
    
    // reads prebonded MIDs from prebonding nodes and adds them into specified list 
    private void addPrebondedMIDsFromPrebondingNodes(
            List<Integer> prebondingNodes, List<RemotelyBondedModuleId> prebondedMIDs
    ) throws Exception {
        for ( int nodeAddr : prebondingNodes ) {
            com.microrisc.simply.Node node = null;
            synchronized ( synchroResultNetwork ) {
                node = resultNetwork.getNode(Integer.toString(nodeAddr));
            }
            
            if ( node == null ) {
                throw new Exception("Node " + nodeAddr + " not available.");
            }

            Node nodeIface = node.getDeviceObject(Node.class);
            if ( nodeIface == null ) {
                throw new Exception("Node interface at " + nodeAddr + " node not found.");
            }

            RemotelyBondedModuleId remoBondedModuleId = nodeIface.readRemotelyBondedModuleId();
            if ( remoBondedModuleId == null ) {
                logger.error("Error reading prebonded MID from node {}", nodeAddr);
                continue;
            }

            logger.info("Node {} prebonded MID={}, UserData={}", 
                    nodeAddr, remoBondedModuleId.getModuleId(),
                    remoBondedModuleId.getUserData()
            );

            if ( !prebondedMIDs.contains(remoBondedModuleId) ) {
                prebondedMIDs.add(remoBondedModuleId);
            }
        }
    }
    
    // returns the list of prebonded MIDs
    private List<RemotelyBondedModuleId> getPrebondedMIDs(
            Coordinator coordinator, com.microrisc.simply.Node coordNode
    ) throws Exception 
    {
        List<RemotelyBondedModuleId> prebondedMIDs = new LinkedList<>();
        
        RemotelyBondedModuleId remoBondedModuleId = coordinator.readRemotelyBondedModuleId();
        if ( remoBondedModuleId == null ) {
            throw new Exception("Error while reading remotely bonded moduleID from coordinator");
        }
        
        logger.info(
                "Coordinator prebonded MID={}, UserData={}", 
                Arrays.toString(remoBondedModuleId.getModuleId()), 
                Arrays.toString(remoBondedModuleId.getUserData())
        );
                
        prebondedMIDs.add(remoBondedModuleId);
        
        if ( bondedNodes.getNodesNumber() == 0 ) {
            return prebondedMIDs;
        }
        
        logger.info("Running FRC to disable and check for prebonding");
        
        Map<String, FRC_Prebonding.Result> prebondDisablingResult = disablePrebonding(coordNode);
                    
        // sort the disabling result
        SortedMap<String, FRC_Prebonding.Result> sortedPrebondDisablingResult 
                = sortFrcResult(prebondDisablingResult);

        // logging prebonding info on each node
        for ( Map.Entry<String, FRC_Prebonding.Result> dataEntry : sortedPrebondDisablingResult.entrySet()
        ) {
            logger.info(
                "Node: {}, bit0: {}, bit.1: {}", 
                dataEntry.getKey(), dataEntry.getValue().getBit0(), dataEntry.getValue().getBit1()
            );
        }
        
        // getting prebonding nodes
        List<Integer> prebondingNodes = getPrebondingNodes(sortedPrebondDisablingResult);        
        if ( prebondingNodes.isEmpty() ) {
            logger.info("No node prebonded.");
            return prebondedMIDs;
        }
        
        logger.info("Nodes provided prebonding: {}" + StringUtils.join(prebondingNodes, ',')) ;
        
        // adding prebonded MIDs from prebonding nodes
        addPrebondedMIDsFromPrebondingNodes(prebondingNodes, prebondedMIDs);
        
        return prebondedMIDs;
    }
    
    
    // authorizes bonds
    private List<Integer> authorizeBonds(
            Coordinator coordinator, List<RemotelyBondedModuleId> prebondedMIDs
    ) throws Exception {
        List<Integer> newAddrs = new LinkedList<>();
        int nextAddr = DPA_ProtocolProperties.NADR_Properties.IQMESH_NODE_ADDRESS_MAX;

        for ( RemotelyBondedModuleId moduleId : prebondedMIDs ) {
            for ( int authorizeRetry = authorizeRetries; authorizeRetry != 0; authorizeRetry-- ) {
                if ( authorizeRetry == authorizeRetries ) {
                    nextAddr = nextFreeAddr(bondedNodes, nextAddr);
                }

                UUID authorizeBondUid = coordinator.call(
                        Coordinator.MethodID.AUTHORIZE_BOND, 
                        new Object[] { nextAddr, moduleId.getModuleId() } 
                );
                
                if ( authorizeBondUid == null ) {
                    throw new Exception(
                        "Error while doing authorizing bond request."
                         + "Module ID: " + moduleId
                    );
                }
                
                // waiting with the possibility of interruption
                Thread.sleep(bondedNodes.getNodesNumber() * 40 + 150);
                
                // getting authorization result
                BondedNode bondedNode = coordinator.getCallResultImmediately(authorizeBondUid, BondedNode.class);
                
                if ( bondedNode != null ) {
                    logger.info(
                        "Authorizing node {}, address={}, devices count={}, waiting to finish authorization...", 
                        Arrays.toString(moduleId.getModuleId()), 
                        bondedNode.getBondedAddress(), 
                        bondedNode.getBondedNodesNum()
                    );
                } else {
                    logger.error("Authorizing node {}, error", moduleId );
                    continue;
                }

                // what to do with pinging - omit completely?
                if ( authorizeRetry != 1 ) {
                    Integer devCount = coordinator.removeBondedNode(nextAddr);
                    if ( devCount == null ) {
                        logger.error("Error remove bond {}");
                    } else {
                        newAddrs.add(Integer.valueOf(bondedNode.getBondedAddress()));
                        updateNodesInfo(coordinator);
                    }
                }
            }
        }
        return newAddrs;
    }
    
    // checks new nodes, removes the nonresponding ones
    private void checkNewNodes(
            Coordinator coordinator, com.microrisc.simply.Node coordNode, List<Integer> newAddrs
    ) throws Exception {
        FRC coordFrc = coordNode.getDeviceObject(FRC.class);
        if ( coordFrc == null ) {
            throw new Exception("FRC peripheral could not been found on coordinator");
        }

        FRC_Data frcDataCheck = coordFrc.send( new FRC_Prebonding( new short[] { 0x01, 0x00 }) );
        for ( int newAddr : newAddrs ) {
            if ( ( ( frcDataCheck.getData()[0 + newAddr / 8] >> ( newAddr % 8 ) ) & 0x01 ) == 0x00 )
            {
                logger.warn("Removing bond {}", newAddr);
                Integer bondedNodesNum = coordinator.removeBondedNode(newAddr);
                if ( bondedNodesNum == null ) {
                    logger.error("Error while removing bond {}", newAddr);
                }
            }
        }
    }
    
    // runs discovery
    private void runDiscovery(Coordinator coordinator) throws Exception {
        for ( int discoveryRetry = discoveryRetries; discoveryRetry != 0; discoveryRetry-- ) {
            UUID uid = coordinator.call(
                    Coordinator.MethodID.RUN_DISCOVERY, 
                    new Object[] { new DiscoveryParams(discoveryTxPower, 0) } 
            );
            
            if ( uid == null ) {
                throw new Exception("Request for running discovery failed.");
            }
            
            while ( true ) {
                CallRequestProcessingState procState = coordinator.getCallRequestProcessingState(uid);
                if ( procState == null ) {
                    throw new Exception("Error while getting state of processing during discovery");
                }
                
                switch ( procState ) {
                    case CANCELLED:
                        throw new Exception("Discovery was cancelled");
                    case ERROR:
                        CallRequestProcessingError procError = coordinator.getCallRequestProcessingError(uid);
                        if ( procError != null ) {
                            throw new Exception("Error during discovery: " + procError.getErrorType());
                        }
                        throw new Exception("Error during discovery");
                    case RESULT_ARRIVED:
                        break;
                    case WAITING_FOR_PROCESSING:
                    case WAITING_FOR_RESULT:
                        Thread.sleep(1000);
                }
                if ( procState == CallRequestProcessingState.RESULT_ARRIVED ) {
                    break;
                }
            }
            
            // how to determine the discovery timeout?
            DiscoveryResult discoResult = coordinator
                    .getCallResultInDefaultWaitingTimeout(uid, DiscoveryResult.class);
            
            if ( discoResult == null ) {
                logger.error("Discovery failed.");
                continue;
            }

            logger.info("Discovered {} nodes", discoResult.getDiscoveredNodesNum());
            updateNodesInfo(coordinator);
            if ( discoResult.getDiscoveredNodesNum() == bondedNodes.getNodesNumber() ) {
                break;
            }
        }
    }
    
    // creates and adds new nodes into result network
    private void addNewNodesWithAllPeripherals(List<Integer> newAddrs) throws Exception {
        String networkId = null;
        synchronized ( synchroResultNetwork ) {
            networkId = resultNetwork.id;
        }
        
        for ( int addr : newAddrs ) {
            com.microrisc.simply.Node newNode 
                = NodeFactory.createNodeWithAllPeripherals(networkId, Integer.toString(addr));
            
            synchronized ( synchroResultNetwork ) {
                resultNetwork.addNode(newNode);
            } 
        }
    }
    
    // performs the algorithm
    private void runAlgorithm() {
        logger.debug("runAlgorithm - start: ");
        
        setState(State.RUNNING);
        
        DateTime startTime = new DateTime();
        logger.info("Automatic network construction started at {}", startTime.toString("HH:mm:ss"));
        
        logger.info("Finding coordinator");
        
        com.microrisc.simply.Node coordNode = null;
        synchronized ( synchroResultNetwork ) {
            coordNode = resultNetwork.getNode(
                Integer.toString(DPA_ProtocolProperties.NADR_Properties.IQMESH_COORDINATOR_ADDRESS)
            );
        }
        
        if ( coordNode == null ) {
            setState(State.ERROR);
            logger.error("Coordinator node not found.");
            logger.debug("runAlgorithm - end");
            return; 
        }
        
        Coordinator coordinator = coordNode.getDeviceObject(Coordinator.class);
        if ( coordinator == null ) {
            setState(State.ERROR);
            logger.error("Coordinator interface not found.");
            logger.debug("runAlgorithm - end");
            return;
        }
        
        OS coordOs = coordNode.getDeviceObject(OS.class);
        if ( coordOs == null ) {
            setState(State.ERROR);
            logger.error("OS interface not found.");
            logger.debug("runAlgorithm - end");
            return;
        }
        
        coordinator.setRequestHwProfile(DPA_ProtocolProperties.HWPID_Properties.DO_NOT_CHECK);
        coordOs.setRequestHwProfile(DPA_ProtocolProperties.HWPID_Properties.DO_NOT_CHECK);
        
        logger.info("Initial network check");
        try {
            updateNodesInfo(coordinator);
        } catch ( Exception ex ) {
            setState(State.ERROR);
            logger.error("Update nodes info error: {}", ex );
            logger.debug("runAlgorithm - end");
            return;
        }
        
        int origNodesCount = bondedNodes.getNodesNumber();
        if ( !checkUnbondedNodes() ) {
            setState(State.ERROR);
            logger.debug("runAlgorithm - end");
            return;
        }
        
        logger.info("Number of hops set to the number of routers");
        RoutingHops prevRoutingHops = coordinator.setHops( new RoutingHops(0xFF, 0xFF) );
        if ( prevRoutingHops == null ) {
            setState(State.ERROR);
            logger.error("Error while setting hops");
            logger.debug("runAlgorithm - end");
        }
        
        logger.info("No LED indication and use of optimal time slot length");
        DPA_Parameter prevParam = coordinator.setDPA_Param( new DPA_Parameter(DPA_Parameter.DPA_ValueType.LAST_RSSI, false, false) );
        if ( prevParam == null ) {
            setState(State.ERROR);
            logger.error("Error while setting DPA parameter");
            logger.debug("runAlgorithm - end");
        }
        
        P2PPrebondingInfo p2pPrebondInfo = null;
        try {
            // information needed to send P2P packet to allow prebonding
            p2pPrebondInfo = getP2PPrebondingInfo();
        } catch ( Exception ex ) {
            setState(State.ERROR);
            logger.error("Error while getting info about P2P Sender peripheral: {}", ex );
            logger.debug("runAlgorithm - end");
        }
        
        logger.info("Automatic network construction in progress");
        
        int round = 1;
        for ( ; 
                bondedNodes.getNodesNumber() != DPA_ProtocolProperties.NADR_Properties.IQMESH_NODE_ADDRESS_MAX; 
                round++
        ) {
            if ( Thread.interrupted() ) {
                setState(State.CANCELLED);
                logger.info("Algorithm interrupted");
                logger.debug("runAlgorithm - end");
                return;
            }
            
            PeriodFormatter perFormatter = new PeriodFormatterBuilder()
                    .appendHours()
                    .appendMinutes()
                    .appendSeconds()
                    .toFormatter();
            logger.info(
                    "Round={}, Nodes={}, New nodes={}, Time={}", 
                    round, 
                    bondedNodes.getNodesNumber(), 
                    bondedNodes.getNodesNumber() - origNodesCount,
                    perFormatter.print( new Period( startTime, new DateTime() ))
            );
            
            try {
                // do prebonding
                prebond(coordOs, coordinator, p2pPrebondInfo);

                // get prebonded MIDs
                List<RemotelyBondedModuleId> prebondedMIDs = getPrebondedMIDs(coordinator, coordNode);

                // authorize bonded nodes
                List<Integer> newAddrs = authorizeBonds(coordinator, prebondedMIDs);

                // no new addresses authorized - continue with next iteration
                if ( newAddrs.isEmpty() ) {
                    continue;
                }
            
                if ( autoUseFrc ) {
                    logger.info("Running FRC to check new nodes");
                    checkNewNodes(coordinator, coordNode, newAddrs);
                }

                logger.info( "Running discovery...");
                runDiscovery(coordinator); 
                
                // adding new bonded nodes into network
                addNewNodesWithAllPeripherals(newAddrs);
            } catch ( InterruptedException e ) {
                setState(State.CANCELLED);
                logger.warn("Algorithm cancelled");
                logger.debug("runAlgorithm - end");
                return;
            } catch ( Exception e ) {
                setState(State.ERROR);
                logger.error("Error while running algorithm: {}", e);
                logger.debug("runAlgorithm - end");
                return;
            }
        }
        
        setState(State.FINISHED);
        logger.debug("runAlgorithm - end");
    }
    
    
    /**
     * Creates new object of the network building algorithm.
     * @param network network to start the algorithm with
     * @throws IllegalArgumentException if {@code network} is {@code null}
     */
    private NetworkBuildingAlgorithmImpl(Builder builder) {
        this.network = checkNetwork(builder.network);
        this.resultNetwork = createDynamicNetwork(network);
        
        this.broadcastServices = checkBroadcastServices(builder.broadcastServices);
        
        this.discoveryTxPower = checkDiscoveryTxPower(builder.discoveryTxPower);
        this.prebondingInterval = checkPrebondingInterval(builder.prebondingInterval);
        this.authorizeRetries = checkAuthorizeRetries(builder.authorizeRetries);
        this.discoveryRetries = checkDiscoveryRetries(builder.discoveryRetries);
        this.temporaryAddressTimeout = checkTemporaryAddressTimeout(builder.temporaryAddressTimeout);
        this.autoUseFrc = builder.autoUseFrc;
        
        this.algoThread = new AlgoThread();
    }
    
    // indication, if the algorithm has already been started
    private boolean started = false;
    
    // indication, if the algorithm has already been canceled
    private boolean canceled = false;
    
    
    @Override
    public void start() {
        logger.debug("start - start: ");
        
        if ( started ) {
            throw new IllegalStateException("Algorithm cannot be started more than once.");
        }
        
        algoThread.start();
        
        started = true;
        logger.info("Started");
        logger.debug("start - end");
    }
    
    
    public State getState() {
        synchronized ( synchroActualState ) {
            return actualState;
        }
    }
    
    public boolean isPrepared() {
        synchronized ( synchroActualState ) {
            return ( actualState == State.PREPARED );
        }
    }
    
    public boolean isRunning() {
        synchronized ( synchroActualState ) {
            return ( actualState == State.RUNNING );
        }
    }
    
    @Override
    public boolean isFinished() {
        synchronized ( synchroActualState ) {
            return ( actualState == State.FINISHED );
        }
    }
    
    public boolean isCancelled() {
        synchronized ( synchroActualState ) {
            return ( actualState == State.CANCELLED );
        }
    }
    
    public boolean isError() {
        synchronized ( synchroActualState ) {
            return ( actualState == State.ERROR );
        }
    }
    
    @Override
    public void cancel() {
        logger.debug("cancel - start:");
        
        if ( canceled ) {
            throw new IllegalStateException("Algorithm cannot be cancelled more than once.");
        }
        
        terminateAlgoThread();
        algoThread = null;
        
        canceled = true;
        logger.info("Cancelled.");
        logger.debug("cancel - end");
    }
    
    /**
     * Returns the network, which is the result of the algorithm's running. It
     * is perfectly possible to call this method even if the algorithm is still
     * running - the method returns the actual result. <br>
     * The returned value is the copy of the network instance the algorithm
     * is running on. 
     * @return result network
     */
    public Network getResultNetwork() {
        synchronized ( synchroResultNetwork ) {
            return getNetworkCopy(resultNetwork);
        }
    }
    
}
