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

package com.microrisc.simply.iqrf.dpa.v201.protocol;

import com.microrisc.simply.iqrf.dpa.protocol.PeripheralToDevIfaceMapper;
import com.microrisc.simply.iqrf.dpa.protocol.PeripheralToDevIfaceMapperFactory;

/**
 * Factory for creation of mapping between standard DPA peripherals and Device Interfaces.
 * 
 * @author Michal Konopa
 */
public final class DPA_PeripheralToDevIfaceMapperFactory 
implements PeripheralToDevIfaceMapperFactory {
    
    /**
     * Returns mapping between standard DPA peripherals and Device Interfaces.
     * @return mapping between standard DPA peripherals and Device Interfaces
     * @throws Exception if an error has occured during the mapping creation
     */
    @Override
    public PeripheralToDevIfaceMapper createPeripheralToDevIfaceMapper() throws Exception {
        return new DPA_StandardPerToDevIfaceMapper();
    }
    
}