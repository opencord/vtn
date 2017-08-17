/*
 * Copyright 2016-present Open Networking Foundation
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
 * Entity that defines possible init state of the cordvtn node.
 */
public enum CordVtnNodeState {

    INIT {
        @Override
        public void process(CordVtnNodeHandler handler, CordVtnNode node) {
            handler.processInitState(node);
        }

        @Override
        public CordVtnNodeState nextState() {
            return DEVICE_CREATED;
        }
    },
    DEVICE_CREATED {
        @Override
        public void process(CordVtnNodeHandler handler, CordVtnNode node) {
            handler.processDeviceCreatedState(node);
        }

        @Override
        public CordVtnNodeState nextState() {
            return PORT_CREATED;
        }
    },
    PORT_CREATED {
        @Override
        public void process(CordVtnNodeHandler handler, CordVtnNode node) {
            handler.processPortCreatedState(node);
        }

        @Override
        public CordVtnNodeState nextState() {
            return COMPLETE;
        }
    },
    COMPLETE {
        @Override
        public void process(CordVtnNodeHandler handler, CordVtnNode node) {
            handler.processCompleteState(node);
        }

        @Override
        public CordVtnNodeState nextState() {
            // last state
            return COMPLETE;
        }
    };

    /**
     * Processes the current node state to proceed to the next state.
     *
     * @param handler cordvtn node state handler
     * @param node    cordvtn node
     */
    public abstract void process(CordVtnNodeHandler handler, CordVtnNode node);

    /**
     * Returns the next node state.
     * @return next node state
     */
    public abstract CordVtnNodeState nextState();
}
