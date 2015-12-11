package be.nabu.eai.module.deployment.deploy;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlRootElement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
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
import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.DynamicEntry;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ExtensibleEntry;
import be.nabu.eai.repository.api.ModifiableNodeEntry;
import be.nabu.eai.repository.api.Node;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.api.ResourceRepository;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.artifacts.api.Artifact;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.resources.ResourceUtils;
import be.nabu.libs.resources.api.ManageableContainer;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.api.WritableResource;
import be.nabu.libs.validator.api.Validation;
import be.nabu.utils.io.IOUtils;

public class DeploymentArtifactGUIManager extends BaseGUIManager<DeploymentArtifact, BaseArtifactGUIInstance<DeploymentArtifact>> {

	private Logger logger = LoggerFactory.getLogger(getClass());
	private ObjectProperty<ResourceRepository> mergedRepository = new SimpleObjectProperty<ResourceRepository>();
	private BuildInformation buildInformation;
	private ListView<PendingMerge> pendingPossibleMerges = new ListView<PendingMerge>();
	private ListView<PendingMerge> pendingRequiredMerges = new ListView<PendingMerge>();
	
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
		return new BaseArtifactGUIInstance<DeploymentArtifact>(this, getArtifactManager(), entry) {
			@Override
			public List<Validation<?>> save() throws IOException {
				for (PendingMerge merge : pendingPossibleMerges.getItems()) {
					merge.save();
				}
				for (PendingMerge merge : pendingRequiredMerges.getItems()) {
					merge.save();
				}
				return super.save();
			}
		};
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

	@SuppressWarnings("unchecked")
	private void deploy(ResourceRepository source, ResourceRepository target, DeploymentInformation deploymentInformation) {
		for (ArtifactMetaData meta : deploymentInformation.getBuild().getArtifacts()) {
			DeploymentResult result = new DeploymentResult();
			result.setType(DeploymentResultType.ARTIFACT);
			result.setId(meta.getId());
			Entry entry = source.getEntry(meta.getId());
			if (!(entry instanceof ResourceEntry)) {
				result.setError("Could not find artifact in source repository");
			}
			else {
				Node resolved = entry.getNode();
				try {
					DeployContextMenu.deploy((ResourceEntry) entry, resolved.getArtifact(), resolved.getArtifactManager().newInstance(), target, meta.getEnvironmentId(), meta.getVersion(), meta.getLastModified());
				}
				catch (Exception e) {
					StringWriter writer = new StringWriter();
					PrintWriter printer = new PrintWriter(writer);
					e.printStackTrace(printer);
					printer.flush();
					result.setError(writer.toString());
				}
			}
			result.setStopped(new Date());
			deploymentInformation.getResults().add(result);
		}
	}
	
	private void reload(ClusterArtifact artifact, DeploymentInformation deploymentInformation) {
		List<String> foldersToReload = new ArrayList<String>();
		for (DeploymentResult succeeded : deploymentInformation.getResults()) {
			// only reload successful artifact deployments
			if (succeeded.getError() == null && DeploymentResultType.ARTIFACT.equals(succeeded.getType())) {
				String parent = succeeded.getId().contains(".") ? succeeded.getId().replaceAll("\\.[^.]+$", "") : null;
				// if it lives on the root, reload it specifically
				if (parent == null) {
					foldersToReload.add(succeeded.getId());
				}
				else if (!foldersToReload.contains(parent)) {
					foldersToReload.add(parent);
				}
			}
		}
		// it is hard to determine the correct order in which to load the deployed artifacts (there might be artifact repositories etc in there)
		// so currently we just find the longest common parent and reload that entirely
		String common = null;
		boolean rootRefresh = false;
		for (int i = foldersToReload.size() - 1; i >= 0; i--) {
			if (common == null) {
				common = foldersToReload.get(i);
			}
			else if (foldersToReload.get(i).equals(common) || foldersToReload.get(i).startsWith(common + ".")) {
				continue;
			}
			else if (common.startsWith(foldersToReload.get(i))) {
				common = foldersToReload.get(i);
			}
			// find common parent
			else {
				while (common.contains(".")) {
					common = common.replaceAll("\\.[^.]+$", "");
					if (foldersToReload.get(i).equals(common) || foldersToReload.get(i).startsWith(common + ".")) {
						break;
					}
					// if we don't have any parents left and still no match, do a root refresh
					else if (!common.contains(".")) {
						rootRefresh = true;
						break;
					}
				}
			}
			if (rootRefresh) {
				break;
			}
		}
		if (rootRefresh) {
			artifact.reloadAll();
		}
		else {
			artifact.reload(common);
		}
		artifact.reload();
	}
	
	private void cleanFolders(ResourceRepository target, DeploymentInformation deploymentInformation) {
		for (String folderToClean : deploymentInformation.getBuild().getFoldersToClean()) {
			DeploymentResult result = new DeploymentResult();
			result.setType(DeploymentResultType.FOLDER);
			result.setId(folderToClean);
			Entry entry = target.getEntry(folderToClean);
			if (entry != null && entry.getParent() instanceof ExtensibleEntry) {
				try {
					((ExtensibleEntry) entry.getParent()).deleteChild(entry.getName(), true);
				}
				catch (IOException e) {
					StringWriter writer = new StringWriter();
					PrintWriter printer = new PrintWriter(writer);
					e.printStackTrace(printer);
					printer.flush();
					result.setError(writer.toString());
				}
			}
			else {
				result.setError("Could not find entry or its parent is not modifiable");
			}
			result.setStopped(new Date());
			deploymentInformation.getResults().add(result);
		}
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
		deploys.getItems().addAll(artifact.getDeployments());
		Button deploy = new Button("Deploy");
		deploy.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent arg0) {
				String deploymentId = deploys.getSelectionModel().getSelectedItem();
				if (deploymentId != null) {
					try {
						DeploymentInformation deploymentInformation = artifact.getDeploymentInformation(deploymentId);
						if (deploymentInformation != null) {
							ResourceRepository target = artifact.getConfiguration().getTarget().getClusterRepository();
							ResourceRepository source = artifact.getDeployment(deploymentId, target, true);
							if (source != null) {
								deploymentInformation.setResults(new ArrayList<DeploymentResult>());
								logger.info("Cleaning folder...");
								cleanFolders(target, deploymentInformation);
								logger.info("Deploying...");
								deploy(source, target, deploymentInformation);
								logger.info("Reloading remote servers...");
								reload(artifact.getConfiguration().getTarget(), deploymentInformation);
								deploymentInformation.setDeployed(new Date());
								ResourceContainer<?> privateDirectory = ResourceUtils.mkdirs(artifact.getDirectory(), EAIResourceRepository.PRIVATE);
								SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd-HH.mm.ss.SSS");
								Resource create = ((ManageableContainer<?>) privateDirectory).create(deploymentId + "-" + formatter.format(deploymentInformation.getDeployed()) + ".xml", "application/xml");
								OutputStream output = new BufferedOutputStream(IOUtils.toOutputStream(((WritableResource) create).getWritable()));
								try {
									deploymentInformation.marshal(output);
								}
								finally {
									output.close();
								}
							}
						}
					}
					catch (Exception e) {
						logger.error("Could not perform deployment", e);
					}
				}
			}
		});
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
		
		pendingPossibleMerges.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<PendingMerge>() {
			@Override
			public void changed(ObservableValue<? extends PendingMerge> arg0, PendingMerge arg1, PendingMerge arg2) {
				if (arg2 != null) {
					openMerge(tabs, arg2);
				}
			}
		});
		pendingPossibleMerges.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent arg0) {
				if (pendingPossibleMerges.getSelectionModel().getSelectedItem() != null) {
					openMerge(tabs, pendingPossibleMerges.getSelectionModel().getSelectedItem());
				}
			}
		});
		pendingRequiredMerges.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<PendingMerge>() {
			@Override
			public void changed(ObservableValue<? extends PendingMerge> arg0, PendingMerge arg1, PendingMerge arg2) {
				if (arg2 != null) {
					openMerge(tabs, arg2);
				}
			}
		});
		pendingRequiredMerges.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent arg0) {
				if (pendingPossibleMerges.getSelectionModel().getSelectedItem() != null) {
					openMerge(tabs, pendingPossibleMerges.getSelectionModel().getSelectedItem());
				}
			}
		});
		
		final ListView<String> unchanged = new ListView<String>();
		final ListView<String> updated = new ListView<String>();
		final ListView<String> removed = new ListView<String>();
		final ListView<String> added = new ListView<String>();
		final ListView<String> missing = new ListView<String>();
		
		final Button prepare = new Button("Prepare");
		final Button createDeployment = new Button("Create");
		final Button cancelDeployment = new Button("Cancel");
		prepare.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
			@SuppressWarnings({ "rawtypes", "unchecked" })
			@Override
			public void handle(MouseEvent arg0) {
				try {
					BuildInformation buildInformation = artifact.getConfiguration().getBuild().getBuildInformation(builds.getSelectionModel().getSelectedItem());
					mergedRepository.set(artifact.getConfiguration().getBuild().getBuild(builds.getSelectionModel().getSelectedItem(), artifact.getConfiguration().getTarget().getClusterRepository(), true));
					if (buildInformation.getArtifacts() != null) {
						for (ArtifactMetaData artifactMeta : buildInformation.getArtifacts()) {
							Entry sourceEntry = mergedRepository.get().getEntry(artifactMeta.getId());
							if (sourceEntry instanceof ResourceEntry) {
								ArtifactMerger merger = DeployContextMenu.getMerger(sourceEntry);
								if (merger != null) {
									Entry targetEntry = artifact.getConfiguration().getTarget().getClusterRepository().getEntry(artifactMeta.getId());
									AnchorPane anchorPane = new AnchorPane();
									Artifact mergedArtifact = sourceEntry.getNode().getArtifact();
									if (merger.merge(mergedArtifact, targetEntry == null || !targetEntry.isNode() ? null : targetEntry.getNode().getArtifact(), anchorPane, mergedRepository.get())) {
										if (added.getItems().contains(artifactMeta.getId())) {
											pendingRequiredMerges.getItems().add(new PendingMerge((ResourceEntry) sourceEntry, mergedArtifact, anchorPane));
										}
										else {
											pendingPossibleMerges.getItems().add(new PendingMerge((ResourceEntry) sourceEntry, mergedArtifact, anchorPane));
										}
									}
								}
							}
						}
					}
					builds.setDisable(true);
					refreshBuilds.setDisable(true);
				}
				catch (Exception e) {
					logger.error("Could not create merged repository", e);
				}
			}
		});
		prepare.disableProperty().bind(builds.getSelectionModel().selectedItemProperty().isNull().or(createDeployment.disableProperty().not()));
		createDeployment.disableProperty().bind(mergedRepository.isNull());
		cancelDeployment.disableProperty().bind(mergedRepository.isNull());
		createDeployment.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent arg0) {
				try {
					DeploymentInformation information = new DeploymentInformation();
					information.setTargetId(artifact.getConfiguration().getTarget().getId());
					information.setDeploymentId(artifact.getId());
					information.setBuild(buildInformation);
					information.setAdded(new ArrayList<String>(added.getItems()));
					information.setUnchanged(new ArrayList<String>(unchanged.getItems()));
					information.setRemoved(new ArrayList<String>(removed.getItems()));
					information.setMissing(new ArrayList<String>(missing.getItems()));
					information.setUpdated(new ArrayList<String>(updated.getItems()));
					information.setCreated(new Date());
					List<String> merged = new ArrayList<String>();
					for (PendingMerge merge : pendingPossibleMerges.getItems()) {
						merged.add(merge.getId());
					}
					for (PendingMerge merge : pendingRequiredMerges.getItems()) {
						merged.add(merge.getId());
					}
					information.setMerged(merged);
					
					ResourceContainer<?> container = ((ResourceEntry) entry).getContainer();
					ResourceContainer<?> privateDirectory = ResourceUtils.mkdirs(container, EAIResourceRepository.PRIVATE);
					String name = builds.getSelectionModel().getSelectedItem() + "-" + artifact.getConfiguration().getTarget().getId();
					Resource create = ((ManageableContainer<?>) privateDirectory).create(name + ".zip", "application/zip");
					ZipOutputStream zip = new ZipOutputStream(IOUtils.toOutputStream(((WritableResource) create).getWritable()));
					try {
						ZipEntry zipEntry = new ZipEntry("deployment.xml");
						zip.putNextEntry(zipEntry);
						information.marshal(zip);
						EAIRepositoryUtils.zip(zip, (ResourceEntry) mergedRepository.get().getRoot(), null);
					}
					finally {
						zip.close();
					}
					if (!deploys.getItems().contains(name)) {
						deploys.getItems().add(name);
					}
					mergedRepository.set(null);
					builds.setDisable(false);
					refreshBuilds.setDisable(false);
					pendingPossibleMerges.getItems().clear();
					pendingRequiredMerges.getItems().clear();
					tabs.getTabs().clear();
				}
				catch (Exception e) {
					logger.error("Could not create build", e);
				}
			}
		});
		cancelDeployment.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent arg0) {
				builds.setDisable(false);
				refreshBuilds.setDisable(false);
				mergedRepository.set(null);
				pendingPossibleMerges.getItems().clear();
				pendingRequiredMerges.getItems().clear();
				tabs.getTabs().clear();
			}
		});
		
		HBox buildBox = new HBox();
		buildBox.getChildren().addAll(new Label("Builds: "), builds, refreshBuilds, prepare, createDeployment, cancelDeployment);
		
		builds.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
			@Override
			public void changed(ObservableValue<? extends String> arg0, String arg1, String arg2) {
				unchanged.getItems().clear();
				updated.getItems().clear();
				removed.getItems().clear();
				added.getItems().clear();
				missing.getItems().clear();
				pendingPossibleMerges.getItems().clear();
				pendingRequiredMerges.getItems().clear();
				mergedRepository.set(null);
				
				if (arg2 != null) {
					try {
						ResourceRepository target = artifact.getConfiguration().getTarget().getClusterRepository();
						buildInformation = artifact.getConfiguration().getBuild().getBuildInformation(arg2);
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
											// if the folder is set to be cleaned and it is not in the artifacts-to-be-deployed, it will be removed (ignore dynamics & folders)
											if (entry.isNode() && !(entry instanceof DynamicEntry) && !artifactIds.contains(entry.getId())) {
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
		final TitledPane pendingPossiblePane = new TitledPane("Pending Possible Merge (0)", pendingPossibleMerges);
		pendingPossibleMerges.itemsProperty().get().addListener(new ListChangeListener<PendingMerge>() {
			@Override
			public void onChanged(javafx.collections.ListChangeListener.Change<? extends PendingMerge> arg0) {
				pendingPossiblePane.setText("Pending Possible Merge (" + pendingPossibleMerges.getItems().size() + ")");				
			}
		});
		final TitledPane pendingRequiredPane = new TitledPane("Pending Required Merge (0)", pendingRequiredMerges);
		pendingRequiredMerges.itemsProperty().get().addListener(new ListChangeListener<PendingMerge>() {
			@Override
			public void onChanged(javafx.collections.ListChangeListener.Change<? extends PendingMerge> arg0) {
				pendingRequiredPane.setText("Pending Required Merge (" + pendingRequiredMerges.getItems().size() + ")");				
			}
		});
		
		accordion.getPanes().addAll(unchangedPane, removedPane, addedPane, updatedPane, missingPane, pendingRequiredPane, pendingPossiblePane);
		vbox.getChildren().addAll(deployButtons, buildBox, accordion, tabs);
		AnchorPane.setLeftAnchor(vbox, 0d);
		AnchorPane.setRightAnchor(vbox, 0d);
		scrollRoot.getChildren().add(vbox);
		pane.getChildren().add(scroll);
		return artifact;
	}
	
	private void openMerge(final TabPane tabs, PendingMerge arg2) {
		boolean alreadyOpen = false;
		for (Tab tab : tabs.getTabs()) {
			if (tab.getId().equals(arg2.getId())) {
				alreadyOpen = true;
				tabs.getSelectionModel().select(tab);
				break;
			}
		}
		if (!alreadyOpen) {
			Tab tab = new Tab(arg2.getId());
			tab.setId(arg2.getId());
			tab.setContent(arg2.getPane());
			tabs.getTabs().add(tab);
			tabs.getSelectionModel().select(tab);
		}
	}
	
	public static enum DeploymentResultType {
		ARTIFACT,
		FOLDER
	}
	
	public static class DeploymentResult {
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
	}
	
	@XmlRootElement(name = "deploymentInformation")
	public static class DeploymentInformation {
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
	
	public class PendingMerge {
		private AnchorPane pane;
		private boolean merged;
		private Artifact artifact;
		private ResourceEntry entry;

		public PendingMerge(ResourceEntry entry, Artifact artifact, AnchorPane pane) {
			this.entry = entry;
			this.artifact = artifact;
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
			return artifact.getId();
		}
		@Override
		public String toString() {
			return getId();
		}
		
		@SuppressWarnings("unchecked")
		public void save() {
			try {
				Node node = entry.getNode();
				String environmentId = node.getEnvironmentId();
				long version = node.getVersion();
				Date lastModified = node.getLastModified();
				node.getArtifactManager().newInstance().save(entry, artifact);
				if (entry instanceof ModifiableNodeEntry) {
					((ModifiableNodeEntry) entry).updateNodeContext(environmentId, version, lastModified);
				}
			}
			catch (Exception e) {
				logger.error("Could not save merged version of: " + artifact.getId());
			}
		}
	}
	
	private static boolean isSame(Object a, Object b) {
		return (a == null && b == null) || (a != null && a.equals(b));
	}
}
