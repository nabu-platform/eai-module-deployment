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
