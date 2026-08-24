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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.module.deployment.action.DeploymentAction;
import be.nabu.eai.module.deployment.action.RunProvider;
import be.nabu.eai.module.deployment.action.RunProvider.RunResult;
import be.nabu.eai.module.deployment.action.RunProvider.RunState;
import be.nabu.eai.module.deployment.deploy.DeploymentInformation;
import be.nabu.eai.module.deployment.deploy.DeploymentREST;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.util.SystemPrincipal;
import be.nabu.eai.server.Server;
import be.nabu.libs.events.api.EventDispatcher;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.TimestampedResource;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.api.ServiceRunner;
import be.nabu.libs.services.pojo.POJOUtils;
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
	
	public static boolean SLAVE_DEV = Boolean.parseBoolean(System.getProperty("dev.slave", "false"));
	
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
	public List<RunResult> runDeploymentActions(@WebParam(name = "force") Boolean force, @WebParam(name = "runProviderId") String serviceId) {
		ServiceRunner serviceRunner = EAIResourceRepository.getInstance().getServiceRunner();
		if (force == null) {
			if (SLAVE_DEV && EAIResourceRepository.isDevelopment()) {
				force = true;
			}
			else {
				force = false;
			}
		}
		List<RunResult> results = new ArrayList<RunResult>();
		RunProvider runProvider = null;
		Service service = serviceId == null ? null : (Service) EAIResourceRepository.getInstance().resolve(serviceId);
		if (service != null) {
			runProvider = POJOUtils.newProxy(RunProvider.class, EAIResourceRepository.getInstance(), SystemPrincipal.ROOT, service);
		}
		// we do not run the deployment actions in development modus!
		if (serviceRunner instanceof Server && (force || !EAIResourceRepository.isDevelopment())) {
			Properties runtimeProperties = ((Server) serviceRunner).getRuntimeProperties();
			EventDispatcher dispatcher = EAIResourceRepository.getInstance().getComplexEventDispatcher();
			for (DeploymentAction action : EAIResourceRepository.getInstance().getArtifacts(DeploymentAction.class)) {
				ComplexEventImpl event = dispatcher == null ? null : new ComplexEventImpl();
				if (event != null) {
					event.setEventCategory("deployment");
					event.setStarted(new Date());
					event.setCreated(event.getStarted());
					event.setArtifactId(action.getId());
				}
				Resource child = action.getDirectory().getChild("state.xml");
				String hash;
				// if we don't have a state, we can't run
				if (child == null) {
					continue;
				}
				try {
					ReadableContainer<ByteBuffer> readable = ((ReadableResource) child).getReadable();
					byte[] digest = SecurityUtils.digest(IOUtils.toInputStream(readable), DigestAlgorithm.SHA1);
					hash = SecurityUtils.encodeDigest(digest);
				}
				catch (Exception e) {
					logger.error("Can not compute hash for deployment action: " + action.getId(), e);
					continue;
				}
				try {
					// if we have already run this, skip it
					if (runProvider != null) {
						RunResult run = runProvider.findRun(action.getId(), hash);
						if (run != null) {
							continue;
						}
					}
					// if we dont have a run provider, we check for hashes
					else {
						String lastSyncHash = runtimeProperties.getProperty(action.getId() + ":hash");
						// if we have a hash of what was last synced, we check the hash of the current state file, it if matches we don't run it
						if (lastSyncHash != null && hash.equals(lastSyncHash) && !force) {
							continue;
						}
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
					
					results.add(new RunResult() {
						@Override
						public Date getStarted() {
							return event.getStarted();
						}
						@Override
						public Date getStopped() {
							return event.getStopped();
						}
						@Override
						public Date getReleased() {
							return child instanceof TimestampedResource ? ((TimestampedResource) child).getLastModified() : event.getStarted();
						}
						@Override
						public String getDeploymentActionId() {
							return action.getId();
						}
						@Override
						public String getHash() {
							return hash;
						}
						@Override
						public RunState getState() {
							return RunState.SUCCEEDED;
						}
						@Override
						public String getLog() {
							return null;
						}
					});
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
					StringWriter writer = new StringWriter();
					PrintWriter printer = new PrintWriter(writer);
					e.printStackTrace(printer);
					printer.flush();
					results.add(new RunResult() {
						@Override
						public Date getStarted() {
							return event.getStarted();
						}
						@Override
						public Date getStopped() {
							return event.getStopped();
						}
						@Override
						public Date getReleased() {
							return child instanceof TimestampedResource ? ((TimestampedResource) child).getLastModified() : event.getStarted();
						}
						@Override
						public String getDeploymentActionId() {
							return action.getId();
						}
						@Override
						public String getHash() {
							return hash;
						}
						@Override
						public RunState getState() {
							return RunState.FAILED;
						}
						@Override
						public String getLog() {
							return writer.toString();
						}
					});
				}
			}
		}
		return results;
	}
}
