package ru.afterwhy.openapimd.model;

public record ResponseBody(int httpCode, String mimeType, SpecSchema specSchema) {
}
