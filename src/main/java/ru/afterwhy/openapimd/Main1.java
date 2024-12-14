package ru.afterwhy.openapimd;

import java.util.Locale;

public class Main1 {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java OpenApiToMarkdown <path-to-openapi-file>");
            return;
        }

        String openApiFilePath = args[0];
        var spec = new SpecParser().parse(openApiFilePath, Locale.of("ru-RU"));
        System.out.println(spec);
    }
}
