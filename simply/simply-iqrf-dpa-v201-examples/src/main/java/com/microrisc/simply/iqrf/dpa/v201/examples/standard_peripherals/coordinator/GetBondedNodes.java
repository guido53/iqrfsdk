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

package com.microrisc.simply.iqrf.dpa.v201.examples.standard_peripherals.coordinator;

import com.microrisc.simply.CallRequestProcessingState;
import com.microrisc.simply.Network;
import com.microrisc.simply.Node;
import com.microrisc.simply.Simply;
import com.microrisc.simply.SimplyException;
import com.microrisc.simply.errors.CallRequestProcessingError;
import com.microrisc.simply.iqrf.dpa.v201.DPA_SimplyFactory;
import com.microrisc.simply.iqrf.dpa.v201.devices.Coordinator;
import com.microrisc.simply.iqrf.dpa.v201.types.BondedNodes;
import java.io.File;


/**
 * Examples of using Coordinator peripheral - synchronous version.
 * 
 * @author Michal Konopa
 * @author Rostislav Spinar
 */
public class GetBondedNodes {
    // reference to Simply
    private static Simply simply = null;
    
    // prints out specified error description, destroys the Simply and exits
    private static void printMessageAndExit(String message) {
        System.out.println(message);
        if ( simply != null) {
            simply.destroy();
        }
        System.exit(1);
    }
    
    public static void main(String[] args) {
        
        // creating Simply instance
        try {
            simply = DPA_SimplyFactory.getSimply("config" + File.separator + "Simply-standard_per.properties");
        } catch ( SimplyException ex ) {
            printMessageAndExit("Error while creating Simply: " + ex.getMessage());
        }
        
        // getting network 1
        Network network1 = simply.getNetwork("1", Network.class);
        if ( network1 == null ) {
            printMessageAndExit("Network 1 doesn't exist");
        }
        
        // getting a master node
        Node master = network1.getNode("0");
        if ( master == null ) {
            printMessageAndExit("Master doesn't exist");
        }
        
        // getting Coordinator interface
        Coordinator coordinator = master.getDeviceObject(Coordinator.class);
        if ( coordinator == null ) {
            printMessageAndExit("Coordinator doesn't exist");
        }
       
        // get all bonded nodes
        BondedNodes bondedNodes = coordinator.getBondedNodes();
        if ( bondedNodes == null ) {
            CallRequestProcessingState procState = coordinator.getCallRequestProcessingStateOfLastCall();
            if ( procState == CallRequestProcessingState.ERROR ) {
                CallRequestProcessingError error = coordinator.getCallRequestProcessingErrorOfLastCall();
                printMessageAndExit("Getting of bonded nodes failed: " + error);
            } else {
                printMessageAndExit("Getting of bonded nodes hasn't been processed yet: " + procState);
            }
        }
        
        // bonded nodes
        System.out.println("Bonded nodes: " + bondedNodes.bondedNodesListToString());
        
        // end working with Simply
        simply.destroy();
    }
}
