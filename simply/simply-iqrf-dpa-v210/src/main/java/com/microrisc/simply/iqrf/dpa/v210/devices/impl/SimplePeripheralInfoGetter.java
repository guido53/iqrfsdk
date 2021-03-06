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
import com.microrisc.simply.iqrf.dpa.v210.devices.PeripheralInfoGetter;
import com.microrisc.simply.iqrf.dpa.v210.di_services.method_id_transformers.PeripheralInfoGetterStandardTransformer;
import com.microrisc.simply.iqrf.dpa.v210.types.PeripheralEnumeration;
import com.microrisc.simply.iqrf.dpa.v210.types.PeripheralInfo;
import java.util.UUID;

/**
 * Simple {@code PeripheralInfoGetter} implementation. 
 * 
 * @author Michal Konopa
 */
public final class SimplePeripheralInfoGetter 
extends DPA_DeviceObject implements PeripheralInfoGetter {
    
    public SimplePeripheralInfoGetter(String networkId, String nodeId, ConnectorService connector, 
            CallRequestProcessingInfoContainer resultsContainer
    ) {
        super(networkId, nodeId, connector, resultsContainer);
    }
    
    @Override
    public UUID call(Object methodId, Object[] args) {
        String methodIdStr = transform((PeripheralInfoGetter.MethodID) methodId);
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
        return PeripheralInfoGetterStandardTransformer.getInstance().transform(methodId);
    }
    
    @Override
    public UUID async_getPeripheralEnumeration() {
        return dispatchCall("1", new Object[] { getRequestHwProfile() }, getDefaultWaitingTimeout() );
    }
    
    @Override
    public PeripheralEnumeration getPeripheralEnumeration() {
        UUID uid = dispatchCall("1", new Object[] { getRequestHwProfile() }, getDefaultWaitingTimeout() );
        if ( uid == null ) {
            return null;
        }
        return getCallResult(uid, PeripheralEnumeration.class, getDefaultWaitingTimeout() );
    }
    
    private static int checkPeripheralId(int peripheralId) {
        if ( !DataTypesChecker.isByteValue(peripheralId) ) {
            throw new IllegalArgumentException("Peripheral ID out of bounds");
        }
        return peripheralId;
    }
    
    @Override
    public UUID async_getPeripheralInfo(int peripheralId) {
        checkPeripheralId(peripheralId);
        return dispatchCall(
                "2", new Object[] { getRequestHwProfile(), peripheralId } 
        );
    }
    
    @Override
    public PeripheralInfo getPeripheralInfo(int peripheralId) {
        checkPeripheralId(peripheralId);
        UUID uid = dispatchCall(
                "2", new Object[] { getRequestHwProfile(), peripheralId }, 
                getDefaultWaitingTimeout() 
        );
        if ( uid == null ) {
            return null;
        }
        return getCallResult(uid, PeripheralInfo.class, getDefaultWaitingTimeout() );
    }
    
    @Override
    public UUID async_getMorePeripheralsInfo(int startPeripheralId) {
        checkPeripheralId(startPeripheralId);
        return dispatchCall(
                "3", new Object[] { getRequestHwProfile(), startPeripheralId }
        );
    }
    
    @Override
    public PeripheralInfo[] getMorePeripheralsInfo(int startPeripheralId) {
        checkPeripheralId(startPeripheralId);
        UUID uid = dispatchCall(
                "3", new Object[] { getRequestHwProfile(), startPeripheralId }, 
                getDefaultWaitingTimeout()
        );
        if ( uid == null ) {
            return null;
        }
        return getCallResult(uid, PeripheralInfo[].class, getDefaultWaitingTimeout() );
    }
}
