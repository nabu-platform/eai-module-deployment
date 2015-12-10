package be.nabu.eai.module.deployment.deploy;

import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.managers.base.JAXBArtifactManager;
import be.nabu.libs.resources.api.ResourceContainer;

public class DeploymentArtifactManager extends JAXBArtifactManager<DeploymentConfiguration, DeploymentArtifact> {

	public DeploymentArtifactManager() {
		super(DeploymentArtifact.class);
	}

	@Override
	protected DeploymentArtifact newInstance(String id, ResourceContainer<?> container, Repository repository) {
		return new DeploymentArtifact(id, container, repository);
	}

}
