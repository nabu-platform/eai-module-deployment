package be.nabu.eai.module.deployment.build;

import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import be.nabu.eai.module.cluster.ClusterArtifact;
import be.nabu.eai.repository.jaxb.ArtifactXMLAdapter;

@XmlRootElement(name = "build")
@XmlType(propOrder = {"source", "version", "minorVersion", "artifacts", "foldersToClean"})
public class BuildConfiguration {
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
		return foldersToClean;
	}
	public void setFoldersToClean(List<String> foldersToClean) {
		this.foldersToClean = foldersToClean;
	}
}
