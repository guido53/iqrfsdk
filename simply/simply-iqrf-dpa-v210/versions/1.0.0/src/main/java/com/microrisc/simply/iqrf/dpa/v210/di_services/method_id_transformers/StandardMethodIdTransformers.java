

package com.microrisc.simply.iqrf.dpa.v210.di_services.method_id_transformers;

import com.microrisc.simply.di_services.MethodIdTransformer;
import com.microrisc.simply.iqrf.dpa.v210.devices.Coordinator;
import com.microrisc.simply.iqrf.dpa.v210.devices.EEEPROM;
import com.microrisc.simply.iqrf.dpa.v210.devices.EEPROM;
import com.microrisc.simply.iqrf.dpa.v210.devices.FRC;
import com.microrisc.simply.iqrf.dpa.v210.devices.GeneralLED;
import com.microrisc.simply.iqrf.dpa.v210.devices.GeneralMemory;
import com.microrisc.simply.iqrf.dpa.v210.devices.IO;
import com.microrisc.simply.iqrf.dpa.v210.devices.LEDG;
import com.microrisc.simply.iqrf.dpa.v210.devices.LEDR;
import com.microrisc.simply.iqrf.dpa.v210.devices.Node;
import com.microrisc.simply.iqrf.dpa.v210.devices.OS;
import com.microrisc.simply.iqrf.dpa.v210.devices.PWM;
import com.microrisc.simply.iqrf.dpa.v210.devices.PeripheralInfoGetter;
import com.microrisc.simply.iqrf.dpa.v210.devices.RAM;
import com.microrisc.simply.iqrf.dpa.v210.devices.SPI;
import com.microrisc.simply.iqrf.dpa.v210.devices.Thermometer;
import com.microrisc.simply.iqrf.dpa.v210.devices.UART;
import java.util.HashMap;
import java.util.Map;

/**
 * Access to Standard method ID transformers. 
 * 
 * @author Michal Konopa
 */
public final class StandardMethodIdTransformers {
    private final Map<Class, MethodIdTransformer> transformers;
    
    /**
     * Private constructor.
     */
    private StandardMethodIdTransformers() {
        transformers = new HashMap<>();
        addTransformers();
    }
    
    /** Singleton. */
    private static final StandardMethodIdTransformers instance = 
            new StandardMethodIdTransformers();
    
    
    /**
     * @return StandardMethodIdTransformers instance
     */
    static public StandardMethodIdTransformers getInstance() {
        return instance;
    }
    
    private void addTransformers() {
        transformers.put(Coordinator.class, CoordinatorStandardTransformer.getInstance());
        transformers.put(EEEPROM.class, EEEPROMStandardTransformer.getInstance());
        transformers.put(FRC.class, FRCStandardTransformer.getInstance());
        transformers.put(GeneralLED.class, GeneralLEDStandardTransformer.getInstance());
        transformers.put(LEDR.class, GeneralLEDStandardTransformer.getInstance());
        transformers.put(LEDG.class, GeneralLEDStandardTransformer.getInstance());
        transformers.put(GeneralMemory.class, GeneralMemoryStandardTransformer.getInstance());
        transformers.put(EEPROM.class, GeneralMemoryStandardTransformer.getInstance());
        transformers.put(RAM.class, GeneralMemoryStandardTransformer.getInstance());
        transformers.put(IO.class, IOStandardTransformer.getInstance());
        transformers.put(Node.class, NodeStandardTransformer.getInstance());
        transformers.put(OS.class, OSStandardTransformer.getInstance());
        transformers.put(PWM.class, PWMStandardTransformer.getInstance());
        transformers.put(PeripheralInfoGetter.class, PeripheralInfoGetterStandardTransformer.getInstance());
        transformers.put(SPI.class, SPIStandardTransformer.getInstance());
        transformers.put(Thermometer.class, ThermometerStandardTransformer.getInstance());
        transformers.put(UART.class, UARTStandardTransformer.getInstance());
    }
    
    
    
    /**
     * Returns method ID transformer for specified device interface.
     * @param devIface device interface, which to return the transformer for
     * @return method ID transformer for specified device interface.
     */
    public MethodIdTransformer getTransformer(Class devIface) {
        return transformers.get(devIface);
    }
    
 }