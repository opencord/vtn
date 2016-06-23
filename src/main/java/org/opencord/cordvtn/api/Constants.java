/*
 * Copyright 2016-present Open Networking Laboratory
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
package org.opencord.cordvtn.api;

/**
 * Provides constants used in CORD VTN services.
 */
public final class Constants {

    private Constants() {
    }

    public static final String CORDVTN_APP_ID = "org.opencord.vtn";

    public static final String ERROR_XOS_ACCESS = "XOS access is not configured";
    public static final String ERROR_OPENSTACK_ACCESS = "OpenStack access is not configured";
    public static final String MSG_OK = "OK";
    public static final String MSG_NO = "NO";

    public static final String PORT_NAME = "portName";
    public static final String DEFAULT_TUNNEL = "vxlan";
    public static final String DEFAULT_BRIDGE = "br-int";
    public static final String VPORT_PREFIX = "tap";
}
