/*
 *  Copyright (c) 2005-2011, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package org.wso2.carbon.rssmanager.core.dao;

import org.wso2.carbon.rssmanager.core.dao.exception.RSSDAOException;
import org.wso2.carbon.rssmanager.core.entity.DatabasePrivilegeTemplate;

public interface DatabasePrivilegeTemplateDAO {

    void addDatabasePrivilegesTemplate(DatabasePrivilegeTemplate template,
                                       int tenantId) throws RSSDAOException;

    void removeDatabasePrivilegesTemplate(String templateName,
                                          int tenantId) throws RSSDAOException;

    void updateDatabasePrivilegesTemplate(DatabasePrivilegeTemplate template,
                                          int tenantId) throws RSSDAOException;

    DatabasePrivilegeTemplate getDatabasePrivilegesTemplate(String name,
                                                            int tenantId) throws RSSDAOException;

    DatabasePrivilegeTemplate[] getDatabasePrivilegesTemplates(
            int tenantId) throws RSSDAOException;

    void setPrivilegeTemplateProperties(DatabasePrivilegeTemplate template,
                                        int tenantId) throws RSSDAOException;

    void removeDatabasePrivilegesTemplateEntries(String templateName,
                                                 int tenantId) throws RSSDAOException;

    boolean isDatabasePrivilegeTemplateExist(String templateName,
                                             int tenantId) throws RSSDAOException;

}