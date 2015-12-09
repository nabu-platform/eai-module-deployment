package be.nabu.eai.module.deployment.menu;

import java.util.ArrayList;
import java.util.List;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.api.ArtifactGUIManager;
import be.nabu.eai.developer.api.ArtifactMerger;
import be.nabu.eai.developer.api.EntryContextMenuProvider;
import be.nabu.eai.developer.managers.base.BasePropertyOnlyGUIManager;
import be.nabu.eai.developer.managers.base.JAXBArtifactMerger;
import be.nabu.eai.developer.util.Confirm;
import be.nabu.eai.developer.util.Confirm.ConfirmType;
import be.nabu.eai.module.cluster.ClusterArtifact;
import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.api.ArtifactManager;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ExtensibleEntry;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.api.ResourceRepository;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.artifacts.api.Artifact;
import be.nabu.libs.validator.api.Validation;

public class DeployContextMenu implements EntryContextMenuProvider {
	
	private static Logger logger = LoggerFactory.getLogger(DeployContextMenu.class);

	@Override
	public MenuItem getContext(Entry entry) {
		if (entry instanceof ResourceEntry && entry.isNode()) {
			Menu menu = new Menu("Deploy");
			for (final ClusterArtifact cluster : entry.getRepository().getArtifacts(ClusterArtifact.class)) {
				MenuItem item = new MenuItem(cluster.getId());
				final ResourceRepository repository = cluster.getClusterRepository();
				if (repository != null) {
					item.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
						@SuppressWarnings({ "unchecked", "rawtypes" })
						@Override
						public void handle(ActionEvent arg0) {
							ArtifactMerger merger = getMerger(entry);
							try {
								ArtifactManager artifactManager = entry.getNode().getArtifactManager().newInstance();
								if (merger != null) {
									Entry remoteEntry = repository.getEntry(entry.getId());
									AnchorPane anchorPane = new AnchorPane();
									// we need a cloned copy of the source as the merger will modify it
									List<Validation<?>> messages = new ArrayList<Validation<?>>();
									Artifact clone = artifactManager.load((ResourceEntry) entry, messages);
									// if we need to merge, visualize it
									if (merger.merge(clone, remoteEntry.getNode().getArtifact(), anchorPane, repository)) {
										HBox box = new HBox();
										final Button button = new Button("Deploy");
										button.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
											@Override
											public void handle(ActionEvent arg0) {
												// don't want to trigger it twice...
												button.setDisable(true);
												if (!deploy(clone, artifactManager, repository)) {
													Confirm.confirm(ConfirmType.ERROR, "Deployment", "Deployment of " + clone.getId() + " to " + cluster.getId() + " failed", null);
													button.setDisable(false);
												}
												else {
													Confirm.confirm(ConfirmType.INFORMATION, "Deployment", "Deployment of " + clone.getId() + " to " + cluster.getId() + " succeeded", null);
												}
											}
										});
										box.getChildren().add(button);
										anchorPane.getChildren().add(box);
										Tab tab = MainController.getInstance().newTab("Merge: " + entry.getId() + " (" + cluster.getId() + ")");
										tab.setContent(anchorPane);
									}
									// else just deploy
									else if (!deploy(clone, artifactManager, repository)) {
										Confirm.confirm(ConfirmType.ERROR, "Deployment", "Deployment of " + clone.getId() + " to " + cluster.getId() + " failed", null);
									}
									else {
										Confirm.confirm(ConfirmType.INFORMATION, "Deployment", "Deployment of " + clone.getId() + " to " + cluster.getId() + " succeeded", null);
									}
								}
								else if (!deploy(entry.getNode().getArtifact(), artifactManager, repository)) {
									Confirm.confirm(ConfirmType.ERROR, "Deployment", "Deployment of " + entry.getId() + " to " + cluster.getId() + " failed", null);
								}
								else {
									Confirm.confirm(ConfirmType.INFORMATION, "Deployment", "Deployment of " + entry.getId() + " to " + cluster.getId() + " succeeded", null);
								}
							}
							catch (Exception e) {
								logger.error("Could not compare " + entry.getId(), e);
							}
						}
					});
					menu.getItems().add(item);
				}
			}
			if (!menu.getItems().isEmpty()) {
				return menu;
			}
		}
		return null;
	}
	
	public <T extends Artifact> boolean deploy(T artifact, ArtifactManager<T> artifactManager, ResourceRepository targetRepository) {
		try {
			// get the parent directory
			Entry parent = artifact.getId().contains(".") 
				? EAIRepositoryUtils.getDirectoryEntry(targetRepository, artifact.getId().replaceAll("\\.[^.]+$", ""), true)
				: targetRepository.getRoot();
			
			if (!(parent instanceof ExtensibleEntry)) {
				return false;
			}
			String name = artifact.getId().contains(".") ? artifact.getId().replaceAll("^.*[\\.]+([^.]+)$", "$1") : artifact.getId();
				
			if (parent.getChild(name) != null) {
				((ExtensibleEntry) parent).deleteChild(name, false);
			}
			RepositoryEntry entry = ((ExtensibleEntry) parent).createNode(name, artifactManager);
			artifactManager.save(entry, artifact);
		}
		catch (Exception e) {
			logger.error("Could not deploy " + artifact.getId(), e);
			return false;
		}
		return true;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static ArtifactMerger<?> getMerger(Entry entry) {
		List<Class<ArtifactMerger>> mergers = entry.getRepository().getImplementationsFor(ArtifactMerger.class);
		for (Class<ArtifactMerger> merger : mergers) {
			try {
				ArtifactMerger instance = merger.newInstance();
				if (instance.getArtifactClass().isAssignableFrom(entry.getNode().getArtifactClass())) {
					return instance;
				}
			}
			catch (Exception e) {
				logger.error("Could not load differ: " + merger, e);
			}
		}
		ArtifactGUIManager<?> guiManager = MainController.getInstance().getGUIManager(entry.getNode().getArtifactClass());
		// if we have a property-based GUI Manager, show the default differ
		if (guiManager != null && BasePropertyOnlyGUIManager.class.isAssignableFrom(guiManager.getClass())) {
			return new JAXBArtifactMerger((BasePropertyOnlyGUIManager) guiManager);
		}
		return null;
	}
}
