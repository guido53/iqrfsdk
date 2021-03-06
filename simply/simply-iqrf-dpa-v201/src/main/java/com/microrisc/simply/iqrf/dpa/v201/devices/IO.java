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

package com.microrisc.simply.iqrf.dpa.v201.devices;

import com.microrisc.simply.DeviceInterface;
import com.microrisc.simply.DeviceInterfaceMethodId;
import com.microrisc.simply.di_services.GenericAsyncCallable;
import com.microrisc.simply.di_services.MethodIdTransformer;
import com.microrisc.simply.iqrf.dpa.di_services.DPA_StandardServices;
import com.microrisc.simply.iqrf.dpa.v201.types.IO_Command;
import com.microrisc.simply.iqrf.dpa.v201.types.IO_DirectionSettings;
import com.microrisc.simply.iqrf.types.VoidType;
import java.util.UUID;

/**
 * DPA Device Interface for general IO operations.
 * 
 * @author Michal Konopa
 */
@DeviceInterface
public interface IO 
extends DPA_StandardServices, GenericAsyncCallable, MethodIdTransformer {
    /**
     * Identifiers of this device interface's methods.
     */
    enum MethodID implements DeviceInterfaceMethodId {
        SET_DIRECTION,
        SET_OUTPUT_STATE,
        GET
    }
    
    /**
     * Sends method call request for setting the direction of the individual 
     * IO pins of the individual ports. Additionally, the same request can be
     * used to setup weak pull-ups at the pins where available. 
     * See datasheet of the PIC MCU for a description of IO ports.
     * @param directionSettings direction settings
     * @return unique identifier of sent request
     */
    UUID async_setDirection(IO_DirectionSettings[] directionSettings);
    
    /**
     * Sets the direction of the individual IO pins of the individual ports.
     * Synchronous wrapper for {@link #async_setDirection(com.microrisc.simply.iqrf.dpa.v201.types.IO_DirectionSettings[])  async_setDirection} method.
     * @param directionSettings direction settings
     * @return {@code VoidType} object, if operation has processed correctly <br>
     *         {@code null}, if an error has occurred during processing
     */
    VoidType setDirection(IO_DirectionSettings[] directionSettings);
    
    
    /**
     * Sends method call request for setting the output state of the IO pins. 
     * It also allows inserting an active waiting delay between IO pins settings. 
     * This feature can be used to generate an arbitrary time defined signals 
     * on the IO pins of the MCU. During the active waiting the device is blocked 
     * and any network traffic will not be processed. 
     * <p>
     * This command is executed after the DPA response is sent back to the device 
     * that sent the original DPA IO Set request. Therefore if an invalid port is 
     * specified an error code is not returned inside DPA response but the rest of 
     * the request execution is skipped.
     * @param ioCommands IO commands to execute
     * @return unique identifier of sent request
     */
    UUID async_setOutputState(IO_Command[] ioCommands);
    
    /**
     * Sets the output state of the IO pins.
     * Synchronous wrapper for {@link #async_setOutputState(com.microrisc.simply.iqrf.dpa.v201.types.IO_Command[]) 
     * async_setOutputState} method.
     * @param ioCommands IO commands to execute
     * @return {@code VoidType} object, if operation has processed correctly <br>
     *         {@code null}, if an error has occurred during processing
     */
    VoidType setOutputState(IO_Command[] ioCommands);
    
    
    /**
     * Sends method call request for getting the input state of all supported 
     * the MCU ports (PORTx).
     * @return unique identifier of sent request
     */
    UUID async_get();
    
    /**
     * Reads the input state of all supported the MCU ports.
     * Synchronous wrapper for {@link #async_get() async_get} method.
     * @return array of bytes representing state of port PORTA, PORTB, …, 
     *         ending with the last supported MCU port. <br>
     *         {@code null}, if an error has occurred during processing
     */
    short[] get();
}
