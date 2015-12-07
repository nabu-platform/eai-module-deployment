package be.nabu.eai.module.deployment.build;

import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.artifacts.jaxb.JAXBArtifact;
import be.nabu.libs.resources.api.ResourceContainer;

public class BuildArtifact extends JAXBArtifact<BuildConfiguration> {

	private Repository repository;

	public BuildArtifact(String id, ResourceContainer<?> directory, Repository repository) {
		super(id, directory, "build.xml", BuildConfiguration.class);
		this.repository = repository;
	}

	public Repository getRepository() {
		return repository;
	}

}
