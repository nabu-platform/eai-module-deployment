package be.nabu.eai.module.deployment.deploy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import be.nabu.eai.module.cluster.RemoteRepository;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.api.ResourceRepository;
import be.nabu.eai.repository.artifacts.jaxb.JAXBArtifact;
import be.nabu.libs.resources.ResourceUtils;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.memory.MemoryDirectory;
import be.nabu.libs.resources.zip.ZIPArchive;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;

public class DeploymentArtifact extends JAXBArtifact<DeploymentConfiguration> {

	public DeploymentArtifact(String id, ResourceContainer<?> directory, Repository repository) {
		super(id, directory, repository, "deployment.xml", DeploymentConfiguration.class);
	}

	public ResourceContainer<?> getDeploymentContainer() {
		try {
			if (getConfig().getUri() != null) {
				return ResourceUtils.mkdir(getConfig().getUri(), null);
			}
			else {
				return ResourceUtils.mkdirs(getDirectory(), EAIResourceRepository.PRIVATE);
			}
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public List<String> getDeployments() {
		List<String> deployments = new ArrayList<String>();
		ResourceContainer<?> deploymentContainer = getDeploymentContainer();
		if (deploymentContainer != null) {
			for (Resource child : deploymentContainer) {
				if (child.getName().endsWith(".zip")) {
					deployments.add(child.getName().replace(".zip", ""));
				}
			}
			Collections.sort(deployments);
		}
		return deployments;
	}
	
	public List<String> getDeploymentResults() {
		List<String> deployments = new ArrayList<String>();
		ResourceContainer<?> deploymentContainer = getDeploymentContainer();
		if (deploymentContainer != null) {
			for (Resource child : deploymentContainer) {
				if (child.getName().endsWith(".xml")) {
					deployments.add(child.getName().replace(".xml", ""));
				}
			}
			Collections.sort(deployments);
		}
		return deployments;
	}
	
	public Resource getDeploymentArchive(String id) {
		ResourceContainer<?> deploymentContainer = getDeploymentContainer();
		if (deploymentContainer == null) {
			return null;
		}
		Resource child = deploymentContainer.getChild(id + ".zip");
		if (!(child instanceof ReadableResource)) {
			return null;
		}
		return child;
	}
	
	public ResourceRepository getDeployment(String id, ResourceRepository parent, boolean allowChainedLookup) throws IOException {
		return getAsRepository(parent == null ? (ResourceRepository) getRepository() : parent, allowChainedLookup, getDeploymentArchive(id));
	}

	public static ResourceRepository getAsRepository(ResourceRepository parent, boolean allowChainedLookup, Resource child) throws IOException {
		ResourceContainer<?> root = new MemoryDirectory();
		ResourceUtils.unzip(child, root);
		RemoteRepository remoteRepository = new RemoteRepository(parent, root);
		remoteRepository.setAllowLocalLookup(allowChainedLookup);
		remoteRepository.start();
		return remoteRepository;
	}
	
	public DeploymentInformation getDeploymentInformation(String id) throws IOException {
		ResourceContainer<?> deploymentContainer = getDeploymentContainer();
		if (deploymentContainer == null) {
			return null;
		}
		Resource child = deploymentContainer.getChild(id + ".zip");
		ZIPArchive archive = new ZIPArchive();
		try {
			archive.setSource(child);
			child = archive.getChild("deployment.xml");
			if (child instanceof ReadableResource) {
				ReadableContainer<ByteBuffer> readable = ((ReadableResource) child).getReadable();
				try {
					return DeploymentInformation.unmarshal(IOUtils.toInputStream(readable));
				}
				finally {
					readable.close();
				}
			}
			return null;
		}
		finally {
			archive.close();
		}
	}
	
	public DeploymentInformation getDeploymentResult(String id) throws IOException {
		ResourceContainer<?> deploymentContainer = getDeploymentContainer();
		if (deploymentContainer == null) {
			return null;
		}
		Resource child = deploymentContainer.getChild(id + ".xml");
		if (child instanceof ReadableResource) {
			ReadableContainer<ByteBuffer> readable = ((ReadableResource) child).getReadable();
			try {
				return DeploymentInformation.unmarshal(IOUtils.toInputStream(readable));
			}
			finally {
				readable.close();
			}
		}
		return null;
	}
}
