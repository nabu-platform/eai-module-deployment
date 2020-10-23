package be.nabu.eai.module.deployment.action;

import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.managers.base.JAXBArtifactManager;
import be.nabu.libs.resources.api.ResourceContainer;

public class DeploymentActionManager extends JAXBArtifactManager<DeploymentActionConfiguration, DeploymentAction> {

	public DeploymentActionManager() {
		super(DeploymentAction.class);
	}

	@Override
	protected DeploymentAction newInstance(String id, ResourceContainer<?> container, Repository repository) {
		return new DeploymentAction(id, container, repository);
	}

}
