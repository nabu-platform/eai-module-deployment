package be.nabu.eai.module.deployment.build;

import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.artifacts.jaxb.JAXBArtifact;
import be.nabu.libs.resources.api.ResourceContainer;

public class BuildArtifact extends JAXBArtifact<BuildConfiguration> {

	public BuildArtifact(String id, ResourceContainer<?> directory, Repository repository) {
		super(id, directory, repository, "build.xml", BuildConfiguration.class);
	}

}
