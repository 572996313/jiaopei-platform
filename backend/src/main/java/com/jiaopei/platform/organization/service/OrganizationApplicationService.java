package com.jiaopei.platform.organization.service;

import com.jiaopei.platform.organization.dto.OrganizationBootstrapView;
import com.jiaopei.platform.organization.dto.OrganizationSchemaView;

public interface OrganizationApplicationService {

    OrganizationBootstrapView getBootstrapView();

    OrganizationSchemaView getSchemaView();
}
