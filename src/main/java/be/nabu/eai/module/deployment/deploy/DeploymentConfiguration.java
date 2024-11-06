/*
* Copyright (C) 2015 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

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
