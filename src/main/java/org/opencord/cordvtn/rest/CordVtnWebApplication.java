/*
 * Copyright 2015-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opencord.cordvtn.rest;

import org.onlab.rest.AbstractWebApplication;

import java.util.Set;

/**
 * CORD VTN Web application.
 */
public class CordVtnWebApplication extends AbstractWebApplication {
    @Override
    public Set<Class<?>> getClasses() {
        return getClasses(ServiceNetworkWebResource.class,
                          ServicePortWebResource.class,
                          NeutronMl2NetworksWebResource.class,
                          NeutronMl2SubnetsWebResource.class,
                          NeutronMl2PortsWebResource.class,
                          CordVtnWebLoggingFilter.class);
    }
}
