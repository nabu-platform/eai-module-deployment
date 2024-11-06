/*
* Copyright (C) 2015 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package nabu.misc.deployment;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Properties;

import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.module.deployment.action.DeploymentAction;
import be.nabu.eai.module.deployment.deploy.DeploymentInformation;
import be.nabu.eai.module.deployment.deploy.DeploymentREST;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.server.Server;
import be.nabu.libs.events.api.EventDispatcher;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.services.api.ServiceRunner;
import be.nabu.utils.cep.api.EventSeverity;
import be.nabu.utils.cep.impl.CEPUtils;
import be.nabu.utils.cep.impl.ComplexEventImpl;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.security.DigestAlgorithm;
import be.nabu.utils.security.SecurityUtils;

@WebService
public class Services {

	private Logger logger = LoggerFactory.getLogger(getClass());
	
	@WebResult(name = "information")
	public DeploymentInformation deploy(@WebParam(name = "zip") InputStream zip) throws IOException {
		ServiceRunner serviceRunner = EAIResourceRepository.getInstance().getServiceRunner();
		if (serviceRunner instanceof Server) {
			return new DeploymentREST((Server) serviceRunner).deploy(zip);
		}
		else {
			throw new IllegalStateException("Can not find the server");
		}
	}
	
	// this will run the deployment actions that have not yet run on this server
	// if you enable force, they will always run
	public void runDeploymentActions(@WebParam(name = "force") Boolean force) {
		ServiceRunner serviceRunner = EAIResourceRepository.getInstance().getServiceRunner();
		if (force == null) {
			force = false;
		}
		// we do not run the deployment actions in development modus!
		if (serviceRunner instanceof Server && (force || !EAIResourceRepository.isDevelopment())) {
			Properties runtimeProperties = ((Server) serviceRunner).getRuntimeProperties();
			EventDispatcher dispatcher = EAIResourceRepository.getInstance().getComplexEventDispatcher();
			for (DeploymentAction action : EAIResourceRepository.getInstance().getArtifacts(DeploymentAction.class)) {
				ComplexEventImpl event = null;
				if (dispatcher != null) {
					event = new ComplexEventImpl();
					event.setEventCategory("deployment");
					event.setStarted(new Date());
					event.setCreated(event.getStarted());
					event.setArtifactId(action.getId());
				}
				try {
					Resource child = action.getDirectory().getChild("state.xml");
					// if we don't have a state, we can't run
					if (child == null) {
						continue;
					}
					ReadableContainer<ByteBuffer> readable = ((ReadableResource) child).getReadable();
					byte[] digest = SecurityUtils.digest(IOUtils.toInputStream(readable), DigestAlgorithm.SHA1);
					String hash = SecurityUtils.encodeDigest(digest);
					
					String lastSyncHash = runtimeProperties.getProperty(action.getId() + ":hash");
					// if we have a hash of what was last synced, we check the hash of the current state file, it if matches we don't run it
					if (lastSyncHash != null && hash.equals(lastSyncHash) && !force) {
						continue;
					}
					logger.info("Running deployment action: " + action.getId());
					action.runTarget();
					
					// set the hash
					runtimeProperties.setProperty(action.getId() + ":hash", hash);
					
					if (event != null) {
						event.setEventName("deployment-action-success");
						event.setSeverity(EventSeverity.INFO);
						event.setStopped(new Date());
						dispatcher.fire(event, this);
					}
				}
				catch (Exception e) {
					logger.error("Could not run deployment action: " + action.getId(), e);
					if (dispatcher != null) {
						CEPUtils.enrich(event, e);
						event.setEventName("deployment-action-failed");
						event.setSeverity(EventSeverity.ERROR);
						event.setStopped(new Date());
						dispatcher.fire(event, this);
					}
				}
			}
		}
	}
}
