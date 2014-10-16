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

package com.microrisc.simply.iqrf.dpa.examples.standard_peripherals.frc;

import com.microrisc.simply.CallRequestProcessingState;
import com.microrisc.simply.Network;
import com.microrisc.simply.Node;
import com.microrisc.simply.Simply;
import com.microrisc.simply.SimplyException;
import com.microrisc.simply.errors.CallRequestProcessingError;
import com.microrisc.simply.iqrf.dpa.DPA_SimplyFactory;
import com.microrisc.simply.iqrf.dpa.devices.FRC;
import com.microrisc.simply.iqrf.dpa.types.FRC_Command;
import com.microrisc.simply.iqrf.dpa.types.FRC_Data;
import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;


/**
 * Example of using FRC peripheral - send command and getting extra result using
 * asynchronous method calling.
 * 
 * @author Michal Konopa
 * @author Rostislav Spinar
 */
public class SendAndExtraResultAsync {
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
    
    // processes NULL result
    private static void processNullResult(FRC frc, String errorMsg, String notProcMsg) 
    {
        CallRequestProcessingState procState = frc.getCallRequestProcessingStateOfLastCall();
        if ( procState == CallRequestProcessingState.ERROR ) {
            CallRequestProcessingError error = frc.getCallRequestProcessingErrorOfLastCall();
            printMessageAndExit(errorMsg + ": " + error);
        } else {
            printMessageAndExit(notProcMsg + ": " + procState);
        }
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
        if ( network1 == null ) {
            printMessageAndExit("Network 1 doesn't exist");
        }
        
        // getting a master node
        Node master = network1.getNode("0");
        if ( master == null ) {
            printMessageAndExit("Master doesn't exist");
        }
        
        // getting FRC interface
        FRC frc = master.getDeviceObject(FRC.class);
        if ( frc == null ) {
            printMessageAndExit("FRC doesn't exist or is not enabled");
        }
        
        // Setting DO waiting timeout to 11s
        frc.setDefaultWaitingTimeout(11000);
       
        // User's defined FRC command
        // byte FRC_User = x;
        
        // First up to 57 bytes of FRC result
        UUID getFRCRequestUid = frc.async_send( new FRC_Command(FRC_Command.PredefinedCommand.TEMPERATURE), 
                new short[] { 0, 0, 0, 0, 0 }
        );
        
        // ... do some other work
        System.out.println("Sleeping ... waiting for FRC result");
        Thread.sleep(3000);
        
        // getting result of method call
        FRC_Data frcData = frc.getCallResultInDefaultWaitingTimeout(getFRCRequestUid, FRC_Data.class);
        if ( frcData == null ) {
            processNullResult(frc, "Sending FRC command failed", 
                    "Sending FRC command hasn't been processed yet"
            );
        }
        
        // remaining bytes of FRC result
        Short[] frcExtraData = frc.extraResult();
        if ( frcExtraData == null ) {
            processNullResult(frc, "Setting FRC extra result failed", 
                    "Setting FRC extra result hasn't been processed yet"
            );
        }
        
        // printing incomming FRC data
        System.out.println("FRC data: \n" + frcData.toPrettyFormatedString() );
        System.out.println("FRC extra bytes: " + Arrays.toString(frcExtraData));
        
        // end working with Simply
        simply.destroy();
    }
}
