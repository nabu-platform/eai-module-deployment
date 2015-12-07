package be.nabu.eai.module.deployment.build;

import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.managers.base.JAXBArtifactManager;
import be.nabu.libs.resources.api.ResourceContainer;

public class BuildArtifactManager extends JAXBArtifactManager<BuildConfiguration, BuildArtifact> {

	public BuildArtifactManager() {
		super(BuildArtifact.class);
	}

	@Override
	protected BuildArtifact newInstance(String id, ResourceContainer<?> container, Repository repository) {
		return new BuildArtifact(id, container, repository);
	}

}
