package be.nabu.eai.module.deployment.deploy;

import java.util.Date;

public class DeploymentResult {
	private Date started = new Date(), stopped;
	private String id, error;
	private DeploymentResultType type;
	public Date getStarted() {
		return started;
	}
	public void setStarted(Date started) {
		this.started = started;
	}
	public Date getStopped() {
		return stopped;
	}
	public void setStopped(Date stopped) {
		this.stopped = stopped;
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getError() {
		return error;
	}
	public void setError(String error) {
		this.error = error;
	}
	public DeploymentResultType getType() {
		return type;
	}
	public void setType(DeploymentResultType type) {
		this.type = type;
	}
	@Override
	public String toString() {
		return "[" + type + "] " + id + (error == null ? "" : " - " + error);
	}
}