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

package com.microrisc.simply.iqrf.dpa.v210.devices.impl;

import com.microrisc.simply.ConnectorService;
import com.microrisc.simply.CallRequestProcessingInfoContainer;
import com.microrisc.simply.iqrf.dpa.v210.DPA_DeviceObject;
import com.microrisc.simply.iqrf.dpa.v210.devices.OS;
import com.microrisc.simply.iqrf.dpa.v210.di_services.method_id_transformers.OSStandardTransformer;
import com.microrisc.simply.iqrf.dpa.v210.types.DPA_Request;
import com.microrisc.simply.iqrf.dpa.v210.types.HWP_Configuration;
import com.microrisc.simply.iqrf.dpa.v210.types.OsInfo;
import com.microrisc.simply.iqrf.dpa.v210.types.SleepInfo;
import com.microrisc.simply.iqrf.types.VoidType;
import java.util.UUID;

/**
 * Simple {@code OS} implementation.
 * 
 * @author Michal Konopa
 */
public final class SimpleOS 
extends DPA_DeviceObject implements OS {
    
    public SimpleOS(String networkId, String nodeId, ConnectorService connector, 
            CallRequestProcessingInfoContainer resultsContainer
    ) {
        super(networkId, nodeId, connector, resultsContainer);
    }
    
    @Override
    public UUID call(Object methodId, Object[] args) {
        String methodIdStr = transform((OS.MethodID) methodId);
        if ( methodIdStr == null ) {
            return null;
        }
        
        if ( args == null ) {
            return dispatchCall( methodIdStr, new Object[] { getRequestHwProfile() } );
        }
        
        Object[] argsWithHwProfile = new Object[ args.length + 1 ];
        argsWithHwProfile[0] = getRequestHwProfile();
        System.arraycopy( args, 0, argsWithHwProfile, 1, args.length );
        return dispatchCall( methodIdStr, argsWithHwProfile);
    }
    
    @Override
    public String transform(Object methodId) {
        return OSStandardTransformer.getInstance().transform(methodId);
    }
    
    
    @Override
    public OsInfo read() {
        UUID uid = dispatchCall("1", new Object[] { getRequestHwProfile() }, getDefaultWaitingTimeout() );
        if ( uid == null ) {
            return null;
        }
        return getCallResult(uid, OsInfo.class, getDefaultWaitingTimeout() );
    }
    
    @Override
    public VoidType reset() {
        UUID uid = dispatchCall("2", new Object[] { getRequestHwProfile() }, getDefaultWaitingTimeout() );
        if ( uid == null ) {
            return null;
        }
        return getCallResult(uid, VoidType.class, getDefaultWaitingTimeout() );
    }
    
    @Override
    public HWP_Configuration readHWPConfiguration() {
        UUID uid = dispatchCall("3", new Object[] { getRequestHwProfile() }, getDefaultWaitingTimeout() );
        if ( uid == null ) {
            return null;
        }
        return getCallResult(uid, HWP_Configuration.class, getDefaultWaitingTimeout() );
    }
 
    @Override
    public VoidType runRFPGM() {
        UUID uid = dispatchCall("4", new Object[] { getRequestHwProfile() }, getDefaultWaitingTimeout() );
        if ( uid == null ) {
            return null;
        }
        return getCallResult(uid, VoidType.class, getDefaultWaitingTimeout() );
    }

    @Override
    public VoidType sleep(SleepInfo sleepInfo) {
        UUID uid = dispatchCall("5", new Object[] { getRequestHwProfile(), sleepInfo }, 
                getDefaultWaitingTimeout() 
        );
        if ( uid == null ) {
            return null;
        }
        return getCallResult(uid, VoidType.class, getDefaultWaitingTimeout());
    }
    
    @Override
    public VoidType batch(DPA_Request[] requests) {
        UUID uid = dispatchCall(
                "6", new Object[] { getRequestHwProfile(), requests }, getDefaultWaitingTimeout() 
        );
        if ( uid == null ) {
            return null;
        }
        return getCallResult(uid, VoidType.class, getDefaultWaitingTimeout());
    }
    
    private static final int USER_ADDR_LOWER_BOUND = 0x00;
    private static final int USER_ADDR_UPPER_BOUND = 0xFFFF;
    
    private static int checkUserAddress(int userAddress) {
        if ( (userAddress < USER_ADDR_LOWER_BOUND) || (userAddress > USER_ADDR_UPPER_BOUND) ) {
            throw new IllegalArgumentException("User address out of bounds");
        }
        return userAddress;
    }
    
    @Override
    public VoidType setUSEC(int value) {
        checkUserAddress(value);
        UUID uid = dispatchCall("7", new Object[] { getRequestHwProfile(), value }, 
                getDefaultWaitingTimeout() 
        );
        if ( uid == null ) {
            return null;
        }
        return getCallResult(uid, VoidType.class, getDefaultWaitingTimeout());
    }
    
    // MID key length
    private static final int MID_KEY_LENGTH = 24; 
    
    private static void checkKey(short[] key) {
        if ( key == null ) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        if ( key.length != MID_KEY_LENGTH ) {
            throw new IllegalArgumentException(
                    "Invalid key length. Expected: " + MID_KEY_LENGTH
            );
        }
    }
    
    @Override
    public VoidType setMID(short[] key) {
        checkKey(key);
        UUID uid = dispatchCall("8", new Object[] { getRequestHwProfile(), key }, 
                getDefaultWaitingTimeout() 
        );
        if ( uid == null ) {
            return null;
        }
        return getCallResult(uid, VoidType.class, getDefaultWaitingTimeout());
    }
}
