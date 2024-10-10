package ru.afterwhy.openapimd;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.OpenAPIV3Parser;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Main {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static Map<String, Schema> allSchemas = new HashMap<>();

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java OpenApiToMarkdown <path-to-openapi-file>");
            return;
        }

        String openApiFilePath = args[0];
        OpenAPI openAPI = new OpenAPIV3Parser().read(openApiFilePath);

        if (openAPI == null) {
            System.out.println("Error parsing the OpenAPI file.");
            return;
        }

        if (openAPI.getComponents() != null && openAPI.getComponents().getSchemas() != null) {
            allSchemas = openAPI.getComponents().getSchemas();
        }

        try (FileWriter writer = new FileWriter("api-documentation.md")) {
            generateMarkdown(openAPI, writer);
        } catch (IOException e) {
            System.out.println("Error writing to markdown file: " + e.getMessage());
        }
    }

    private static void generateMarkdown(OpenAPI openAPI, FileWriter writer) throws IOException {
        Info info = openAPI.getInfo();
        // Заголовок первого уровня (title из info)
        writer.write("# " + info.getTitle() + "\n\n");

        // Описание API (description из info)
        if (info.getDescription() != null) {
            writer.write(info.getDescription() + "\n\n");
        }

        // Заголовок второго уровня - API
        writer.write("## API\n\n");

        // Группировка эндпойнтов по тэгам
        Map<String, List<Map.Entry<String, OperationWithMethod>>> operationsByTag = groupOperationsByTag(openAPI.getPaths());

        for (String tag : operationsByTag.keySet()) {
            writer.write("- [" + tag + "](#" + tag.toLowerCase() + ")\n");
            for (Map.Entry<String, OperationWithMethod> entry : operationsByTag.get(tag)) {
                Operation operation = entry.getValue().operation;
                String summary = operation.getSummary();
                if (summary == null) {
                    summary = entry.getKey(); // Используем путь, если summary не указано
                }
                writer.write("  - [" + summary + "](#" + summary.replace(" ", "-").replace("/", "-").toLowerCase() + ")\n");
            }
        }

        writer.write("\n");

        // Описание тэгов и эндпойнтов
        for (String tag : operationsByTag.keySet()) {
            writer.write("### " + tag + "\n\n");

            for (Map.Entry<String, OperationWithMethod> entry : operationsByTag.get(tag)) {
                String endpoint = entry.getKey();
                OperationWithMethod operationWithMethod = entry.getValue();
                Operation operation = operationWithMethod.operation;
                String httpMethod = operationWithMethod.httpMethod;
                String summary = operation.getSummary();
                if (summary == null) {
                    summary = endpoint; // Используем путь, если summary не указано
                }

                // Заголовок 4 уровня с использованием summary
                writer.write("#### " + summary + "\n\n");

                // Метод, путь и operationId
                writer.write("`" + httpMethod.toUpperCase() + " " + endpoint + "`\n\n");
                writer.write("**Operation ID:** `" + operation.getOperationId() + "`\n\n");

                // Описание (description)
                if (operation.getDescription() != null) {
                    writer.write(operation.getDescription() + "\n\n");
                }

                // Параметры запроса
                if (operation.getParameters() != null && !operation.getParameters().isEmpty()) {
                    writer.write("##### Параметры запроса\n\n");
                    writer.write("| Тип | Название | Описание | Обязательный |\n");
                    writer.write("|-----|----------|----------|--------------|\n");
                    for (Parameter parameter : operation.getParameters()) {
                        String type = translateInType(parameter.getIn());
                        String required = parameter.getRequired() ? "+" : "-";
                        writer.write("| " + type + " | " + parameter.getName() + " | "
                                + (parameter.getDescription() != null ? parameter.getDescription() : "")
                                + " | " + required + " |\n");
                    }
                    writer.write("\n");
                }

                if (operation.getRequestBody() != null) {
                    writer.write("### Запрос\n");
                    String requestFields = generateRequestBody(operation);
                    writer.write(requestFields);
                }

                if (!operation.getResponses().isEmpty()) {
                    writer.write("### Ответ\n");
                    String responseFields = generateResponseBody(operation);
                    writer.write(responseFields);
                }
            }
        }
    }

    private static Map<String, List<Map.Entry<String, OperationWithMethod>>> groupOperationsByTag(Paths paths) {
        Map<String, List<Map.Entry<String, OperationWithMethod>>> operationsByTag = new HashMap<>();

        for (Map.Entry<String, PathItem> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            PathItem pathItem = pathEntry.getValue();

            // Собираем операции для каждого метода и пути
            if (pathItem.getGet() != null) {
                addOperationToTags(operationsByTag, new OperationWithMethod("get", pathItem.getGet()), path);
            }
            if (pathItem.getPost() != null) {
                addOperationToTags(operationsByTag, new OperationWithMethod("post", pathItem.getPost()), path);
            }
            if (pathItem.getPut() != null) {
                addOperationToTags(operationsByTag, new OperationWithMethod("put", pathItem.getPut()), path);
            }
            if (pathItem.getDelete() != null) {
                addOperationToTags(operationsByTag, new OperationWithMethod("delete", pathItem.getDelete()), path);
            }
        }

        return operationsByTag;
    }

    private static void addOperationToTags(Map<String, List<Map.Entry<String, OperationWithMethod>>> operationsByTag, OperationWithMethod operationWithMethod, String path) {
        Operation operation = operationWithMethod.operation;
        if (operation.getTags() != null) {
            for (String tag : operation.getTags()) {
                operationsByTag.computeIfAbsent(tag, k -> new ArrayList<>()).add(new AbstractMap.SimpleEntry<>(path, operationWithMethod));
            }
        }
    }

    private static String translateInType(String in) {
        switch (in) {
            case "query":
                return "запрос";
            case "path":
                return "путь";
            case "header":
                return "заголовок";
            case "cookie":
                return "cookie";
            default:
                return "неизвестно";
        }
    }

    private static String generateExample(Operation operation) {
        // Если есть requestBody, пытаемся сгенерировать пример на его основе
        RequestBody requestBody = operation.getRequestBody();
        if (requestBody != null && requestBody.getContent() != null) {
            MediaType mediaType = requestBody.getContent().get("application/json");
            if (mediaType != null && mediaType.getSchema() != null) {
                Schema<?> schema = getSchemaFromMediaType(mediaType);
                return generateExampleFromSchema(schema);
            }
        }

        // Если нет requestBody, возвращаем стандартный пример
        return "{ \"example\": \"value\" }";
    }

    private static Schema getSchemaFromMediaType(MediaType mediaType) {
        return resolveSchemaReference(mediaType.getSchema().get$ref());
    }

    private static String generateExampleFromSchema(Schema<?> schema) {
        if (schema.getExample() != null) {
            String example = schema.getExample().toString();
            if (schema instanceof ComposedSchema || schema instanceof ObjectSchema) {
                return formatJson(example);
            } else {
                return example;
            }
        }

        // Проверка на типы схем
        if (schema instanceof ComposedSchema) {
            // Обработка allOf, anyOf, oneOf через ComposedSchema
            ComposedSchema composedSchema = (ComposedSchema) schema;
            if (composedSchema.getAllOf() != null) {
                return generateExampleForAllOf(composedSchema.getAllOf());
            } else if (composedSchema.getAnyOf() != null) {
                return generateExampleForAnyOf(composedSchema.getAnyOf());
            } else if (composedSchema.getOneOf() != null) {
                return generateExampleForOneOf(composedSchema.getOneOf());
            }
        } else if (schema instanceof ObjectSchema) {
            return generateExampleForObject((ObjectSchema) schema);
        } else if (schema instanceof ArraySchema) {
            return generateExampleForArray((ArraySchema) schema);
        } else if (schema instanceof StringSchema) {
            return generateExampleForString((StringSchema) schema);
        } else if (schema instanceof UUIDSchema) {
            return generateExampleForUuid((UUIDSchema) schema);
        } else if (schema instanceof DateSchema) {
            return generateExampleForDate((DateSchema) schema);
        } else if (schema.get$ref() != null) {
            // Обработка ссылки на другую схему
            return resolveExampleSchemaReference(schema.get$ref());
        }

        // Для неизвестных типов возвращаем пустой объект
        throw new UnsupportedOperationException("Unsupported schema type: " + schema.getClass().getSimpleName());
    }

    // Разрешение ссылки $ref на другую схему
    private static String resolveExampleSchemaReference(String ref) {
        // Ссылки обычно выглядят как "#/components/schemas/SchemaName"
        if (ref.startsWith("#/components/schemas/")) {
            String schemaName = ref.substring("#/components/schemas/".length());
            Schema<?> referencedSchema = allSchemas.get(schemaName);

            if (referencedSchema != null) {
                // Рекурсивно генерируем пример для схемы, на которую ссылается $ref
                return generateExampleFromSchema(referencedSchema);
            }
        }
        return "{}"; // Если не смогли найти схему, возвращаем пустой объект
    }

    private static String generateExampleForAllOf(List<Schema> allOfSchemas) {
        Map<String, Object> combinedExample = new LinkedHashMap<>();

        // Объединяем все примеры из всех схем в allOf
        for (Schema<?> schema : allOfSchemas) {
            String examplePart = generateExampleFromSchema(schema);
            if (examplePart.startsWith("{") && examplePart.endsWith("}")) {
                Map<String, Object> parsedPart = parseJsonToMap(examplePart);
                combinedExample.putAll(parsedPart);
            }
        }

        return mapToJson(combinedExample);
    }

    private static String generateExampleForAnyOf(List<Schema> anyOfSchemas) {
        // Пример: возвращаем первый пример из списка anyOf
        if (!anyOfSchemas.isEmpty()) {
            return generateExampleFromSchema(anyOfSchemas.get(0));
        }
        return "{}";
    }

    private static String generateExampleForOneOf(List<Schema> oneOfSchemas) {
        // Пример: возвращаем первый пример из списка oneOf
        if (!oneOfSchemas.isEmpty()) {
            return generateExampleFromSchema(oneOfSchemas.get(0));
        }
        return "{}";
    }

    private static String generateExampleForObject(ObjectSchema schema) {
        Map<String, Object> exampleObject = new LinkedHashMap<>();
        Map<String, Schema> properties = schema.getProperties();

        if (properties != null) {
            for (Map.Entry<String, Schema> entry : properties.entrySet()) {
                String propertyName = entry.getKey();
                Schema propertySchema = entry.getValue();

                // Проверка на наличие примера
                if (propertySchema.getExample() != null) {
                    exampleObject.put(propertyName, propertySchema.getExample());
                } else if (propertySchema.get$ref() != null) {
                    // Если есть ссылка на другую схему
                    exampleObject.put(propertyName, resolveExampleSchemaReference(propertySchema.get$ref()));
                } else {
                    exampleObject.put(propertyName, generateExampleFromSchema(propertySchema));
                }
            }
        }

        return mapToJson(exampleObject);
    }

    private static String generateExampleForArray(ArraySchema schema) {
        List<Object> exampleArray = new ArrayList<>();
        Schema<?> itemsSchema = schema.getItems();

        if (itemsSchema != null) {
            exampleArray.add(generateExampleFromSchema(itemsSchema));
        }

        return listToJson(exampleArray);
    }

    private static String generateExampleForString(StringSchema schema) {
        StringBuilder result = new StringBuilder();

        if (schema.getExample() != null) {
            appendStringExample(schema.getExample(), result);
        }

        return result.toString();
    }

    private static String generateExampleForUuid(UUIDSchema schema) {
        StringBuilder result = new StringBuilder();

        if (schema.getExample() != null) {
            appendStringExample(schema.getExample(), result);
        } else {
            result.append("\"").append(UUID.randomUUID()).append("\"");
        }

        return result.toString();
    }

    private static String generateExampleForDate(DateSchema schema) {
        StringBuilder result = new StringBuilder();

        if (schema.getExample() != null) {
            appendStringExample(schema.getExample(), result);
        } else {
            result.append("\"").append(DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.now())).append("\"");
        }

        return result.toString();
    }

    private static void appendStringExample(Object schema, StringBuilder result) {
        String example = schema.toString();
        if (!example.startsWith("\"")) {
            result.append("\"");
        }
        result.append(example);
        if (!example.endsWith("\"")) {
            result.append("\"");
        }
    }

    // Метод для конвертации карты в JSON-строку
    private static String mapToJson(Map<String, Object> map) {
        StringBuilder jsonBuilder = new StringBuilder("{\n");
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            jsonBuilder.append("  \"").append(entry.getKey()).append("\": ");
            jsonBuilder.append(formatValue(entry.getValue())).append(",\n");
        }
        // Удаление последней запятой
        if (!map.isEmpty()) {
            jsonBuilder.setLength(jsonBuilder.length() - 2);
        }
        jsonBuilder.append("\n}");
        return formatJson(jsonBuilder.toString());
    }

    private static String formatJson(String json) {
        try {
            Map<String, Object> stringObjectMap = objectMapper.readValue(json, new TypeReference<>() {});
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(stringObjectMap);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    // Метод для конвертации списка в JSON-строку
    private static String listToJson(List<Object> list) {
        StringBuilder jsonBuilder = new StringBuilder("[\n");
        for (Object value : list) {
            jsonBuilder.append("  ").append(formatValue(value)).append(",\n");
        }
        // Удаление последней запятой
        if (!list.isEmpty()) {
            jsonBuilder.setLength(jsonBuilder.length() - 2);
        }
        jsonBuilder.append("\n]");
        return jsonBuilder.toString();
    }

    // Форматирование значения в JSON (строки в кавычках)
    private static String formatValue(Object value) {
        if (value instanceof Map<?,?>) {
            return mapToJson((Map<String, Object>) value);
        }
        if (value instanceof String && !((String) value).startsWith("{")) {
            return "\"" + value + "\"";
        } else if (!(value instanceof String) && !(value instanceof Number) && !(value instanceof ArrayNode)) {
            return "\"" + value + "\"";
        }
        return value.toString();
    }

    // Пример парсера JSON-строки в Map (должно быть реализовано с библиотекой)
    private static Map<String, Object> parseJsonToMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static class OperationWithMethod {
        String httpMethod;
        Operation operation;

        public OperationWithMethod(String httpMethod, Operation operation) {
            this.httpMethod = httpMethod;
            this.operation = operation;
        }
    }

    // properties

    private static String generateRequestBody(Operation operation) {
        // Если есть requestBody, пытаемся сгенерировать пример на его основе
        StringBuilder sb = new StringBuilder();
        RequestBody requestBody = operation.getRequestBody();
        if (requestBody != null && requestBody.getContent() != null) {
            MediaType mediaType = requestBody.getContent().get("application/json");
            if (mediaType != null && mediaType.getSchema() != null) {
                Schema<?> schema = getSchemaFromMediaType(mediaType);
                sb.append(generatePropertiesDescription(schema));

                // Пример запроса
                sb.append("##### Пример запроса\n\n");
                String example = mediaType.getExample() != null ? mediaType.getExample().toString() : generateExample(operation);
                sb.append("```json\n" + example + "\n```\n\n");
            }
        }


        return sb.toString();
    }

    private static String generateResponseBody(Operation operation) {
        // Если есть requestBody, пытаемся сгенерировать пример на его основе
        StringBuilder sb = new StringBuilder();
        for (String response : operation.getResponses().keySet()) {
            ApiResponse apiResponse = operation.getResponses().get(response);
            sb.append("#### ").append(response).append("\n\n");
            MediaType mediaType = apiResponse.getContent().get("application/json");
            if (mediaType != null && mediaType.getSchema() != null) {
                Schema<?> schema = getSchemaFromMediaType(mediaType);
                sb.append(generatePropertiesDescription(schema));

                sb.append("##### Пример ответа\n\n");
                String example = mediaType.getExample() != null ? mediaType.getExample().toString() : generateExampleFromSchema(schema);
                sb.append("```json\n" + example + "\n```\n\n");
            }
        }

        return sb.toString();
    }

    // Метод для генерации описания полей с поддержкой allOf
    private static String generatePropertiesDescription(Schema<?> schema) {
        Map<String, Schema> properties = new LinkedHashMap<>();

        // Если это составная схема, обрабатываем allOf
        if (schema instanceof ComposedSchema) {
            ComposedSchema composedSchema = (ComposedSchema) schema;
            if (composedSchema.getAllOf() != null) {
                for (Schema<?> innerSchema : composedSchema.getAllOf()) {
                    properties.putAll(getProperties(innerSchema));
                }
            }
        } else {
            // Если это обычная схема, просто извлекаем свойства
            properties.putAll(getProperties(schema));
        }

        // Генерация описания в виде таблицы
        return generateMarkdownTableForProperties(properties);
    }

    // Извлечение полей (properties) из схемы
    private static Map<String, Schema> getProperties(Schema<?> schema) {
        Map<String, Schema> properties = new LinkedHashMap<>();

        // Если это ссылка на другую схему, разрешаем ее
        if (schema.get$ref() != null) {
            Schema<?> referencedSchema = resolveSchemaReference(schema.get$ref());
            if (referencedSchema != null && referencedSchema.getProperties() != null) {
                properties.putAll(referencedSchema.getProperties());
            }
        } else if (schema.getProperties() != null) {
            properties.putAll(schema.getProperties());
        }

        return properties;
    }

    // Разрешение ссылки $ref на другую схему
    private static Schema<?> resolveSchemaReference(String ref) {
        if (ref.startsWith("#/components/schemas/")) {
            String schemaName = ref.substring("#/components/schemas/".length());
            return allSchemas.get(schemaName);
        }
        return null; // Если не удалось найти ссылку
    }

    // Генерация Markdown-таблицы для свойств
    private static String generateMarkdownTableForProperties(Map<String, Schema> properties) {
        StringBuilder tableBuilder = new StringBuilder();
        tableBuilder.append("| Тип | Название | Описание | Обязательный |\n");
        tableBuilder.append("|-----|----------|----------|--------------|\n");

        for (Map.Entry<String, Schema> entry : properties.entrySet()) {
            String propertyName = entry.getKey();
            Schema<?> propertySchema = entry.getValue();

            String type = getSchemaType(propertySchema);
            String description = propertySchema.getDescription() != null ? propertySchema.getDescription() : "—";
            String required = propertySchema.getRequired() != null && propertySchema.getRequired().contains(propertyName) ? "+" : "-";

            // Тип поля. Если это массив, указываем тип элементов
            if (propertySchema instanceof ArraySchema) {
                ArraySchema arraySchema = (ArraySchema) propertySchema;
                type = "array of " + arraySchema.getItems().getType();
            }

            // Добавляем строку в таблицу
            tableBuilder.append("| ")
                    .append(translateType(type)) // Переводим тип на русский язык
                    .append(" | ").append(propertyName)
                    .append(" | ").append(description)
                    .append(" | ").append(required)
                    .append(" |\n");
        }

        return tableBuilder.toString();
    }

    private static String getSchemaType(Schema<?> propertySchema) {
        if (propertySchema.getType() != null) {
            return propertySchema.getType();
        } if (propertySchema.get$ref() != null) {
            Schema<?> schema = resolveSchemaReference(propertySchema.get$ref());
            if (schema != null) {
                return getSchemaType(schema);
            }
        }
        return "object";
    }

    // Перевод типов на русский язык
    private static String translateType(String type) {
        switch (type) {
            case "string":
                return "строка";
            case "integer":
                return "число";
            case "boolean":
                return "логический";
            case "array":
                return "массив";
            case "object":
                return "объект";
            default:
                return type;
        }
    }
}
