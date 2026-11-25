package org.example.is_lab1.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

public final class YamlConfig {

    private static final YAMLMapper YAML_MAPPER;

    static {
        YAML_MAPPER = new YAMLMapper();
        YAML_MAPPER.findAndRegisterModules();
        YAML_MAPPER.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        YAML_MAPPER.disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
        YAML_MAPPER.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    private YamlConfig() {
    }

    public static YAMLMapper getYamlMapper() {
        return YAML_MAPPER;
    }
}
