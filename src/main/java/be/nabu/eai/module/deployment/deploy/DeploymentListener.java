package be.nabu.eai.module.deployment.deploy;

import be.nabu.eai.server.Server;
import be.nabu.eai.server.api.ServerListener;
import be.nabu.libs.http.api.HTTPRequest;
import be.nabu.libs.http.api.server.HTTPServer;
import be.nabu.libs.http.server.rest.RESTHandler;

public class DeploymentListener implements ServerListener {

	@Override
	public void listen(Server server, HTTPServer httpServer) {
		httpServer.getDispatcher().subscribe(HTTPRequest.class, new RESTHandler("/", DeploymentREST.class, null, server));
	}

}
