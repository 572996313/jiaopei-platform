package com.cornerstone.module.organization.dto;

import java.util.List;

public record OrganizationSchemaView(
    List<String> tables,
    List<String> aggregates,
    String migration
) {
}
