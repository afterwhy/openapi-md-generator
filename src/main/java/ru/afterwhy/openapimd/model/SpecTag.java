package ru.afterwhy.openapimd.model;

import java.util.List;

public record SpecTag(String name, String description, List<SpecOperation> operations) {
}
