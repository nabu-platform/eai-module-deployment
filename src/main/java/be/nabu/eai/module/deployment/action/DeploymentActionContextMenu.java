package be.nabu.eai.module.deployment.action;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.api.EntryContextMenuProvider;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ResourceEntry;

public class DeploymentActionContextMenu implements EntryContextMenuProvider {

	@Override
	public MenuItem getContext(Entry entry) {
		if (entry.isNode() && DeploymentAction.class.isAssignableFrom(entry.getNode().getArtifactClass())) {
			Menu menu = new Menu("Deployment Action");
			
			MenuItem create = new MenuItem("Refresh Deployment Data");
			create.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent arg0) {
					try {
						DeploymentAction artifact = (DeploymentAction) entry.getNode().getArtifact();
						artifact.runSource();
					}
					catch (Exception e) {
						MainController.getInstance().notify(e);
					}
				}
			});
			
			menu.getItems().add(create);
			
			if (entry instanceof ResourceEntry && ((ResourceEntry) entry).getContainer().getChild("state.xml") != null) {
				MenuItem run = new MenuItem("Run Last Deployment Data");
				run.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent arg0) {
						try {
							DeploymentAction artifact = (DeploymentAction) entry.getNode().getArtifact();
							artifact.runTarget();
						}
						catch (Exception e) {
							MainController.getInstance().notify(e);
						}
					}
				});
				
				menu.getItems().add(run);
			}
			return menu;
		}
		return null;
	}
}
