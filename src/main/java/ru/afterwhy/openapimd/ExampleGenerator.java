package ru.afterwhy.openapimd;

import io.swagger.v3.oas.models.media.*;
import ru.afterwhy.openapimd.model.SpecSchema;
import ru.afterwhy.openapimd.model.SpecSchemaProperty;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ExampleGenerator {
    private static final Random random = new Random();

    public static Object getExample(Schema<?> schema, SpecSchema itemSpec, List<SpecSchemaProperty> parameters, SchemaGetter storage, Locale locale) {
        var resourceBundle = ResourceBundle.getBundle("locale", locale);

        if (parameters.isEmpty()) {
            return getExampleFromSchema(schema, itemSpec, resourceBundle);
        }

        return parameters
                .stream()
                .map(p -> Map.entry(p.name(), p.example()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Object getExampleFromSchema(Schema<?> schema, SpecSchema itemSpec, ResourceBundle resourceBundle) {
        // Проверка на типы схем
        return switch (schema) {
            case ComposedSchema _, ObjectSchema _, JsonSchema _, MapSchema _ -> new HashMap<>();
            case ArraySchema arraySchema -> getExampleForArray(arraySchema, itemSpec, resourceBundle);
            case ByteArraySchema byteArraySchema -> getExampleForByteArray(byteArraySchema);
            case FileSchema fileSchema -> getExampleForFile(fileSchema);
            case BooleanSchema booleanSchema -> getExampleForBoolean(booleanSchema);
            case IntegerSchema integerSchema -> getExampleForInteger(integerSchema);
            case NumberSchema numberSchema -> getExampleForNumber(numberSchema);
            case StringSchema _, PasswordSchema _ -> getExampleForString((Schema<String>) schema, resourceBundle);
            case EmailSchema emailSchema -> getExampleForEmail(emailSchema);
            case UUIDSchema uuidSchema -> getExampleForUuid(uuidSchema);
            case DateSchema dateSchema -> getExampleForDate(dateSchema);
            case DateTimeSchema dateTimeSchema -> getExampleForDateTime(dateTimeSchema);
            case null, default ->
                    throw new UnsupportedOperationException("Unsupported schema type: " + (schema != null ? schema.getClass().getSimpleName() : null));
        };
    }

    private static Object getExampleForByteArray(ByteArraySchema byteArraySchema) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    private static Object getExampleForFile(FileSchema fileSchema) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    private static Object getExampleForEmail(EmailSchema emailSchema) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    private static List<Object> getExampleForArray(ArraySchema schema, SpecSchema itemSpec, ResourceBundle resourceBundle) {
        List<Object> exampleArray = new ArrayList<>();
        var itemsSchema = schema.getItems();

        if (itemsSchema != null) {
            exampleArray.add(getExampleFromSchema(itemsSchema, itemSpec.itemSpec(), resourceBundle));
        } else {
            exampleArray.add(new Object());
        }

        return exampleArray;
    }

    private static String getExampleForString(Schema<String> schema, ResourceBundle resourceBundle) {
        if (schema.getExample() != null) {
            return schema.getExample().toString();
        }

        return generateExample(
                schema,
                Object::toString,
                () -> resourceBundle.getString("default-examples.string")
        );
    }

    private static Boolean getExampleForBoolean(BooleanSchema schema) {
        return generateExample(
                schema,
                o -> Objects.equals(o.toString(), "true"),
                random::nextBoolean
        );
    }

    private static UUID getExampleForUuid(UUIDSchema schema) {
        return generateExample(
                schema,
                example -> {
                    try {
                        return UUID.fromString(example.toString());
                    } catch (Exception e) {
                        //todo: log
                        return null;
                    }
                },
                UUID::randomUUID
        );
    }

    private static Number getExampleForInteger(IntegerSchema schema) {
        return switch (schema.getFormat()) {
            case "int32" -> generateExampleForInt(schema);
            case null -> generateExampleForInt(schema);
            case "int64" -> generateExampleForLong(schema);
            default -> throw new IllegalStateException("Unexpected integer format value: " + schema.getFormat());
        };
    }

    private static Integer generateExampleForInt(IntegerSchema schema) {
        return generateExample(
                schema,
                example -> parseNumber(example, Integer::parseInt),
                random::nextInt
        );
    }

    private static Long generateExampleForLong(IntegerSchema schema) {
        return generateExample(
                schema,
                example -> parseNumber(example, Long::parseLong),
                random::nextLong
        );
    }

    private static Number getExampleForNumber(NumberSchema schema) {
        return switch (schema.getFormat()) {
            case "float" -> generateExampleForFloat(schema);
            case "numner" -> generateExampleForDouble(schema);
            default -> throw new IllegalStateException("Unexpected integer format value: " + schema.getFormat());
        };
    }

    private static Float generateExampleForFloat(NumberSchema schema) {
        return generateExample(
                schema,
                example -> {
                    try {
                        return parseNumber(example, Float::parseFloat);
                    } catch (Exception e) {
                        //todo: log
                        return null;
                    }
                },
                random::nextFloat
        );
    }

    private static Double generateExampleForDouble(NumberSchema schema) {
        return generateExample(
                schema,
                example -> parseNumber(example, Double::parseDouble),
                random::nextDouble
        );
    }

    private static <T extends Number> T parseNumber(Object example, Function<String, T> parseFunction) {
        try {
            return parseFunction.apply(example.toString());
        } catch (Exception e) {
            //todo: log
            return null;
        }
    }

    private static String getExampleForDate(DateSchema schema) {
        return generateExample(
                schema,
                Object::toString,
                () -> DateTimeFormatter.ISO_DATE.format(LocalDateTime.now().minusDays(random.nextInt(365)))
        );
    }

    private static String getExampleForDateTime(DateTimeSchema schema) {
        return generateExample(
                schema,
                Object::toString,
                () -> DateTimeFormatter.ISO_DATE_TIME.format(OffsetDateTime.now().minusDays(random.nextInt(365)))
        );
    }

    private static <T, E> E generateExample(Schema<T> schema, Function<Object, E> castFunction, Supplier<E> defaultValueSupplier) {
        E example = null;
        if (schema.getExample() != null) {
            example = castFunction.apply(schema.getExample());
        }

        if (example != null) {
            return example;
        }

        return defaultValueSupplier.get();
    }
}
