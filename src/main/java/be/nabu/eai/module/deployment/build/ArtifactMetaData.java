package be.nabu.eai.module.deployment.build;

import java.util.Date;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import be.nabu.eai.repository.api.ArtifactManager;
import be.nabu.eai.repository.util.ClassAdapter;

@SuppressWarnings("rawtypes")
public class ArtifactMetaData {
	private long version;
	private String id, environmentId;
	private Date lastModified;
	private Class<? extends ArtifactManager> artifactManagerClass;
	
	public ArtifactMetaData() {
		// auto construct
	}
	public ArtifactMetaData(String id, String environmentId, long version, Date lastModified, Class<? extends ArtifactManager> artifactManagerClass) {
		this.id = id;
		this.environmentId = environmentId;
		this.version = version;
		this.lastModified = lastModified;
		this.artifactManagerClass = artifactManagerClass;
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public long getVersion() {
		return version;
	}
	public void setVersion(long version) {
		this.version = version;
	}
	public String getEnvironmentId() {
		return environmentId;
	}
	public void setEnvironmentId(String environmentId) {
		this.environmentId = environmentId;
	}
	public Date getLastModified() {
		return lastModified;
	}
	public void setLastModified(Date lastModified) {
		this.lastModified = lastModified;
	}
	@XmlAttribute
	@XmlJavaTypeAdapter(ClassAdapter.class)
	public Class<? extends ArtifactManager> getArtifactManagerClass() {
		return artifactManagerClass;
	}
	public void getArtifactManagerClass(Class<? extends ArtifactManager> artifactManagerClass) {
		this.artifactManagerClass = artifactManagerClass;
	}
}