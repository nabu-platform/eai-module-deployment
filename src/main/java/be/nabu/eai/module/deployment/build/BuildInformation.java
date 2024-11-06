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

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "buildInformation")
public class BuildInformation {
	private Date created = new Date();
	private int version, minorVersion;
	private String buildId, clusterId;
	private List<String> foldersToClean;
	private List<ArtifactMetaData> artifacts, references;
	private String environmentId;
	
	public BuildInformation() {
		// auto construct
	}
	
	public BuildInformation(int version, int minorVersion, String buildId, String clusterId, String environmentId) {
		this.version = version;
		this.minorVersion = minorVersion;
		this.buildId = buildId;
		this.clusterId = clusterId;
		this.environmentId = environmentId;
	}
	@XmlAttribute
	public Date getCreated() {
		return created;
	}
	public void setCreated(Date created) {
		this.created = created;
	}
	@XmlAttribute
	public int getVersion() {
		return version;
	}
	public void setVersion(int version) {
		this.version = version;
	}
	@XmlAttribute
	public int getMinorVersion() {
		return minorVersion;
	}
	public void setMinorVersion(int minorVersion) {
		this.minorVersion = minorVersion;
	}
	public List<String> getFoldersToClean() {
		return foldersToClean;
	}
	public void setFoldersToClean(List<String> foldersToClean) {
		this.foldersToClean = foldersToClean;
	}
	@XmlAttribute
	public String getBuildId() {
		return buildId;
	}
	public void setBuildId(String buildId) {
		this.buildId = buildId;
	}
	@XmlAttribute
	public String getClusterId() {
		return clusterId;
	}
	public void setClusterId(String clusterId) {
		this.clusterId = clusterId;
	}
	@XmlAttribute
	public String getEnvironmentId() {
		return environmentId;
	}
	public void setEnvironmentId(String environmentId) {
		this.environmentId = environmentId;
	}
	public List<ArtifactMetaData> getReferences() {
		return references;
	}
	public void setReferences(List<ArtifactMetaData> references) {
		this.references = references;
	}
	public List<ArtifactMetaData> getArtifacts() {
		return artifacts;
	}
	public void setArtifacts(List<ArtifactMetaData> artifacts) {
		this.artifacts = artifacts;
	}

	public void marshal(OutputStream output) {
		try {
			Marshaller marshaller = JAXBContext.newInstance(BuildInformation.class).createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
			marshaller.marshal(this, output);
		}
		catch(JAXBException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static BuildInformation unmarshal(InputStream input) {
		try {
			return (BuildInformation) JAXBContext.newInstance(BuildInformation.class).createUnmarshaller().unmarshal(input);
		}
		catch(JAXBException e) {
			throw new RuntimeException(e);
		}
	}
}