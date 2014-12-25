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

package com.microrisc.simply.iqrf.dpa.v201.init;

import com.microrisc.simply.init.InitObjects;
import com.microrisc.simply.iqrf.dpa.protocol.PeripheralToDevIfaceMapper;

/**
 * Provides access to objects, which are needed in the process of initialization 
 * of Simply DPA.
 * 
 * @author Michal Konopa
 * @param <T> type of configuration settings
 */
public interface DPA_InitObjects<T extends Object> extends InitObjects<T> {
    /**
     * Returns DPA Peripherals to Device Interfaces mapper
     * @return DPA Peripherals to Device Interfaces Mapper
     */
    PeripheralToDevIfaceMapper getPeripheralToDevIfaceMapper();
}
