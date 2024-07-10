package com.unulearner.backend.storage.extensions;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

public class OnDiskURLSerializer extends JsonSerializer<String> {
    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value != null && !value.startsWith("/")) {
            gen.writeString("/" + value);
        } else {
            gen.writeString(value);
        }
    }
}
