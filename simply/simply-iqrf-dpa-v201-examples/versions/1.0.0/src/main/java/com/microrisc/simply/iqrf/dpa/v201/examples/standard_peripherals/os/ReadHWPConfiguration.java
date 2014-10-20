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

package com.microrisc.simply.iqrf.dpa.v201.examples.standard_peripherals.os;

import com.microrisc.simply.CallRequestProcessingState;
import static com.microrisc.simply.CallRequestProcessingState.ERROR;
import com.microrisc.simply.Simply;
import com.microrisc.simply.SimplyException;
import com.microrisc.simply.Network;
import com.microrisc.simply.Node;
import com.microrisc.simply.errors.CallRequestProcessingError;
import com.microrisc.simply.iqrf.dpa.v201.DPA_SimplyFactory;
import com.microrisc.simply.iqrf.dpa.v201.devices.OS;
import com.microrisc.simply.iqrf.dpa.v201.types.HWP_Configuration;
import java.io.File;

/**
 * Example of using OS Peripheral - synchronous version.
 * 
 * @author Michal Konopa
 * @author Rostislav Spinar
 */
public class ReadHWPConfiguration {
    // reference to Simply
    private static Simply simply = null;
    
    // prints out specified message, destroys the Simply and exits
    private static void printMessageAndExit(String message) {
        System.out.println(message);
        if ( simply != null) {
            simply.destroy();
        }
        System.exit(1);
    }
    

    public static void main(String[] args) throws InterruptedException {
        // creating Simply instance
        try {
            simply = DPA_SimplyFactory.getSimply("config" + File.separator + "Simply-standard_per.properties");
        } catch ( SimplyException ex ) {
            printMessageAndExit("Error while creating Simply: " + ex.getMessage());
        }
        
        // getting network 1
        Network network1 = simply.getNetwork("1", Network.class);
        if (network1 == null) {
            printMessageAndExit("Network 1 doesn't not exist");
        }
        
        // getting node 1
        Node node1 = network1.getNode("1");
        if (node1 == null) {
            printMessageAndExit("Node 1 doesn't exist");
        }
        
        // get access to OS peripheral
        OS os = node1.getDeviceObject(OS.class);
        if (os == null) {
            printMessageAndExit("OS doesn't exist or is not enabled");
        }
               
        // read HWP config setting
        HWP_Configuration hwpConfig = os.readHWPConfiguration();
        if (hwpConfig == null) {
            CallRequestProcessingState procState = os.getCallRequestProcessingStateOfLastCall();
            if ( procState == ERROR ) {
                CallRequestProcessingError error = os.getCallRequestProcessingErrorOfLastCall();
                printMessageAndExit("HWP config reading failed: " + error);
            } else {
                printMessageAndExit("HWP config reading hasn't been processed yet: " + procState);
            }
        }
        
        System.out.println("HWP config: \n" + hwpConfig.toPrettyFormatedString());
        
        // end working with Simply
        simply.destroy();
    }
}