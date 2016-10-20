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
package org.opencord.cordvtn.codec;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onosproject.codec.CodecService;
import org.opencord.cordvtn.api.net.ServiceNetwork;
import org.opencord.cordvtn.api.net.ServicePort;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Implementation of the JSON codec brokering service for VTN.
 */
@Component(immediate = true)
public class CodecRegistrator {
    private final Logger log = getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CodecService codecService;

    @Activate
    public void activate() {
        codecService.registerCodec(ServiceNetwork.class, new ServiceNetworkCodec());
        codecService.registerCodec(ServicePort.class, new ServicePortCodec());
        log.info("Started");
    }

    @Deactivate
    public void deactivate() {
        codecService.unregisterCodec(ServiceNetwork.class);
        codecService.unregisterCodec(ServicePort.class);
        log.info("Stopped");
    }
}
