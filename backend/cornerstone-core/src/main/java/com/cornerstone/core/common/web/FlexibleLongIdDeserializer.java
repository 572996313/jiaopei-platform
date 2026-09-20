package com.cornerstone.core.common.web;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

public class FlexibleLongIdDeserializer extends JsonDeserializer<Long> {

    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.VALUE_NUMBER_INT) {
            return parser.getLongValue();
        }
        if (token == JsonToken.VALUE_STRING) {
            return ExternalIdParser.parseOptional(parser.getText(), parser.currentName());
        }
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        return (Long) context.handleUnexpectedToken(Long.class, parser);
    }
}
