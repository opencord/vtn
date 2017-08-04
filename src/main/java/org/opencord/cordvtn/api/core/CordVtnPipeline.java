/*
 * Copyright 2017-present Open Networking Foundation
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
package org.opencord.cordvtn.api.core;

import org.onlab.packet.VlanId;
import org.onosproject.net.flow.FlowRule;
import org.opencord.cordvtn.api.node.CordVtnNode;

/**
 * Service providing cordvtn pipeline.
 */
public interface CordVtnPipeline {

    // tables
    int TABLE_ZERO = 0;
    int TABLE_IN_PORT = 1;
    int TABLE_ACCESS = 2;
    int TABLE_IN_SERVICE = 3;
    int TABLE_DST = 4;
    int TABLE_TUNNEL_IN = 5;
    int TABLE_VLAN = 6;

    // priorities
    int PRIORITY_MANAGEMENT = 55000;
    int PRIORITY_HIGH = 50000;
    int PRIORITY_DEFAULT = 5000;
    int PRIORITY_LOW = 4000;
    int PRIORITY_ZERO = 0;

    VlanId VLAN_WAN = VlanId.vlanId((short) 500);

    /**
     * Initializes the pipeline for the supplied node.
     *
     * @param node cordvtn node
     */
    void initPipeline(CordVtnNode node);

    /**
     * Clean up the pipeline for all nodes.
     */
    void cleanupPipeline();

    /**
     * Processes the given flow rule.
     *
     * @param install install or remove
     * @param rule    flow rule to process
     */
    void processFlowRule(boolean install, FlowRule rule);
}
