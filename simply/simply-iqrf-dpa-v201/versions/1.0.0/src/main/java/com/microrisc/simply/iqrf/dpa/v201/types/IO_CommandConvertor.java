
package com.microrisc.simply.iqrf.dpa.v201.types;

import com.microrisc.simply.types.PrimitiveConvertor;
import com.microrisc.simply.types.ValueConversionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides functionality for conversion between array of bytes and 
 * {@code IO_Command} objects.
 * 
 * @author Michal Konopa
 */
public final class IO_CommandConvertor extends PrimitiveConvertor {
    /** Logger. */
    private static final Logger logger = LoggerFactory.getLogger(IO_CommandConvertor.class);
    
    /** Singleton. */
    private static final IO_CommandConvertor instance = new IO_CommandConvertor();
    
    private IO_CommandConvertor() {}
    
    /**
     * @return {@code IO_CommandConvertor} instance 
     */
    static public IO_CommandConvertor getInstance() {
        return instance;
    }
    
    /** Size of returned response. */
    static public final int TYPE_SIZE = 3;
    
    @Override
    public int getGenericTypeSize() {
        return TYPE_SIZE;
    }
    
    // postitions of fields
    static private final int FIRST_FIELD_POS = 0;
    static private final int SECOND_FIELD_POS = 1;
    static private final int THIRD_FIELD_POS = 2;
    
    
    @Override
    public short[] toProtoValue(Object value) throws ValueConversionException {
        logger.debug("toProtoValue - start: value={}", value);
        
        if (!(value instanceof IO_Command)) {
            throw new ValueConversionException("Value to convert has not proper type.");
        }
        
        short[] protoValue = new short[TYPE_SIZE];
        IO_Command ioCommand = (IO_Command)value;
        
        protoValue[FIRST_FIELD_POS] = (short)ioCommand.getFirstField();
        protoValue[SECOND_FIELD_POS] = (short)ioCommand.getSecondField();
        protoValue[THIRD_FIELD_POS] = (short)ioCommand.getThirdField();
        
        logger.debug("toProtoValue - end: {}", protoValue);
        return protoValue;
    }
    
    /**
     * Currently not supported. Throws {@code UnsupportedOperationException }.
     * @throws UnsupportedOperationException 
     */
    @Override
    public Object toObject(short[] protoValue) throws ValueConversionException {
        throw new UnsupportedOperationException("Currently not supported");
    }
}
