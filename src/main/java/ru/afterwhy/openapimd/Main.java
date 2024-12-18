package ru.afterwhy.openapimd;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;

public class Main {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java OpenApiToMarkdown <path-to-openapi-file>");
            return;
        }

        String openApiFilePath = args[0];
        var locale = Locale.of("ru-RU");
        var spec = new SpecParser().parse(openApiFilePath, locale);

        var mdRenderer = new MdRenderer(locale);
        try (FileWriter writer = new FileWriter("api-documentation-new.md")) {
            mdRenderer.render(spec, writer);
        } catch (IOException e) {
            System.out.println("Error writing to markdown file: " + e.getMessage());
        }

    }
}
