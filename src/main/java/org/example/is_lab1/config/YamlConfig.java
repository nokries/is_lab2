package org.example.is_lab1.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

// Разбор YAML-файлов импорта
public final class YamlConfig {

    private static final YAMLMapper YAML_MAPPER;

    static {
        YAML_MAPPER = new YAMLMapper();
        // Подключает поддержку java.time (ZonedDateTime у города) и т.п.
        YAML_MAPPER.findAndRegisterModules();
        // Строгий разбор: 12.5 в целочисленном поле - ошибка, а не молчаливое округление до 12
        YAML_MAPPER.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        // Не включаем FAIL_ON_NULL_FOR_PRIMITIVES: в DTO есть примитивы (id, capital, ...), которых в файле нет
        // (id выдаёт база), и Jackson считает их отсутствие "null для примитива". Обязательность полей
        // проверяется Bean Validation (@NotNull / @Positive) после разбора файла.
        YAML_MAPPER.disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
        // Неизвестное поле в записи (опечатка в названии) - ошибка. Это значение по умолчанию, указано явно
        YAML_MAPPER.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    private YamlConfig() {
    }

    public static YAMLMapper getYamlMapper() {
        return YAML_MAPPER;
    }
}
