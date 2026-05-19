package com.btl.transport.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer offsetDateTimeDeserializer() {
        return builder -> builder.deserializerByType(OffsetDateTime.class, new StdDeserializer<>(OffsetDateTime.class) {
            @Override
            public OffsetDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                String text = p.getText().trim();
                try {
                    return OffsetDateTime.parse(text);
                } catch (DateTimeParseException e) {
                    return LocalDateTime.parse(text).atOffset(ZoneOffset.UTC);
                }
            }
        });
    }
}
