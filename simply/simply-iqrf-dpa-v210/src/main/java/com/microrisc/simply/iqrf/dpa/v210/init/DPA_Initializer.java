/* 
 * Copyright 2014 MICRORISC s.r.o.
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

package com.microrisc.simply.iqrf.dpa.v210.init;

import com.microrisc.simply.BaseNetwork;
import com.microrisc.simply.ConnectorService;
import com.microrisc.simply.Network;
import com.microrisc.simply.Node;
import com.microrisc.simply.SimpleDeviceObjectFactory;
import com.microrisc.simply.SimplyException;
import com.microrisc.simply.connector.response_waiting.ResponseWaitingConnector;
import com.microrisc.simply.init.AbstractInitializer;
import com.microrisc.simply.init.InitConfigSettings;
import com.microrisc.simply.iqrf.dpa.v210.devices.Coordinator;
import com.microrisc.simply.iqrf.dpa.v210.devices.PeripheralInfoGetter;
import com.microrisc.simply.iqrf.dpa.v210.protocol.DPA_ProtocolProperties;
import com.microrisc.simply.iqrf.dpa.v210.types.BondedNodes;
import com.microrisc.simply.iqrf.dpa.v210.types.DiscoveryParams;
import com.microrisc.simply.iqrf.dpa.v210.types.DiscoveryResult;
import com.microrisc.simply.iqrf.dpa.v210.types.PeripheralEnumeration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsulates inicialization process of DPA based networks.
 * 
 * @author Michal Konopa
 */
public final class DPA_Initializer 
extends 
    AbstractInitializer<DPA_InitObjects<InitConfigSettings<Configuration, Map<String, Configuration>>>, Network> 
{
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(DPA_Initializer.class);
    
    
    /** Inner objects. */
    private DPA_InitObjects<InitConfigSettings<Configuration, Map<String, Configuration>>> initObjects = null;
    
    /** Device object factory. */
    private final SimpleDeviceObjectFactory devObjectFactory = new SimpleDeviceObjectFactory();
    
    /** Configuration settings for initializer. */
    private DPA_InitializerConfiguration dpaInitConfig = null;
    
    
    /**
     * Creates and returns peripheral information object for specified node.
     */
    private PeripheralInfoGetter createPerInfoObject(String networkId, String nodeId) 
            throws Exception {
        Class baseImplClass = initObjects.getImplClassMapper().getImplClass(
                PeripheralInfoGetter.class
        );
        
        return (PeripheralInfoGetter)devObjectFactory.getDeviceObject(
                networkId, nodeId, initObjects.getConnectionStack().getConnector(),
                baseImplClass, initObjects.getConfigSettings().getGeneralSettings()
        );
    }
    
    /**
     * Returns peripheral numbers provided by specified node.
     * @param infoDeviceObj
     * @return set of peripheral numbers the device supports
     */
    private Set<Integer> getPeripheralNumbers(PeripheralInfoGetter infoDeviceObj) 
            throws SimplyException {
        logger.debug("getPeripheralNumbers - start: infoDeviceObj={}", infoDeviceObj);
        
        GettingPeripheralsConfiguration gettingPerConfig 
                = dpaInitConfig.getEnumerationConfiguration().getGettingPeripheralsConfiguration();
        if ( gettingPerConfig == null ) {
            throw new SimplyException("Getting peripherals configuration not available");
        }
        
        int attemptId = 0;
        PeripheralEnumeration perEnum = null;
        while ( (attemptId < gettingPerConfig.getPerAttemptsNum()) && ( perEnum == null ) ) {
            logger.info("Getting peripheral enumeration: {} attempt", attemptId+1);
            
            UUID uid = infoDeviceObj.async_getPeripheralEnumeration();
            if ( uid != null ) {
                perEnum = infoDeviceObj.getCallResult(uid, PeripheralEnumeration.class,
                        gettingPerConfig.getPerTimeout()
                );
            }  
            
            if ( perEnum == null ) { 
                logger.info("State of peripheral enumeration request: " + 
                        infoDeviceObj.getCallRequestProcessingState(uid)
                );
            }
            attemptId++;
        }
        
        if ( perEnum == null ) {
            throw new SimplyException("No response from peripheral enumeration request.");
        }
        
        int[] defaultPerNumbers = perEnum.getDefaultPeripherals();
        int userPerTotal = perEnum.getUserDefPeripheralsNum();
        Set<Integer> allPerNumbers = new HashSet<>();
        for ( int defPerNumber : defaultPerNumbers ) {
            allPerNumbers.add(defPerNumber);
        }
        
        final int STANDARD_PER_NUM = 32;
        for ( int userPerNum = 0; userPerNum < userPerTotal; userPerNum++ ) {
            allPerNumbers.add(STANDARD_PER_NUM + userPerNum);
        }
        
        logger.debug("getPeripheralNumbers - end: {}", allPerNumbers);
        return allPerNumbers;
    }
    
    /**
     * Returns list of bonded nodes IDs.
     * @param coord coordinator to use
     * @return list of bonded nodes IDs
     */
    private List<Integer> getBondedNodesIds(Coordinator coord) throws Exception {
        logger.debug("getBondedNodesIds - start: coord={}", coord);
        
        BondedNodesConfiguration bondedNodesConfig = null;
        switch ( dpaInitConfig.getInitializationType() ) {
            case ENUMERATION:
                bondedNodesConfig = dpaInitConfig.getEnumerationConfiguration().getBondedNodesConfiguration();
                break;
            case FIXED:
                bondedNodesConfig = dpaInitConfig.getFixedInitConfiguration().getBondedNodesConfiguration();
                break;
        }
        
        if ( bondedNodesConfig == null ) {
            throw new SimplyException("No configuration found for bonded nodes.");
        }
        
        int attemptId = 0;
        BondedNodes result = null;
        while ( (attemptId < bondedNodesConfig.getBondedNodesAttemptsNum()) && (result == null) ) {
            logger.info("Get bonded nodes: {} attempt", attemptId+1);
            
            UUID uid = coord.call(Coordinator.MethodID.GET_BONDED_NODES, null);
            if ( uid != null ) {
                result = coord.getCallResult(uid, BondedNodes.class, 
                        bondedNodesConfig.getBondedNodesTimeout()
                );
            }
            attemptId++;
        }
        
        if ( result == null ) {
            throw new Exception("Request for getting bonded nodes failed");
        }
        
        List<Integer> bondedNodesIds = result.getList();
        logger.debug("getBondedNodesIds - end: {}", result.bondedNodesListToString());
        return bondedNodesIds;
    }
    
    /**
     * Creates node for specified nodeId and returns it.
     * @param networkId network ID
     * @param nodeId node ID
     * @return node for specified nodeId
     */
    Node createNode(String networkId, String nodeId) throws Exception {
        logger.debug("createNode - start: networkId={}, nodeId={}", networkId, nodeId);
        System.out.println("Creating node " + nodeId + ":");
        
        // creating Peripheral Information object to get all supported peripherals
        PeripheralInfoGetter perInfoObject = createPerInfoObject(networkId, nodeId);
        
        Set<Integer> peripheralNumbers = getPeripheralNumbers(perInfoObject);
        System.out.println("Peripherals: " + Arrays.toString(peripheralNumbers.toArray( new Integer[0])) );
        
        Node node = NodeFactory.createNode(networkId, nodeId, peripheralNumbers);
        
        System.out.println("Node created\n");
        logger.debug("createNode - end: {}", node);
        
        return node;
    }
    
    // Creates and returns map of nodes, which are bonded to specified coordinator.
    private Map<String, Node> createBondedNodes(String networkId, List<Integer> bondedNodesIds) 
            throws Exception {
        logger.debug("createBondedNodes - start: networkId={}, master={}", 
                networkId, Arrays.toString(bondedNodesIds.toArray( new Integer[0] ))
        );
        
        // for new line in the printed output
        System.out.println();        
        
        Map<String, Node> nodesMap = new HashMap<>();
        for ( Integer bondedNodeId : bondedNodesIds ) {
            if ( bondedNodeId > DPA_ProtocolProperties.NADR_Properties.IQMESH_NODE_ADDRESS_MAX ) {
                continue;
            }
            
            Node bondedNode = null;
            try {
                bondedNode = createNode(networkId, String.valueOf(bondedNodeId));
            } catch ( Exception e ) {
                throw new Exception("Fail to create bonded node " + bondedNodeId, e);
            }
            
            nodesMap.put(String.valueOf(bondedNodeId), bondedNode);
        }
        
        logger.debug("createBondedNodes - end: {}", nodesMap);
        return nodesMap;
    }
    
    /**
     * Runs process of discovery and returns its results.
     * @param coord coordinator to run discovery on
     * @return discovery result
     */
    private DiscoveryResult runDiscovery(Coordinator coord) throws SimplyException {
        // run discovery process
        System.out.println("Run discovery ...");
        
        DiscoveryConfiguration discConfig = dpaInitConfig.getDiscoveryConfiguration();
        
        // setting connector
        ConnectorService connector = initObjects.getConnectionStack().getConnector();
        ResponseWaitingConnector respWaitConn = (ResponseWaitingConnector)connector;
        
        long prevRespTimeout = respWaitConn.getResponseTimeout();
        respWaitConn.setResponseTimeout(discConfig.discoveryTimeout());
        
        coord.setDefaultWaitingTimeout(discConfig.discoveryTimeout() + 2000);
        DiscoveryResult discResult = coord.runDiscovery(
                new DiscoveryParams(discConfig.dicoveryTxPower(), 0)
        );
        
        respWaitConn.setResponseTimeout(prevRespTimeout);
        return discResult;
    }
    
    // creates network enumerated using enumeration of devices inside IQRF network
    private Network createEnumeratedNetwork(String networkId, Configuration networkSettings) 
            throws Exception 
    {
        logger.debug("createEnumeratedNetwork - start: networkId={}, networkSettings={}", 
                networkId, networkSettings
        );

        // creating master node
        Node masterNode = createNode(networkId, "0");
        logger.info("Master node created");
        
        // map of nodes of this network
        Map<String, Node> nodesMap = null;
        
        // checking, if coordinator is present at the master
        Coordinator masterCoord = masterNode.getDeviceObject(Coordinator.class);
        if ( masterCoord == null ) {
            logger.warn(
                    "Master node doesn't contain Coordinator interface."
                    + "No bonded nodes will be created"
            );
            nodesMap = new HashMap<>();
            nodesMap.put("0", masterNode);
            return new BaseNetwork(networkId, nodesMap);
        }
        
        EnumerationConfiguration enumConfig = dpaInitConfig.getEnumerationConfiguration();
        if ( enumConfig == null ) {
            throw new SimplyException("Configuration for enumeration not found.");
        }
        
        // getting currently bonded nodes
        List<Integer> bondedNodesIds = null;
        if ( enumConfig.getBondedNodesConfiguration() != null ) {
            bondedNodesIds = getBondedNodesIds(masterCoord);
            System.out.println("Number of bonded nodes: " + bondedNodesIds.size());
            System.out.println("Bonded nodes: " + Arrays.toString(bondedNodesIds.toArray(new Integer[0])));
        } else {
            bondedNodesIds = new LinkedList<>();
        }
        
        // running discovery process
        if ( dpaInitConfig.getDiscoveryConfiguration() != null ) {
            DiscoveryResult discoResult = runDiscovery(masterCoord);
            if ( discoResult == null ) {
                throw new SimplyException("Discovery failed");
            }
            System.out.println("Number of discovered nodes: " + discoResult.getDiscoveredNodesNum());
            
            if ( bondedNodesIds.size() != discoResult.getDiscoveredNodesNum() ) {
                logger.warn(
                        "Number of bonded nodes NOT equal to the number of discovered nodes:"
                        + " bonded nodes number = " + bondedNodesIds.size()
                        + " discovered nodes number = " + discoResult.getDiscoveredNodesNum()
                );
            } 
        }
        
        // creating nodes bonded to the Master node
        nodesMap = createBondedNodes(networkId, bondedNodesIds);
        nodesMap.put("0", masterNode);
        Network network = new BaseNetwork(networkId, nodesMap);
        
        logger.debug("createEnumeratedNetwork - end: {}", network);
        return network;
    }
    
    // creates nodes map from specified fixed mapping
    private Map<String, Node> createNodesFromNetworkFuncMapping(
            String networkId, Map<String, Set<Integer>> networkMapping, Set<Integer> bondedNodesIds
    ) throws SimplyException, Exception 
    {
        logger.debug("createNodesFromNetworkFuncMapping - start: networkId={}, networkMapping={}", 
                networkId, networkMapping
        );
        
        FixedInitConfiguration fixedInitConfig = dpaInitConfig.getFixedInitConfiguration();
        if ( fixedInitConfig == null ) {
            throw new SimplyException("Configuration for fixed initialization not found.");
        }
        
        Map<String, Node> nodesMap = new HashMap<>();
        for ( Map.Entry<String, Set<Integer>> nodeMappingEntry : networkMapping.entrySet() ) {
            int nodeId = Integer.parseInt(nodeMappingEntry.getKey());
            // coordinator was already created
            if ( nodeId == 0 ) {
                continue;
            }
            
            if ( !bondedNodesIds.contains(nodeId) ) {
                logger.warn("Node " + nodeId + " not bonded. Representation will not be created." );
                continue;
            }
            
            System.out.println("Creating node " + nodeId + ":");
            System.out.println("Peripherals: " + Arrays.toString(nodeMappingEntry.getValue().toArray( new Integer[0])) );
            
            Node node = NodeFactory.createNode(
                    networkId, nodeMappingEntry.getKey(), nodeMappingEntry.getValue()
            );
            nodesMap.put(nodeMappingEntry.getKey(), node);
            
            System.out.println("Node created");
        }
        
        logger.debug("createNodesFromNetworkFuncMapping - end: {}", nodesMap);
        return nodesMap;
    }
    
    // creates network defined by fixed entity
    private Network createFixedNetwork(String networkId, Configuration networkSettings) 
            throws Exception {
        logger.debug("createFixedNetwork - start: networkId={}, networkSettings={}", 
                networkId, networkSettings
        );
        
        FixedInitConfiguration fixedInitConfig = dpaInitConfig.getFixedInitConfiguration();
        if ( fixedInitConfig == null ) {
            throw new SimplyException("Fixed initialization configuration is missing."); 
        }
        
        Map<String, Set<Integer>> networkMapping 
                = fixedInitConfig.getNetworksFunctionalityToSimplyMapping().getMapping().get(networkId);
        if ( networkMapping == null ) {
            throw new SimplyException(
                "Mapping of functionality for network " + networkId + " not available."
            );
        }
        
        // creating master node
        Node masterNode = NodeFactory.createNode(networkId, "0", networkMapping.get("0"));
        logger.info("Master node created");
        
        // checking, if coordinator is present at the master
        Coordinator masterCoord = masterNode.getDeviceObject(Coordinator.class);
        if ( masterCoord == null ) {
            logger.warn(
                    "Master node doesn't contain Coordinator interface."
                    + "No bonded nodes will be created"
            );
            Map<String, Node> nodesMap = new HashMap<>();
            nodesMap.put("0", masterNode);
            return new BaseNetwork(networkId, nodesMap);
        }
        
        // getting currently bonded nodes
        List<Integer> bondedNodesIds = null;
        if ( fixedInitConfig.getBondedNodesConfiguration() != null ) {
            bondedNodesIds = getBondedNodesIds(masterCoord);
            System.out.println("Number of bonded nodes: " + bondedNodesIds.size());
            System.out.println("Bonded nodes: " + Arrays.toString(bondedNodesIds.toArray(new Integer[0])));
        } else {
            bondedNodesIds = new LinkedList<>();
        }
        
        // running discovery process
        if ( dpaInitConfig.getDiscoveryConfiguration() != null ) {
            DiscoveryResult discoResult = runDiscovery(masterCoord);
            if ( discoResult == null ) {
                throw new SimplyException("Discovery failed");
            }
            System.out.println("Number of discovered nodes: " + discoResult.getDiscoveredNodesNum());
            
            if ( bondedNodesIds.size() != discoResult.getDiscoveredNodesNum() ) {
                logger.warn(
                        "Number of bonded nodes NOT equal to the number of discovered nodes:"
                        + " bonded nodes number = " + bondedNodesIds.size()
                        + " ,discovered nodes number = " + discoResult.getDiscoveredNodesNum()
                );
            } 
        }
        
        // creating nodes bonded to the Master node
        Map<String, Node> nodesMap = createNodesFromNetworkFuncMapping(
                networkId, networkMapping, new HashSet<>(bondedNodesIds)
        );
        nodesMap.put("0", masterNode);
        Network network = new BaseNetwork(networkId, nodesMap);
        
        logger.debug("createFixedNetwork - end: {}", network);
        return network;
    }
    
    
    /**
     * Creates and returns new network - according to specified settings.
     * @param networkId ID of created network
     * @param networkSettings settings of created network
     * @return network
     */
    private Network createNetwork(String networkId, Configuration networkSettings) 
            throws Exception {
        logger.debug("createNetwork - start: networkId={}, networkSettings={}", 
                networkId, networkSettings
        );
        
        System.out.println("Creating network " + networkId + " ...");
        
        Network network = null;
        
        switch ( dpaInitConfig.getInitializationType() ) {
            case ENUMERATION:
                network = createEnumeratedNetwork(networkId, networkSettings);
                break;
            case FIXED:
                network = createFixedNetwork(networkId, networkSettings);
                break;
            default:
                throw new SimplyException(
                        "Unsupported initialization type: " + dpaInitConfig.getInitializationType()
                );
        }
        
        System.out.println("Network " + networkId + " successfully created.");
        
        logger.debug("createNetwork - end: {}", network);
        return network;
    }
    
    @Override
    public Map<String, Network> initialize(
            DPA_InitObjects<InitConfigSettings<Configuration, Map<String, Configuration>>> initObjects
    ) throws Exception {
        logger.debug("initialize - start: innerObjects={}", initObjects);
        System.out.println("Starting initialization of Simply ...");
        
        this.initObjects = initObjects;
        this.dpaInitConfig = DPA_InitializerConfigurationFactory.
                getDPA_InitializerConfiguration(initObjects.getConfigSettings().getGeneralSettings()
        );
        
        // starting the connector
        this.initObjects.getConnectionStack().start();
        
        // result map of networks
        Map<String, Network> networksMap = new HashMap<>();
        
        // initialize each network
        Map<String, Configuration> networksSettings = initObjects.getConfigSettings().getNetworksSettings(); 
        for ( Map.Entry<String, Configuration> networkEntry : networksSettings.entrySet() ) {
            Network network = createNetwork(networkEntry.getKey(), networkEntry.getValue());
            networksMap.put(networkEntry.getKey(), network);
        }
        System.out.println("Initialization of Simply complete.");
        
        logger.info("Initialization complete");
        logger.debug("initialize - end");
        return networksMap;
    }
}
