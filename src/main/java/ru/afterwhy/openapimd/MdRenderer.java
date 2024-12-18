package ru.afterwhy.openapimd;

import ru.afterwhy.openapimd.model.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

public class MdRenderer {

    private final ResourceBundle resourceBundle;

    public MdRenderer(Locale locale) {
        this.resourceBundle = ResourceBundle.getBundle("locale", locale);
    }

    public String render(Specification spec) {
        try(var os = new ByteArrayOutputStream(); var writer = new OutputStreamWriter(os)) {
            render(spec, writer);
            return os.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    public void render(Specification spec, OutputStreamWriter writer) throws IOException {
        // Заголовок первого уровня (title из info)
        writer.write("# " + spec.title() + "\n\n");

        // Описание API (description из info)
        if (spec.description() != null) {
            writer.write(spec.description() + "\n\n");
        }

        // Заголовок второго уровня - API
        writer.write("## API\n\n");

        // Группировка эндпойнтов по тэгам
        for (SpecTag tag : spec.tags()) {
            writer.write("- [" + tag.name() + "](#" + stringToLink(tag.name()) + ")\n");
            for (SpecOperation operation : tag.operations()) {
                String summary = spec.getEndpoint(operation.httpMethod(), operation.path()).getSummaryEvenIfNotExists();
                writer.write("  - [" + summary + "](#" + stringToLink(summary) + ")\n");
            }
        }

        writer.write("\n");

        // Описание тэгов и эндпойнтов
        for (SpecTag tag : spec.tags()) {
            writer.write("### " + tag.name() + "\n\n");

            for (SpecOperation operation : tag.operations()) {
                String path = operation.path();
                var endpoint = spec.getEndpoint(operation.httpMethod(), operation.path());
                String summary = endpoint.getSummaryEvenIfNotExists();

                // Заголовок 4 уровня с использованием summary
                writer.write("#### " + summary + "\n\n");

                // Метод, путь и operationId
                writer.write("`" + endpoint.method().name().toUpperCase() + " " + path + "`\n\n");
                writer.write("**Operation ID:** `" + endpoint.operationId() + "`\n\n");

                // Описание (description)
                if (endpoint.description() != null) {
                    writer.write(endpoint.description() + "\n\n");
                }

                // Параметры запроса
                if (endpoint.parameters() != null && !endpoint.parameters().isEmpty()) {
                    writer.write("##### %s\n\n".formatted(resourceBundle.getString("schema-properties.request.header")));
                    var typeHeader = resourceBundle.getString("schema-parameters.table-header.type");
                    var nameHeader = resourceBundle.getString("schema-parameters.table-header.name");
                    var descriptionHeader = resourceBundle.getString("schema-parameters.table-header.description");
                    var requiredHeader = resourceBundle.getString("schema-parameters.table-header.required");
                    writer.write("| %s | %s | %s | %s |\n".formatted(typeHeader, nameHeader, descriptionHeader, requiredHeader));
                    writer.write("|----|----|----|----|\n");
                    for (EndpointParameter parameter : endpoint.parameters()) {
                        String type = getEndpointParameterTypeLocalized(parameter);
                        var name = parameter.name();
                        var description = parameter.description() != null ? parameter.description() : "";
                        String required = parameter.required() ? "+" : "-";
                        writer.write("| " + type + " | " + name + " | " + description + " | " + required + " |\n");
                    }
                    writer.write("\n");
                }

                if (endpoint.request() != null) {
                    writer.write("### %s\n".formatted(resourceBundle.getString("schema-properties.request")));
                    for (Map.Entry<String, SpecSchema> requestVariant : endpoint.request().content().entrySet()) {
                        writer.write("#### %s\n".formatted(requestVariant.getKey()));
                        writer.write(generateMarkdownTableForProperties(requestVariant.getValue().properties()));
                    }
                }

                if (!endpoint.responses().responses().isEmpty()) {
                    writer.write("### %s\n".formatted(resourceBundle.getString("schema-properties.response")));
                    for (Map.Entry<Integer, ExchangeContent> responsesByHttpCode : endpoint.responses().responses().entrySet()) {
                        writer.write("#### %s\n".formatted(responsesByHttpCode.getKey()));
                        for (Map.Entry<String, SpecSchema> requestVariant : responsesByHttpCode.getValue().content().entrySet()) {
                            writer.write("##### %s\n".formatted(requestVariant.getKey()));
                            writer.write(generateMarkdownTableForProperties(requestVariant.getValue().properties()));
                        }
                    }
                }
            }
        }
    }

    private String getEndpointParameterTypeLocalized(EndpointParameter parameter) {
        var type = parameter.type();
        var suffix = type != null ? type.name().toLowerCase() : "unspecified";
        return resourceBundle.getString("endpoint-parameter-type." + suffix);
    }

    private static String stringToLink(String str) {
        return str.replace(" ", "-").replace("/", "-").toLowerCase();
    }

    private String generateMarkdownTableForProperties(List<SpecSchemaProperty> properties) {
        StringBuilder tableBuilder = new StringBuilder();
        var typeHeader = resourceBundle.getString("schema-properties.table-header.type");
        var nameHeader = resourceBundle.getString("schema-properties.table-header.name");
        var descriptionHeader = resourceBundle.getString("schema-properties.table-header.description");
        var requiredHeader = resourceBundle.getString("schema-properties.table-header.required");
        tableBuilder.append("| ").append(typeHeader).append(" | ").append(nameHeader).append(" | ").append(descriptionHeader).append(" | ").append(requiredHeader).append(" |\n");
        tableBuilder.append("|-----|----------|----------|--------------|\n");

        for (var parameter : properties) {
            String propertyName = parameter.name();
            var propertySchema = parameter.schema();

            String type = getPropertyTypeName(parameter.type(), parameter.schema().itemSpec());
            String description = propertySchema.description() != null ? propertySchema.description() : "—";
            String required = parameter.required() ? "+" : "-";

            // Добавляем строку в таблицу
            tableBuilder.append("| ")
                    .append(type)
                    .append(" | ").append(propertyName)
                    .append(" | ").append(description)
                    .append(" | ").append(required)
                    .append(" |\n");
        }

        return tableBuilder.toString();
    }

    private String getPropertyTypeName(String type, SpecSchema itemSchema) {
        if (type == null) {
            type = "object";
        }

        var name = resourceBundle.getString("schema-property-type." + type.toLowerCase());

        if (type.equals("array")) {
            name = name.formatted(itemSchema.name());
        }

        return name;
    }
}
