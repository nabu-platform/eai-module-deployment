package be.nabu.eai.module.deployment.deploy;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipInputStream;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.server.Server;
import be.nabu.libs.http.HTTPException;
import be.nabu.libs.resources.ResourceUtils;
import be.nabu.libs.resources.api.ManageableContainer;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.memory.MemoryDirectory;
import be.nabu.libs.resources.memory.MemoryResource;
import be.nabu.utils.aspects.AspectUtils;
import be.nabu.utils.io.IOUtils;

@Path("/")
public class DeploymentREST {
	
	@Context
	private Server server;
	
	private Logger logger = LoggerFactory.getLogger(getClass());
	
	@POST
	@Path("/deploy")
	public DeploymentInformation deploy(InputStream input) throws IOException {
		ZipInputStream zip = new ZipInputStream(input);
		MemoryDirectory directory = new MemoryDirectory();
		try {
			ResourceUtils.unzip(zip, directory);
		}
		finally {
			zip.close();
		}
		MemoryResource deploymentXml = directory.getChild("deployment.xml");
		if (deploymentXml == null) {
			throw new HTTPException(400, "Not a valid deployment zip");
		}
		
		DeploymentInformation deploymentInformation = DeploymentInformation.unmarshal(IOUtils.toInputStream(((ReadableResource) deploymentXml).getReadable()));
		String commonToReload = DeploymentUtils.getCommonToReload(deploymentInformation);
		
		ResourceContainer<?> container = ((ResourceEntry) server.getRepository().getRoot()).getContainer();
		if (server.isEnableSnapshots()) {
			server.snapshotRepository(commonToReload.replace('.', '/'));
			// get the non-managed container
			container = (ResourceContainer<?>) AspectUtils.aspects(container).get(0);
		}
		
		logger.info("Deploying: " + commonToReload);
		deploymentInformation.setResults(new ArrayList<DeploymentResult>());
		// delete any files as needed
		if (deploymentInformation.getBuild().getFoldersToClean() != null) {
			for (String folderToClean : deploymentInformation.getBuild().getFoldersToClean()) {
				logger.info("Cleaning " + folderToClean);
				DeploymentResult result = new DeploymentResult();
				try {
					result.setType(DeploymentResultType.FOLDER);
					result.setId(folderToClean);
					ResourceContainer<?> target = (ResourceContainer<?>) ResourceUtils.resolve(container, folderToClean.replace('.', '/'));
					if (target != null) {
						List<String> filesToRemove = new ArrayList<String>();
						for (Resource child : target) {
							if (child.getName().startsWith(".") || EAIResourceRepository.RESERVED.contains(child.getName())) {
								filesToRemove.add(child.getName());
							}
							else if (!(child instanceof ResourceContainer)) {
								filesToRemove.add(child.getName());
							}
						}
						for (String name : filesToRemove) {
							((ManageableContainer<?>) target).delete(name);
						}
						if (!target.iterator().hasNext() && target.getParent() != null) {
							((ManageableContainer<?>) target.getParent()).delete(target.getName());
						}
					}
					else {
						logger.error("Could not find resources for: " + folderToClean);
						result.setError("Could not find resources for: " + folderToClean);
					}
				}
				catch (IOException e) {
					logger.error("Could not delete files for: " + folderToClean, e);
					StringWriter writer = new StringWriter();
					PrintWriter printer = new PrintWriter(writer);
					e.printStackTrace(printer);
					printer.flush();
					result.setError(writer.toString());
				}
				result.setStopped(new Date());
				deploymentInformation.getResults().add(result);
			}
		}
		directory.delete("deployment.xml");
		DeploymentResult result = new DeploymentResult();
		result.setType(DeploymentResultType.ARTIFACT);
		result.setId(commonToReload);
		try {
			ResourceUtils.copy(directory, (ManageableContainer<?>) container, null, true, true);
			server.releaseRepository(commonToReload.replace('.', '/'));
		}
		catch (Exception e) {
			logger.error("Deployment failed", e);
			if (server.isEnableSnapshots()) {
				server.restoreRepository(commonToReload.replace('.', '/'));
			}
			StringWriter writer = new StringWriter();
			PrintWriter printer = new PrintWriter(writer);
			e.printStackTrace(printer);
			printer.flush();
			result.setError(writer.toString());
		}
		result.setStopped(new Date());
		server.getRepository().reload(commonToReload);
		deploymentInformation.getResults().add(result);
		return deploymentInformation;
	}
}
