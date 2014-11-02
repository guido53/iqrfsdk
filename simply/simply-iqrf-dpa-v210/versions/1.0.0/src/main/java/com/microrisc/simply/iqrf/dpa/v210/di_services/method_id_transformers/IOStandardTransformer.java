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

package com.microrisc.simply.iqrf.dpa.v210.di_services.method_id_transformers;

import com.microrisc.simply.di_services.MethodIdTransformer;
import com.microrisc.simply.iqrf.dpa.v210.devices.IO;
import java.util.EnumMap;
import java.util.Map;

/**
 * Standard method ID transformer for IO. 
 * 
 * @author Michal Konopa
 */
public final class IOStandardTransformer implements MethodIdTransformer {
    /**
     * Mapping of method IDs to theirs string representations.
     */
    private static final Map<IO.MethodID, String> methodIdsMap = 
            new EnumMap<IO.MethodID, String>(IO.MethodID.class);
    
    private static void initMethodIdsMap() {
        methodIdsMap.put(IO.MethodID.SET_DIRECTION, "1");
        methodIdsMap.put(IO.MethodID.SET_OUTPUT_STATE, "2");
        methodIdsMap.put(IO.MethodID.GET, "3");
    }
    
    static  {
        initMethodIdsMap();
    }
    
    /** Singleton. */
    private static final IOStandardTransformer instance = new IOStandardTransformer();
    
    
    /**
     * @return IOStandardTransformer instance 
     */
    static public IOStandardTransformer getInstance() {
        return instance;
    }
    
    @Override
    public String transform(Object methodId) {
        if ( !(methodId instanceof IO.MethodID) ) {
            throw new IllegalArgumentException(
                    "Method ID must be of type IO.MethodID."
            );
        }
        return methodIdsMap.get((IO.MethodID) methodId);
    }
    
}
