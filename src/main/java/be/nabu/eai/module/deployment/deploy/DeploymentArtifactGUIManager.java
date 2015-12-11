package be.nabu.eai.module.deployment.deploy;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.event.EventHandler;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TitledPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.api.ArtifactMerger;
import be.nabu.eai.developer.managers.base.BaseArtifactGUIInstance;
import be.nabu.eai.developer.managers.base.BaseGUIManager;
import be.nabu.eai.developer.managers.util.SimpleProperty;
import be.nabu.eai.module.cluster.ClusterArtifact;
import be.nabu.eai.module.deployment.build.BuildArtifact;
import be.nabu.eai.module.deployment.build.BuildArtifactGUIManager.ArtifactMetaData;
import be.nabu.eai.module.deployment.build.BuildArtifactGUIManager.BuildInformation;
import be.nabu.eai.module.deployment.menu.DeployContextMenu;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.Node;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.api.ResourceRepository;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;

public class DeploymentArtifactGUIManager extends BaseGUIManager<DeploymentArtifact, BaseArtifactGUIInstance<DeploymentArtifact>> {

	private Logger logger = LoggerFactory.getLogger(getClass());
	private ResourceRepository mergedRepository;
	
	public DeploymentArtifactGUIManager() {
		super("Deployment", DeploymentArtifact.class, new DeploymentArtifactManager());
	}

	@Override
	protected List<Property<?>> getCreateProperties() {
		return Arrays.asList(
			new SimpleProperty<BuildArtifact>("Build", BuildArtifact.class, true),
			new SimpleProperty<ClusterArtifact>("Target", ClusterArtifact.class, true)
		);
	}

	@Override
	protected BaseArtifactGUIInstance<DeploymentArtifact> newGUIInstance(Entry entry) {
		return new BaseArtifactGUIInstance<DeploymentArtifact>(this, getArtifactManager(), entry);
	}

	@Override
	protected void setEntry(BaseArtifactGUIInstance<DeploymentArtifact> guiInstance, ResourceEntry entry) {
		guiInstance.setEntry(entry);
	}
	
	@Override
	protected void setInstance(BaseArtifactGUIInstance<DeploymentArtifact> guiInstance, DeploymentArtifact instance) {
		guiInstance.setArtifact(instance);
	}

	@Override
	protected DeploymentArtifact newInstance(MainController controller, RepositoryEntry entry, Value<?>...values) throws IOException {
		ClusterArtifact target = null;
		if (values != null) {
			for (Value<?> value : values) {
				if ("Target".equals(value.getProperty().getName())) {
					target = (ClusterArtifact) value.getValue();
				}
			}
		}
		BuildArtifact build = null;
		if (values != null) {
			for (Value<?> value : values) {
				if ("Build".equals(value.getProperty().getName())) {
					build = (BuildArtifact) value.getValue();
				}
			}
		}
		if (target == null || build == null) {
			throw new IllegalArgumentException("Need to specify a target cluster and a build");
		}
		DeploymentArtifact artifact = new DeploymentArtifact(entry.getId(), entry.getContainer(), entry.getRepository());
		artifact.getConfiguration().setBuild(build);
		artifact.getConfiguration().setTarget(target);
		artifact.save(entry.getContainer());
		return artifact;
	}

	@Override
	protected DeploymentArtifact display(MainController controller, AnchorPane pane, Entry entry) throws IOException, ParseException {
		ScrollPane scroll = new ScrollPane();
		AnchorPane.setBottomAnchor(scroll, 0d);
		AnchorPane.setTopAnchor(scroll, 0d);
		AnchorPane.setLeftAnchor(scroll, 0d);
		AnchorPane.setRightAnchor(scroll, 0d);
		AnchorPane scrollRoot = new AnchorPane();
		scrollRoot.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
		scroll.setContent(scrollRoot);
		scrollRoot.prefWidthProperty().bind(scroll.widthProperty());
		
		final DeploymentArtifact artifact = (DeploymentArtifact) entry.getNode().getArtifact();
		
		final VBox vbox = new VBox();
		final TabPane tabs = new TabPane();
		final HBox deployButtons = new HBox();
		final ComboBox<String> deploys = new ComboBox<String>();
		Button deploy = new Button("Deploy");
		deployButtons.getChildren().addAll(new Label("Deployments: "), deploys, deploy);
		
		final ComboBox<String> builds = new ComboBox<String>();
		builds.getItems().addAll(artifact.getConfiguration().getBuild().getBuilds());
		
		final Button refreshBuilds = new Button("Refresh");
		refreshBuilds.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent arg0) {
				builds.getItems().clear();
				try {
					builds.getItems().addAll(artifact.getConfiguration().getBuild().getBuilds());
				}
				catch(Exception e) {
					logger.error("Could not load builds", e);
				}
			}
		});
		
		final ListView<PendingMerge> pendingMerges = new ListView<PendingMerge>();
		pendingMerges.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<PendingMerge>() {
			@Override
			public void changed(ObservableValue<? extends PendingMerge> arg0, PendingMerge arg1, PendingMerge arg2) {
				if (arg2 != null) {
					boolean alreadyOpen = false;
					for (Tab tab : tabs.getTabs()) {
						if (tab.getId().equals(arg2.getId())) {
							alreadyOpen = true;
							break;
						}
					}
					if (!alreadyOpen) {
						Tab tab = new Tab(arg2.getId());
						tab.setClosable(false);
						tab.setId(arg2.getId());
						tab.setContent(arg2.getPane());
						tabs.getTabs().add(tab);
					}
				}
			}
		});
		final Button prepare = new Button("Prepare");
		final Button createDeployment = new Button("Create");
		final Button cancelDeployment = new Button("Cancel");
		prepare.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
			@SuppressWarnings({ "rawtypes", "unchecked" })
			@Override
			public void handle(MouseEvent arg0) {
				try {
					BuildInformation buildInformation = artifact.getConfiguration().getBuild().getBuildInformation(builds.getSelectionModel().getSelectedItem());
					mergedRepository = artifact.getConfiguration().getBuild().getBuild(builds.getSelectionModel().getSelectedItem(), artifact.getConfiguration().getTarget().getClusterRepository(), true);
					if (buildInformation.getArtifacts() != null) {
						for (ArtifactMetaData artifactMeta : buildInformation.getArtifacts()) {
							Entry sourceEntry = mergedRepository.getEntry(artifactMeta.getId());
							if (sourceEntry != null) {
								ArtifactMerger merger = DeployContextMenu.getMerger(sourceEntry);
								if (merger != null) {
									Entry targetEntry = artifact.getConfiguration().getTarget().getClusterRepository().getEntry(artifactMeta.getId());
									AnchorPane anchorPane = new AnchorPane();
									if (merger.merge(sourceEntry.getNode().getArtifact(), targetEntry == null || !targetEntry.isNode() ? null : targetEntry.getNode().getArtifact(), anchorPane, mergedRepository)) {
										pendingMerges.getItems().add(new PendingMerge(artifactMeta.getId(), anchorPane));
									}
								}
							}
						}
					}
					builds.setDisable(true);
					refreshBuilds.setDisable(true);
					prepare.setDisable(true);
					createDeployment.setDisable(false);
					cancelDeployment.setDisable(false);
				}
				catch (Exception e) {
					logger.error("Could not create merged repository", e);
				}
			}
		});
		prepare.setDisable(true);
		createDeployment.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent arg0) {
				// TODO: create deployment
				builds.setDisable(false);
				refreshBuilds.setDisable(false);
				pendingMerges.getItems().clear();
			}
		});
		createDeployment.setDisable(true);
		cancelDeployment.setDisable(true);
		cancelDeployment.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent arg0) {
				builds.setDisable(false);
				refreshBuilds.setDisable(false);
				mergedRepository = null;
				pendingMerges.getItems().clear();
			}
		});
		
		HBox buildBox = new HBox();
		buildBox.getChildren().addAll(new Label("Builds: "), builds, refreshBuilds, prepare, createDeployment, cancelDeployment);
		
		final ListView<String> unchanged = new ListView<String>();
		final ListView<String> updated = new ListView<String>();
		final ListView<String> removed = new ListView<String>();
		final ListView<String> added = new ListView<String>();
		final ListView<String> missing = new ListView<String>();
		
		builds.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
			@Override
			public void changed(ObservableValue<? extends String> arg0, String arg1, String arg2) {
				unchanged.getItems().clear();
				updated.getItems().clear();
				removed.getItems().clear();
				added.getItems().clear();
				missing.getItems().clear();
				pendingMerges.getItems().clear();
				mergedRepository = null;
				cancelDeployment.setDisable(true);
				createDeployment.setDisable(true);
				
				// if nothing is selected, clear panes
				if (arg2 == null) {
					prepare.setDisable(true);
				}
				else {
					try {
						prepare.setDisable(false);
						
						ResourceRepository target = artifact.getConfiguration().getTarget().getClusterRepository();
						BuildInformation buildInformation = artifact.getConfiguration().getBuild().getBuildInformation(arg2);
						List<String> artifactIds = new ArrayList<String>();
						if (buildInformation != null) {
							if (buildInformation.getArtifacts() != null) {
								for (ArtifactMetaData artifactBuild : buildInformation.getArtifacts()) {
									artifactIds.add(artifactBuild.getId());
									Node node = target.getNode(artifactBuild.getId());
									if (node == null) {
										added.getItems().add(artifactBuild.getId());
									}
									else {
										boolean isUnchanged = isSame(artifactBuild.getEnvironmentId(), node.getEnvironmentId());
										isUnchanged &= isSame(artifactBuild.getLastModified(), node.getLastModified());
										isUnchanged &= isSame(Long.valueOf(artifactBuild.getVersion()), Long.valueOf(node.getVersion()));
										if (isUnchanged) {
											unchanged.getItems().add(artifactBuild.getId());
										}
										else {
											updated.getItems().add(artifactBuild.getId());
										}
									}
								}
							}
							if (buildInformation.getFoldersToClean() != null) {
								for (String folderToClean : buildInformation.getFoldersToClean()) {
									Entry folder = target.getEntry(folderToClean);
									if (folder != null) {
										for (Entry entry : folder) {
											// if the folder is set to be cleaned and it is not in the artifacts-to-be-deployed, it will be removed
											if (!artifactIds.contains(entry.getId())) {
												removed.getItems().add(entry.getId());
											}
										}
									}
								}
							}
							if (buildInformation.getReferences() != null) {
								for (ArtifactMetaData reference : buildInformation.getReferences()) {
									Entry referenceEntry = target.getEntry(reference.getId());
									if (referenceEntry == null || !referenceEntry.isNode()) {
										missing.getItems().add(reference.getId());
									}
								}
							}
						}
						// TODO: once you have a view on the deployment, we need to check all updated/added artifacts to see if something is mergeable
						// this will take a lot more overhead (have to load in repository, check artifacts etc) so should mandate a button action
						
//						ResourceRepository source = artifact.getConfiguration().getBuild().getBuild(arg2, artifact.getConfiguration().getTarget().getClusterRepository(), true);
					}
					catch (IOException e) {
						logger.error("Could not open build: " + arg2, e);
					}
				}
			}
		});
		
		Accordion accordion = new Accordion();
		final TitledPane unchangedPane = new TitledPane("Unchanged (0)", unchanged);
		unchanged.itemsProperty().get().addListener(new ListChangeListener<String>() {
			@Override
			public void onChanged(javafx.collections.ListChangeListener.Change<? extends String> arg0) {
				unchangedPane.setText("Unchanged (" + unchanged.getItems().size() + ")");				
			}
		});
		final TitledPane addedPane = new TitledPane("Added (0)", added);
		added.itemsProperty().get().addListener(new ListChangeListener<String>() {
			@Override
			public void onChanged(javafx.collections.ListChangeListener.Change<? extends String> arg0) {
				addedPane.setText("Added (" + added.getItems().size() + ")");				
			}
		});
		final TitledPane removedPane = new TitledPane("Removed (0)", removed);
		removed.itemsProperty().get().addListener(new ListChangeListener<String>() {
			@Override
			public void onChanged(javafx.collections.ListChangeListener.Change<? extends String> arg0) {
				removedPane.setText("Removed (" + removed.getItems().size() + ")");				
			}
		});
		final TitledPane updatedPane = new TitledPane("Updated (0)", updated);
		updated.itemsProperty().get().addListener(new ListChangeListener<String>() {
			@Override
			public void onChanged(javafx.collections.ListChangeListener.Change<? extends String> arg0) {
				updatedPane.setText("Updated (" + updated.getItems().size() + ")");				
			}
		});
		final TitledPane missingPane = new TitledPane("Missing (0)", missing);
		missing.itemsProperty().get().addListener(new ListChangeListener<String>() {
			@Override
			public void onChanged(javafx.collections.ListChangeListener.Change<? extends String> arg0) {
				missingPane.setText("Missing (" + missing.getItems().size() + ")");				
			}
		});
		final TitledPane pendingPane = new TitledPane("Pending Merge (0)", pendingMerges);
		pendingMerges.itemsProperty().get().addListener(new ListChangeListener<PendingMerge>() {
			@Override
			public void onChanged(javafx.collections.ListChangeListener.Change<? extends PendingMerge> arg0) {
				pendingPane.setText("Pending Merge (" + pendingMerges.getItems().size() + ")");				
			}
		});
		
		accordion.getPanes().addAll(unchangedPane, removedPane, addedPane, updatedPane, missingPane, pendingPane);
		vbox.getChildren().addAll(deployButtons, buildBox, accordion, tabs);
		AnchorPane.setLeftAnchor(vbox, 0d);
		AnchorPane.setRightAnchor(vbox, 0d);
		scrollRoot.getChildren().add(vbox);
		pane.getChildren().add(scroll);
		return artifact;
	}
	
	public static class PendingMerge {
		private AnchorPane pane;
		private boolean merged;
		private String id;

		public PendingMerge(String id, AnchorPane pane) {
			this.id = id;
			this.pane = pane;
		}

		public AnchorPane getPane() {
			return pane;
		}
		public void setPane(AnchorPane pane) {
			this.pane = pane;
		}

		public boolean isMerged() {
			return merged;
		}
		public void setMerged(boolean merged) {
			this.merged = merged;
		}

		public String getId() {
			return id;
		}
		public void setId(String id) {
			this.id = id;
		}
		@Override
		public String toString() {
			return id;
		}
	}
	
	private static boolean isSame(Object a, Object b) {
		return (a == null && b == null) || (a != null && a.equals(b));
	}
}
