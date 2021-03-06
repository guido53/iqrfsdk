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
import com.microrisc.simply.iqrf.dpa.v201.di_services.DPA_StandardServices;
import com.microrisc.simply.iqrf.types.VoidType;
import java.util.UUID;

/**
 * DPA EEEPROM Device Interface.
 * 
 * @author Michal Konopa
 */
@DeviceInterface
public interface EEEPROM 
extends DPA_StandardServices, GenericAsyncCallable, MethodIdTransformer {
    /**
     * Identifiers of this device interface's methods.
     */
    enum MethodID implements DeviceInterfaceMethodId {
        READ,
        WRITE
    }
    
    /**
     * Sends method call request for reading from peripheral.
     * @param blockNumber number of (zero based) block to read from
     * @param length length of the data to read (in bytes), must be equal to the block size
     * @return unique identifier of sent request
     */
    UUID async_read(int blockNumber, int length);
    
    /**
     * Reads in data of specified length from specified block.
     * Synchronous wrapper for {@link #async_read(int, int)  async_read} method.
     * @param blockNumber number of (zero based) block to read from
     * @param length length of the data to read (in bytes), must be equal to the block size
     * @return read data <br>
     *         {@code null}, if an error has occurred during processing
     */
    short[] read(int blockNumber, int length);
    
    
    /**
     * Sends method call request for writing to peripheral.
     * @param blockNumber number of (zero based) block to write the data into
     * @param data actual data to be written to the memory, its length must be 
     *             equal to the block size
     * @return unique identifier of sent request
     */
    UUID async_write(int blockNumber, short[] data);
    
    /**
     * Writes specified data to specified address.
     * Synchronous wrapper for {@link #async_write(int, short[])  async_write} method.
     * @param blockNumber number of (zero based) block to write the data into
     * @param data actual data to be written to the memory, its length must be 
     *             equal to the block size
     * @return {@code VoidType} object, if method call has processed allright <br>
     *         {@code null}, if an error has occurred during processing
     */
    VoidType write(int blockNumber, short[] data);
}
