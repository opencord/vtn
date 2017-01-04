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
package org.opencord.cordvtn.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import java.io.IOException;

import static javax.ws.rs.core.Response.Status.Family.SUCCESSFUL;

/**
 * Implementation of a logging filter to warn any unsuccessful request.
 */
public class CordVtnWebLoggingFilter implements ContainerResponseFilter {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException {

        if (responseContext.getStatusInfo().getFamily() != SUCCESSFUL) {
            log.warn("Failed to handle request: {} {}, response: {}",
                     requestContext.getMethod(),
                     requestContext.getUriInfo().getAbsolutePath(),
                     responseContext.getEntity());
        }
    }
}