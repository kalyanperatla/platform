/*
 * Copyright 2004,2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.transport.adaptor.core;


/**
 * listener class to receive the events from the transport proxy
 */

public interface TransportAdaptorListener {


    /**
     * when an event definition is defined, transport calls this method with the definition.
     *
     * @param object - received event definition
     */
    void addEventDefinition(Object object);

    /**
     * when an event definition is removed transport proxy call this method with the definition.
     *
     * @param object - received event definition
     */
    void removeEventDefinition(Object object);

    /**
     * when an event happens transport proxy call this method with the received event.
     *
     * @param object - received event
     */
    void onEvent(Object object);
}
