package com.microrisc.simply.iqrf.dpa.v210.examples.user_peripherals.mydallas.def;

import com.microrisc.simply.CallRequestProcessingInfoContainer;
import com.microrisc.simply.ConnectorService;
import com.microrisc.simply.iqrf.dpa.v210.DPA_DeviceObject;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Simple {@code MyDallas18B20} implementation.
 *
 * @author Martin Strouhal
 */
public final class SimpleMyDallas18B20 extends DPA_DeviceObject implements MyDallas18B20 {

    /**
     * Mapping of method IDs to theirs string representations.
     */
    private static final Map<MyDallas18B20.MethodID, String> methodIdsMap = new EnumMap<>(
            MyDallas18B20.MethodID.class);

    private static void initMethodIdsMap() {
        methodIdsMap.put(MyDallas18B20.MethodID.GET, "0");
    }

    static {
        initMethodIdsMap();
    }

    public SimpleMyDallas18B20(String networkId, String nodeId,
            ConnectorService connector,
            CallRequestProcessingInfoContainer resultsContainer) {
        super(networkId, nodeId, connector, resultsContainer);
    }

    @Override
    public UUID call(Object methodId, Object[] args) {
        String methodIdStr = transform((MyDallas18B20.MethodID) methodId);
        if (methodIdStr == null) {
            return null;
        }

        if (args == null) {
            return dispatchCall(methodIdStr, new Object[]{getRequestHwProfile()});
        }

        Object[] argsWithHwProfile = new Object[args.length + 1];
        argsWithHwProfile[0] = getRequestHwProfile();
        System.arraycopy(args, 0, argsWithHwProfile, 1, args.length);
        return dispatchCall(methodIdStr, argsWithHwProfile);
    }

    @Override
    public String transform(Object methodId) {
        if (!(methodId instanceof MyDallas18B20.MethodID)) {
            throw new IllegalArgumentException(
                    "Method ID must be of type Dallas18B20.MethodID."
            );
        }
        return methodIdsMap.get((MyDallas18B20.MethodID) methodId);
    }

    @Override
    public short get() {
        UUID uid = dispatchCall("0", new Object[]{getRequestHwProfile()},
                getDefaultWaitingTimeout());
        if (uid == null) {
            return Short.MAX_VALUE;
        }
        return getCallResult(uid, short.class, getDefaultWaitingTimeout());
    }
}