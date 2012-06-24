/*
 * Copyright (c) 2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.carbon.registry.jcr.retention;

import javax.jcr.RepositoryException;
import javax.jcr.retention.Hold;


public class RegistryHold implements Hold {

    private String name = "";
    private boolean isDeep = false;

    public RegistryHold(String name, boolean isDeep) {
        this.name = name;
        this.isDeep = isDeep;
    }

    public boolean isDeep() throws RepositoryException {
        return isDeep;
    }

    public String getName() throws RepositoryException {
        return name;
    }
}
