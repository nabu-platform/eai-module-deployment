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

package be.nabu.eai.module.deployment.build;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import be.nabu.eai.module.cluster.ClusterArtifact;
import be.nabu.eai.repository.jaxb.ArtifactXMLAdapter;

@XmlRootElement(name = "build")
@XmlType(propOrder = {"source", "version", "uri", "minorVersion", "artifacts", "foldersToClean"})
public class BuildConfiguration {
	/**
	 * Where to store the builds
	 */
	private URI uri;
	/**
	 * The source to build from
	 */
	private ClusterArtifact source;
	
	/**
	 * The artifacts to build (not using actual artifacts because we don't want reference management etc
	 */
	private List<String> artifacts;
	
	/**
	 * The folders we have to clean (so basically anything in these folders that is not in the list of artifacts should be deleted)
	 */
	private List<String> foldersToClean;
	
	/**
	 * The build version
	 */
	private Integer version, minorVersion;

	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	public ClusterArtifact getSource() {
		return source;
	}
	public void setSource(ClusterArtifact source) {
		this.source = source;
	}

	public List<String> getArtifacts() {
		if (artifacts == null) {
			artifacts = new ArrayList<String>();
		}
		return artifacts;
	}
	public void setArtifacts(List<String> artifacts) {
		this.artifacts = artifacts;
	}

	public Integer getVersion() {
		return version;
	}
	public void setVersion(Integer version) {
		this.version = version;
	}

	public Integer getMinorVersion() {
		return minorVersion;
	}
	public void setMinorVersion(Integer minorVersion) {
		this.minorVersion = minorVersion;
	}
	public List<String> getFoldersToClean() {
		if (foldersToClean == null) {
			foldersToClean = new ArrayList<String>();
		}
		return foldersToClean;
	}
	public void setFoldersToClean(List<String> foldersToClean) {
		this.foldersToClean = foldersToClean;
	}
	public URI getUri() {
		return uri;
	}
	public void setUri(URI uri) {
		this.uri = uri;
	}
}
