package be.nabu.eai.module.deployment.deploy;

import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.artifacts.jaxb.JAXBArtifact;
import be.nabu.libs.resources.api.ResourceContainer;

public class DeploymentArtifact extends JAXBArtifact<DeploymentConfiguration> {

	public DeploymentArtifact(String id, ResourceContainer<?> directory, Repository repository) {
		super(id, directory, repository, "deployment.xml", DeploymentConfiguration.class);
	}
	
}
