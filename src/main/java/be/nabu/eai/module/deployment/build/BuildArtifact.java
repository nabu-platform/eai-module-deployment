package be.nabu.eai.module.deployment.build;

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

public class BuildArtifact extends JAXBArtifact<BuildConfiguration> {

	public BuildArtifact(String id, ResourceContainer<?> directory, Repository repository) {
		super(id, directory, repository, "build.xml", BuildConfiguration.class);
	}
	
	public ResourceContainer<?> getBuildContainer() {
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

	public List<String> getBuilds() {
		List<String> builds = new ArrayList<String>();
		ResourceContainer<?> buildContainer = getBuildContainer();
		if (buildContainer != null) {
			for (Resource child : buildContainer) {
				builds.add(child.getName().replace(".zip", ""));
			}
		}
		Collections.sort(builds);
		return builds;
	}
	
	public ResourceRepository getBuild(String id, ResourceRepository parent, boolean allowChainedLookup) throws IOException {
		ResourceContainer<?> buildContainer = getBuildContainer();
		if (buildContainer == null) {
			return null;
		}
		Resource child = buildContainer.getChild(id + ".zip");
		if (!(child instanceof ReadableResource)) {
			return null;
		}
		ResourceContainer<?> root = new MemoryDirectory();
		ResourceUtils.unzip(child, root);
		RemoteRepository remoteRepository = new RemoteRepository(parent == null ? (ResourceRepository) getRepository() : parent, root);
		remoteRepository.setAllowLocalLookup(allowChainedLookup);
		remoteRepository.start();
		return remoteRepository;
	}
	
	public BuildInformation getBuildInformation(String id) throws IOException {
		ResourceContainer<?> buildContainer = getBuildContainer();
		if (buildContainer == null) {
			return null;
		}
		Resource child = buildContainer.getChild(id + ".zip");
		return getBuildInformation(child);
	}

	public static BuildInformation getBuildInformation(Resource child) throws IOException {
		ZIPArchive archive = new ZIPArchive();
		try {
			archive.setSource(child);
			child = archive.getChild("build.xml");
			if (child instanceof ReadableResource) {
				ReadableContainer<ByteBuffer> readable = ((ReadableResource) child).getReadable();
				try {
					return BuildInformation.unmarshal(IOUtils.toInputStream(readable));
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
}
