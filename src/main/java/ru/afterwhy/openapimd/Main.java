package ru.afterwhy.openapimd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.FileWriter;
import java.util.Locale;

public class Main {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java OpenApiToMarkdown <path-to-openapi-file>");
            return;
        }

        var openApiFilePath = args[0];
        var locale = Locale.of("ru-RU");

        var objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        try (var writer = new FileWriter("api-documentation-new.md")) {
            var spec = new SpecParser().parse(openApiFilePath, locale);
            var mdRenderer = new MdRenderer(objectMapper, locale);
            mdRenderer.render(spec, writer);
        } catch (Exception e) {
            System.out.println("Error building markdown file: " + e.getMessage());
            throw new RuntimeException(e);
        }

    }
}
