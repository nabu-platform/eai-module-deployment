package be.nabu.eai.module.deployment.deploy;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipInputStream;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.module.deployment.action.DeploymentAction;
import be.nabu.eai.module.deployment.build.ArtifactMetaData;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.server.Server;
import be.nabu.libs.artifacts.api.Artifact;
import be.nabu.libs.artifacts.api.DeployHookArtifact;
import be.nabu.libs.http.HTTPException;
import be.nabu.libs.resources.ResourceUtils;
import be.nabu.libs.resources.api.ManageableContainer;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.api.WritableResource;
import be.nabu.libs.resources.memory.MemoryDirectory;
import be.nabu.libs.resources.memory.MemoryResource;
import be.nabu.utils.aspects.AspectUtils;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.WritableContainer;

@Path("/")
public class DeploymentREST {
	
	@Context
	private Server server;
	
	private Logger logger = LoggerFactory.getLogger(getClass());
	
	public DeploymentREST() {
		// auto
	}
	public DeploymentREST(Server server) {
		this.server = server;
	}
	
	@POST
	@Path("/deploy")
	public DeploymentInformation deploy(InputStream input) throws IOException {
		boolean bringOnline = false;
		try {
			// after deployment, run any post deploy artifacts (they might need to prep things for the deployment actions to be done correctly, e.g. database ddl before we do dml with actions)
			List<DeployHookArtifact> deploymentHookArtifacts = server.getRepository().getArtifacts(DeployHookArtifact.class);
			for (DeployHookArtifact artifact : deploymentHookArtifacts) {
				logger.info("Running deployment hook artifact " + artifact.getId() + " [PRE]");
				artifact.preDeployment();
			}
						
			ZipInputStream zip = new ZipInputStream(input);
			MemoryDirectory directory = new MemoryDirectory();
			try {
				ResourceUtils.unzip(zip, directory);
			}
			finally {
				zip.close();
			}
			Date started = new Date();
			MemoryResource deploymentXml = directory.getChild("deployment.xml");
			if (deploymentXml == null) {
				throw new HTTPException(400, "Not a valid deployment zip");
			}
			
			DeploymentInformation deploymentInformation = DeploymentInformation.unmarshal(IOUtils.toInputStream(((ReadableResource) deploymentXml).getReadable()));
			String commonToReload = DeploymentUtils.getCommonToReload(deploymentInformation);
			
			ResourceContainer<?> container = ((ResourceEntry) server.getRepository().getRoot()).getContainer();
			if (server.isEnableSnapshots()) {
				server.snapshotRepository(commonToReload == null ? "/" : commonToReload.replace('.', '/'));
				// get the non-managed container
				container = (ResourceContainer<?>) AspectUtils.aspects(container).get(0);
			}
			// if you didn't bring the server offline yourself, we will do it for you to prevent errors while the reloading happens
			// if you don't snapshot the repository, we do it now, before we start the deployment
			else if (!server.isOffline()) {
				bringOnline = true;
				server.bringOffline();
			}
			
			logger.info("Deploying: " + commonToReload);
			deploymentInformation.setResults(new ArrayList<DeploymentResult>());
			// delete any files as needed
			if (deploymentInformation.getBuild().getFoldersToClean() != null) {
				Collections.sort(deploymentInformation.getBuild().getFoldersToClean(), new Comparator<String>() {
					@Override
					public int compare(String o1, String o2) {
						return o2.length() - o1.length();
					}
				});
				for (String folderToClean : deploymentInformation.getBuild().getFoldersToClean()) {
					logger.info("Cleaning " + folderToClean);
					DeploymentResult result = new DeploymentResult();
					try {
						result.setType(DeploymentResultType.FOLDER);
						result.setId(folderToClean);
						ResourceContainer<?> target = (ResourceContainer<?>) ResourceUtils.resolve(container, folderToClean.replace('.', '/'));
						// folders to clean are never at the artifact level, but at the actual folder level
						if (target != null && target.getParent() != null) {
							((ManageableContainer<?>) target.getParent()).delete(target.getName());
						}
						else {
							logger.warn("Could not find resources for: " + folderToClean);
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
				// if you did snapshot and the server is not offline, do it at this time, before we expose the new stuff
				if (!server.isOffline()) {
					bringOnline = true;
					server.bringOffline();
				}
				server.releaseRepository(commonToReload == null ? "/" : commonToReload.replace('.', '/'));
			}
			catch (Throwable e) {
				logger.error("Deployment failed", e);
				if (server.isEnableSnapshots()) {
					server.restoreRepository(commonToReload == null ? "/" : commonToReload.replace('.', '/'));
				}
				StringWriter writer = new StringWriter();
				PrintWriter printer = new PrintWriter(writer);
				e.printStackTrace(printer);
				printer.flush();
				result.setError(writer.toString());
			}
			result.setStopped(new Date());
			
			try {
				if (commonToReload == null) {
					server.getRepository().reloadAll();
				}
				else {
					server.getRepository().reload(commonToReload);
				}
				deploymentInformation.getResults().add(result);
			}
			catch (Throwable e) {
				logger.error("Reload after deployment failed, please restart the server for consistent results", e);
				StringWriter writer = new StringWriter();
				PrintWriter printer = new PrintWriter(writer);
				e.printStackTrace(printer);
				printer.flush();
				result.setError(writer.toString());
			}
			
			for (DeployHookArtifact artifact : deploymentHookArtifacts) {
				try {
					logger.info("Running deployment hook artifact " + artifact.getId() + " [DURING]");
					artifact.duringDeployment();
				}
				catch (Exception e) {
					logger.error("Failed to run post deployment artifact: " + artifact.getId(), e);
				}
			}
			
			logger.info("Checking for post deployment actions");
			// after deployment, run any deployment actions
			List<ArtifactMetaData> artifacts = deploymentInformation.getBuild().getArtifacts();
			for (ArtifactMetaData artifact : artifacts) {
				Artifact resolve = server.getRepository().resolve(artifact.getId());
				if (resolve instanceof DeploymentAction) {
					logger.info("Running deployment action: " + resolve.getId());
					DeploymentResult actionResult = new DeploymentResult();
					actionResult.setType(DeploymentResultType.ACTION);
					actionResult.setId(artifact.getId());
					try {
						((DeploymentAction) resolve).runTarget();
					}
					catch (Throwable e) {
						logger.error("Deployment action failed", e);
						StringWriter writer = new StringWriter();
						PrintWriter printer = new PrintWriter(writer);
						e.printStackTrace(printer);
						printer.flush();
						result.setError(writer.toString());
					}
					result.setStopped(new Date());
					deploymentInformation.getResults().add(actionResult);
				}
				else if (resolve == null) {
					logger.warn("Could not find artifact: " + artifact.getId() + ", it should have been included in the deployment");
				}
			}
			
			if (server.getDeployments() != null) {
				SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_SSS");
				String fileName = formatter.format(started) + "-" + formatter.format(new Date()) + ".xml";
				Resource create = ((ManageableContainer<?>) server.getDeployments()).create(fileName, "application/xml");
				WritableContainer<ByteBuffer> writable = ((WritableResource) create).getWritable();
				try {
					deploymentInformation.marshal(IOUtils.toOutputStream(writable));
				}
				finally {
					writable.close();
				}
			}
			for (DeployHookArtifact artifact : deploymentHookArtifacts) {
				logger.info("Running deployment hook artifact " + artifact.getId() + " [POST]");
				artifact.postDeployment();
			}
			
			logger.info("Deployment completed: " + deploymentInformation.getDeploymentId());
			if (bringOnline) {
				server.bringOnline();
			}
			return deploymentInformation;
		}
		catch (Throwable e) {
			logger.error("Deployment failed during unspecified action", e);
			throw new RuntimeException(e);
		}
	}
}
