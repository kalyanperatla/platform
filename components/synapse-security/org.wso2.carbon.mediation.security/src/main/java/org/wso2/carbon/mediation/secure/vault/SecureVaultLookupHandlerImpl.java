/*
 *  Copyright (c) 2005-2008, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package org.wso2.carbon.mediation.secure.vault;

import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.base.api.ServerConfigurationService;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.service.RegistryService;
import org.wso2.carbon.registry.core.session.UserRegistry;


public class SecureVaultLookupHandlerImpl implements SecureVaultLookupHandler {


	private static Log log = LogFactory.getLog(SecureVaultLookupHandlerImpl.class);
	
	public enum LookupType {
		FILE, REGISTRY
	}

	private static SecureVaultLookupHandlerImpl instance = null;

	private ServerConfigurationService serverConfigService;

	private RegistryService registryService;

	UserRegistry registry = null;
	

	
	private SecureVaultLookupHandlerImpl(ServerConfigurationService serverConfigurationService,
			RegistryService registryService) throws RegistryException {
		this.serverConfigService = serverConfigurationService;
		this.registryService = registryService;
		try {
			init();
		} catch (RegistryException e) {
			// TODO Auto-generated catch block
			throw new RegistryException("Error while intializing the registry");
		}

	}

	public static SecureVaultLookupHandlerImpl getDefaultSecurityService() throws RegistryException {
		return getDefaultSecurityService(SecurityServiceHolder.getInstance()
				.getServerConfigurationService(), SecurityServiceHolder.getInstance()
				.getRegistryService());
	}

	private static SecureVaultLookupHandlerImpl getDefaultSecurityService(
			ServerConfigurationService serverConfigurationService,
			RegistryService registryService) throws RegistryException {
		if (instance == null) {
			instance = new SecureVaultLookupHandlerImpl(serverConfigurationService,
					registryService);
		}
		return instance;
	}

	private void init() throws RegistryException {
		try {
			registry = registryService.getConfigSystemRegistry();
			SecretManagerInitializer initializer = SecretManagerInitializer.getInstance();
			Resource resource = registry.get("connector-secure-vault-config");
			initializer.init(resource.getProperties(),resource);
			
			//creating vault-specific storage repository (this happens only if not resource not existing)
			org.wso2.carbon.registry.core.Collection secureVaultCollection= registry.newCollection();
			registry.put(SecureVaultConstants.SYSTEM_CONFIG_CONNECTOR_SECURE_VAULT_CONFIG, secureVaultCollection);
			
				} catch (RegistryException e) {
			throw new RegistryException("Error while intializing the registry");
		}
	}

	
	public String getProviderClass() {
		return this.getClass().getName();
	}

	@Override
	public void init(Properties arg0) {

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.wso2.carbon.mediation.secure.vault.MediationSecurity#evaluate(java
	 * .lang.String,
	 * org.wso2.carbon.mediation.secure.vault.MediationSrecurtyClient
	 * .LookupType)
	 */
	@Override
	public String evaluate(String aliasPasword, LookupType lookupType)
			throws RegistryException {
		if (lookupType.equals(LookupType.FILE)) {
			return lookupFileRepositry(aliasPasword);
		} else {
			return lookupRegistry(aliasPasword);
		}

	}

	/**
	 * Lookup in the register to retrieve value
	 * 
	 * @param aliasPasword
	 * @return
	 * @throws RegistryException
	 */
	private String lookupRegistry(String aliasPasword) throws RegistryException {
		if(log.isDebugEnabled()){
			log.info("processing evaluating registry based lookup");
		}
		SecretManager secretManager = SecretManager.getInstance();
		if (secretManager.isInitialized()) {
			return secretManager.getSecret(aliasPasword);
		}
		return null;
	}

	/**
	 * Lookup credentials via the FileRepository configuration
	 * 
	 * @param aliasPasword
	 * @return
	 */
	private String lookupFileRepositry(String aliasPasword) {
		if(log.isDebugEnabled()){
			log.info("processing evaluating file based lookup");
		}
		SecretManager secretManager = SecretManager.getInstance();
		if (secretManager.isInitialized()) {
			return secretManager.getSecret(aliasPasword);
		}
		return null;
	}

}
