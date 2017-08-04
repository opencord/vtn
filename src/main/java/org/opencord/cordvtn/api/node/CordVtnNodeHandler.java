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
package org.opencord.cordvtn.api.node;

/**
 * Service providing cordvtn node bootstrapping.
 */
public interface CordVtnNodeHandler {

    /**
     * Processes the init state node.
     *
     * @param node cordvtn node
     */
    void processInitState(CordVtnNode node);

    /**
     * Processes the device created state node.
     *
     * @param node cordvtn node
     */
    void processDeviceCreatedState(CordVtnNode node);

    /**
     * Processes the port created state node.
     *
     * @param node cordvtn node
     */
    void processPortCreatedState(CordVtnNode node);

    /**
     * Processes the complete state node.
     *
     * @param node cordvtn node
     */
    void processCompleteState(CordVtnNode node);
}
