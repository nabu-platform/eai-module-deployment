package be.nabu.eai.module.deployment.deploy;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlRootElement;

import be.nabu.eai.module.deployment.build.BuildInformation;

@XmlRootElement(name = "deploymentInformation")
public class DeploymentInformation {
	private String targetId, deploymentId;
	private BuildInformation build;
	private Date created, deployed;
	private List<String> merged, added, removed, updated, missing, unchanged;
	private List<DeploymentResult> results;
	public BuildInformation getBuild() {
		return build;
	}
	public String getTargetId() {
		return targetId;
	}
	public void setTargetId(String targetId) {
		this.targetId = targetId;
	}
	public String getDeploymentId() {
		return deploymentId;
	}
	public void setDeploymentId(String deploymentId) {
		this.deploymentId = deploymentId;
	}
	public void setBuild(BuildInformation build) {
		this.build = build;
	}
	public Date getCreated() {
		return created;
	}
	public void setCreated(Date created) {
		this.created = created;
	}
	public List<String> getMerged() {
		return merged;
	}
	public void setMerged(List<String> merged) {
		this.merged = merged;
	}
	public List<String> getAdded() {
		return added;
	}
	public void setAdded(List<String> added) {
		this.added = added;
	}
	public List<String> getRemoved() {
		return removed;
	}
	public void setRemoved(List<String> removed) {
		this.removed = removed;
	}
	public List<String> getUpdated() {
		return updated;
	}
	public void setUpdated(List<String> updated) {
		this.updated = updated;
	}
	public List<String> getMissing() {
		return missing;
	}
	public void setMissing(List<String> missing) {
		this.missing = missing;
	}
	public List<String> getUnchanged() {
		return unchanged;
	}
	public void setUnchanged(List<String> unchanged) {
		this.unchanged = unchanged;
	}
	public Date getDeployed() {
		return deployed;
	}
	public void setDeployed(Date deployed) {
		this.deployed = deployed;
	}
	public List<DeploymentResult> getResults() {
		return results;
	}
	public void setResults(List<DeploymentResult> results) {
		this.results = results;
	}
	public void marshal(OutputStream output) {
		try {
			Marshaller marshaller = JAXBContext.newInstance(DeploymentInformation.class).createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
			marshaller.marshal(this, output);
		}
		catch(JAXBException e) {
			throw new RuntimeException(e);
		}
	}
	public static DeploymentInformation unmarshal(InputStream input) {
		try {
			return (DeploymentInformation) JAXBContext.newInstance(DeploymentInformation.class).createUnmarshaller().unmarshal(input);
		}
		catch(JAXBException e) {
			throw new RuntimeException(e);
		}
	}
}