package be.nabu.eai.module.deployment.action;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import be.nabu.eai.repository.jaxb.ArtifactXMLAdapter;
import be.nabu.eai.repository.util.KeyValueMapAdapter;
import be.nabu.libs.services.api.DefinedService;

@XmlRootElement(name = "deploymentRunner")
public class DeploymentActionConfiguration {
	
	private DefinedService source, target;
	private Map<String, String> properties;
	
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	public DefinedService getSource() {
		return source;
	}
	public void setSource(DefinedService source) {
		this.source = source;
	}

	@NotNull
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	public DefinedService getTarget() {
		return target;
	}
	public void setTarget(DefinedService target) {
		this.target = target;
	}
	
	@XmlJavaTypeAdapter(value = KeyValueMapAdapter.class)
	public Map<String, String> getProperties() {
		if (properties == null) {
			properties = new LinkedHashMap<String, String>();
		}
		return properties;
	}
	public void setProperties(Map<String, String> properties) {
		this.properties = properties;
	}
}
