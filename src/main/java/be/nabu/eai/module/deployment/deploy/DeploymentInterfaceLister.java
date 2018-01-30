package be.nabu.eai.module.deployment.deploy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import be.nabu.eai.developer.api.InterfaceLister;
import be.nabu.eai.developer.util.InterfaceDescriptionImpl;

public class DeploymentInterfaceLister implements InterfaceLister {

	private static Collection<InterfaceDescription> descriptions = null;
	
	@Override
	public Collection<InterfaceDescription> getInterfaces() {
		if (descriptions == null) {
			synchronized(DeploymentInterfaceLister.class) {
				if (descriptions == null) {
					List<InterfaceDescription> descriptions = new ArrayList<InterfaceDescription>();
					descriptions.add(new InterfaceDescriptionImpl("Deployment", "Post Deployment Action", "be.nabu.eai.module.deployment.api.DeploymentAction.act"));
					DeploymentInterfaceLister.descriptions = descriptions;
				}
			}
		}
		return descriptions;
	}

}
