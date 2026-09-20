package com.cornerstone.module.organization.service;

import com.cornerstone.module.organization.dto.OrganizationBootstrapView;
import com.cornerstone.module.organization.dto.OrganizationSchemaView;

public interface OrganizationApplicationService {

    OrganizationBootstrapView getBootstrapView();

    OrganizationSchemaView getSchemaView();
}
