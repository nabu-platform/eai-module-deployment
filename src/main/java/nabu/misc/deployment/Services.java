package nabu.misc.deployment;

import java.io.IOException;
import java.io.InputStream;

import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;

import be.nabu.eai.module.deployment.deploy.DeploymentInformation;
import be.nabu.eai.module.deployment.deploy.DeploymentREST;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.server.Server;
import be.nabu.libs.services.api.ServiceRunner;

@WebService
public class Services {
	
	@WebResult(name = "information")
	public DeploymentInformation deploy(@WebParam(name = "zip") InputStream zip) throws IOException {
		ServiceRunner serviceRunner = EAIResourceRepository.getInstance().getServiceRunner();
		if (serviceRunner instanceof Server) {
			return new DeploymentREST((Server) serviceRunner).deploy(zip);
		}
		else {
			throw new IllegalStateException("Can not find the server");
		}
	}
	
}
