
package com.microrisc.simply.iqrf.dpa.v210.devices.impl;

import com.microrisc.simply.ConnectorService;
import com.microrisc.simply.CallRequestProcessingInfoContainer;
import com.microrisc.simply.iqrf.dpa.v210.DPA_DeviceObject;
import com.microrisc.simply.iqrf.dpa.v210.devices.PWM;
import com.microrisc.simply.iqrf.dpa.v210.di_services.method_id_transformers.PWMStandardTransformer;
import com.microrisc.simply.iqrf.dpa.v210.types.PWM_Parameters;
import com.microrisc.simply.iqrf.types.VoidType;
import java.util.UUID;

/**
 * Simple PWM implementation.
 * 
 * @author Michal Konopa
 */
public final class SimplePWM 
extends DPA_DeviceObject implements PWM {
    
    public SimplePWM(String networkId, String nodeId, ConnectorService connector, 
            CallRequestProcessingInfoContainer resultsContainer
    ) {
        super(networkId, nodeId, connector, resultsContainer);
    }
    
    @Override
    public UUID call(Object methodId, Object[] args) {
        String methodIdStr = transform((PWM.MethodID) methodId);
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
        return PWMStandardTransformer.getInstance().transform(methodId);
    }
    
    @Override
    public VoidType set(PWM_Parameters param) {
        UUID uid = dispatchCall("1", new Object[] { getRequestHwProfile(), param }, getDefaultWaitingTimeout() );
        if ( uid == null ) {
            return null;
        }
        return getCallResult(uid, VoidType.class, getDefaultWaitingTimeout() );
    }
}