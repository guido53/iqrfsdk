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
import com.microrisc.simply.iqrf.dpa.v201.types.BaudRate;
import com.microrisc.simply.iqrf.types.VoidType;

/**
 * DPA UART Device Interface.
 * All methods return {@code null} if an error has occurred during processing
 * of corresponding method call.
 * 
 * @author Michal Konopa
 */
@DeviceInterface
public interface UART 
extends DPA_StandardServices, GenericAsyncCallable, MethodIdTransformer {
    /**
     * Identifiers of this device interface's methods.
     */
    enum MethodID implements DeviceInterfaceMethodId {
        OPEN,
        CLOSE,
        WRITE_AND_READ
    }
    
    /**
     * Opens UART at specified baudrate and flushes internal read and write buffers.
     * The size of the read and write buffers is 32 bytes.
     * @param baudRate baudrate to use
     * @return {@code VoidType} object, if method call has processed allright
     */
    VoidType open(BaudRate baudRate);
    
    /**
     * Closes UART interface.
     * @return {@code VoidType} object, if method call has processed allright
     */
    VoidType close();
    
    /**
     * Reads and/or writes data to/from UART interface.
     * If UART is not open, the request fails with ERROR_FAIL.
     * @param readTimeout specifies timeout in 10 ms unit to wait for data to 
     *        be read from UART after data is (optionally) written. <br>
     *        {@code 0xff} specifies that no data should be read.
     * @param data optional data to be written to the UART
     * @return optional data read from UART if the reading was requested and 
     *         data is available.
     */
    short[] writeAndRead(int readTimeout, short[] data);
}
