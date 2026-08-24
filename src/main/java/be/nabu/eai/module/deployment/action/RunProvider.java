package be.nabu.eai.module.deployment.action;

import java.util.Date;

import javax.jws.WebParam;
import javax.jws.WebResult;

public interface RunProvider {
	
	public enum RunState {
		SUCCEEDED,
		FAILED
	}
	
	public interface RunResult {
		public Date getStarted();
		public Date getStopped();
		public Date getReleased();
		public String getDeploymentActionId();
		public String getHash();
		public RunState getState();
		public String getLog();
	}
	
	@WebResult(name = "runResult")
	public RunResult findRun(
			@WebParam(name = "deploymentActionId") String deploymentActionId, 
			@WebParam(name = "hash") String hash);
}
