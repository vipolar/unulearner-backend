package com.unulearner.backend.storage.extensions;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.Date;

public class OnCommitDateSerializer extends JsonSerializer<Date> {
    private static final Date DEFAULT_DATE = new Date(0);

    @Override
    public void serialize(Date value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value != null && !value.equals(DEFAULT_DATE)) {
            gen.writeString(value.toString());
        } else {
            gen.writeString("N/A");
        }
    }
}
