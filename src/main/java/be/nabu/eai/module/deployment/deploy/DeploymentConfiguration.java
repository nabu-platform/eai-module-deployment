package be.nabu.eai.module.deployment.deploy;

import java.net.URI;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import be.nabu.eai.module.cluster.ClusterArtifact;
import be.nabu.eai.module.deployment.build.BuildArtifact;
import be.nabu.eai.repository.jaxb.ArtifactXMLAdapter;

@XmlRootElement(name = "deployment")
public class DeploymentConfiguration {
	
	/**
	 * Where to store the builds
	 */
	private URI uri;
	private ClusterArtifact target;
	private BuildArtifact build;
	
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	public ClusterArtifact getTarget() {
		return target;
	}
	public void setTarget(ClusterArtifact target) {
		this.target = target;
	}
	
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	public BuildArtifact getBuild() {
		return build;
	}
	public void setBuild(BuildArtifact build) {
		this.build = build;
	}

	public URI getUri() {
		return uri;
	}
	public void setUri(URI uri) {
		this.uri = uri;
	}
	
}
