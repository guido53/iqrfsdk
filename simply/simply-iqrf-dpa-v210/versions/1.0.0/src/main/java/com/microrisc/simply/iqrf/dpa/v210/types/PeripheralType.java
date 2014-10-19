
package com.microrisc.simply.iqrf.dpa.v210.types;

/**
 * Peripheral types.
 * 
 * @author Michal Konopa
 */
public enum PeripheralType {
    DUMMY               (0x00),
    COORDINATOR         (0x01),
    NODE                (0X02),
    OS                  (0x03),
    EEPROM              (0x04),
    BLOCK_EEPROM        (0x05),
    RAM                 (0x06),
    LED                 (0x07),
    SPI                 (0x08),
    IO                  (0x09),
    UART                (0x0A),
    THERMOMETER         (0x0B),
    ADC                 (0x0C),
    PWM                 (0x0D),
    FRC                 (0x0E),
    USER_AREA           (0x80);
    
    
    // peripheral type
    private final int type;
    
    
    private PeripheralType(int type) {
        this.type = type;
    }
    
    /**
     * Returns integer value of peripheral type.
     * @return integer value of peripheral type.
     */
    public int getTypeValue() {
        return type;
    }
}
