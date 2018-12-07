// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.duper;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.applicationmodel.ServiceType;

public class ConfigServerHostApplication extends InfraApplication {
    public ConfigServerHostApplication() {
        super("configserver-host", NodeType.confighost,
                ClusterSpec.Type.container, ClusterSpec.Id.from("configserver-host"),
                ServiceType.HOST_ADMIN);
    }
}