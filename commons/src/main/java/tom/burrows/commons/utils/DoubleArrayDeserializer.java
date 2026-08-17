package tom.burrows.commons.utils;

import java.io.IOException;
import java.util.List;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.type.CollectionType;
import tools.jackson.databind.type.TypeFactory;
import tools.jackson.databind.ValueDeserializer;


public class DoubleArrayDeserializer extends ValueDeserializer<List<Double>>{

    private static final String SPARKLINE = "sparkline_in_7d";
    private static final String PRICES = "price";
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final CollectionType collectionType = 
        TypeFactory
            .createDefaultInstance()
            .constructCollectionType(List.class, Double.class);
    @Override
    public List<Double> deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        JsonNode objNode = ctxt.readTree(p);
        JsonNode jNode = objNode.get(PRICES);
        
        if (jNode == null
            || !jNode.isArray()
            || jNode.isEmpty()
        ) return null;

        return mapper.readerFor(collectionType).readValue(jNode);
    }
    
}
