package be.nabu.eai.module.deployment.deploy;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
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
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.api.ArtifactDiffer;
import be.nabu.eai.developer.api.ArtifactMerger;
import be.nabu.eai.developer.managers.base.BaseArtifactGUIInstance;
import be.nabu.eai.developer.managers.base.BaseGUIManager;
import be.nabu.eai.developer.managers.util.SimpleProperty;
import be.nabu.eai.developer.util.Confirm;
import be.nabu.eai.developer.util.Confirm.ConfirmType;
import be.nabu.eai.module.cluster.ClusterArtifact;
import be.nabu.eai.module.cluster.menu.ClusterContextMenu;
import be.nabu.eai.module.deployment.build.ArtifactMetaData;
import be.nabu.eai.module.deployment.build.BuildArtifact;
import be.nabu.eai.module.deployment.build.BuildInformation;
import be.nabu.eai.module.deployment.menu.DeployContextMenu;
import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.api.DynamicEntry;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ExtensibleEntry;
import be.nabu.eai.repository.api.ModifiableNodeEntry;
import be.nabu.eai.repository.api.Node;
import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.api.ResourceRepository;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.eai.server.ServerConnection;
import be.nabu.libs.artifacts.api.Artifact;
import be.nabu.libs.http.api.HTTPResponse;
import be.nabu.libs.http.core.DefaultHTTPRequest;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.resources.api.FiniteResource;
import be.nabu.libs.resources.api.ManageableContainer;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.api.WritableResource;
import be.nabu.libs.validator.api.Validation;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.mime.api.ContentPart;
import be.nabu.utils.mime.api.ModifiablePart;
import be.nabu.utils.mime.impl.MimeHeader;
import be.nabu.utils.mime.impl.PlainMimeContentPart;

public class DeploymentArtifactGUIManager extends BaseGUIManager<DeploymentArtifact, BaseArtifactGUIInstance<DeploymentArtifact>> {

	private static Logger logger = LoggerFactory.getLogger(DeploymentArtifactGUIManager.class);
	private ObjectProperty<ResourceRepository> mergedRepository = new SimpleObjectProperty<ResourceRepository>();
	private BuildInformation buildInformation;
	private ListView<PendingMerge> pendingPossibleMerges = new ListView<PendingMerge>();
	private ListView<PendingMerge> pendingRequiredMerges = new ListView<PendingMerge>();
	
	public DeploymentArtifactGUIManager() {
		super("Deployment Plan", DeploymentArtifact.class, new DeploymentArtifactManager());
	}

	@Override
	public String getCategory() {
		return "Environments";
	}
	
	@Override
	protected List<Property<?>> getCreateProperties() {
		return Arrays.asList(
			new SimpleProperty<BuildArtifact>("Build", BuildArtifact.class, true),
			new SimpleProperty<ClusterArtifact>("Target", ClusterArtifact.class, true),
			new SimpleProperty<URI>("Deployment Location URI", URI.class, false)
		);
	}

	@Override
	protected BaseArtifactGUIInstance<DeploymentArtifact> newGUIInstance(Entry entry) {
		return new BaseArtifactGUIInstance<DeploymentArtifact>(this, entry) {
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
		DeploymentArtifact artifact = new DeploymentArtifact(entry.getId(), entry.getContainer(), entry.getRepository());
		if (values != null) {
			for (Value<?> value : values) {
				if ("Target".equals(value.getProperty().getName())) {
					artifact.getConfiguration().setTarget((ClusterArtifact) value.getValue());
				}
				else if ("Build".equals(value.getProperty().getName())) {
					artifact.getConfiguration().setBuild((BuildArtifact) value.getValue());
				}
				else if ("Deployment Location URI".equals(value.getProperty().getName())) {
					artifact.getConfiguration().setUri((URI) value.getValue());
				}
			}
		}
		if (artifact.getConfiguration().getTarget() == null || artifact.getConfiguration().getBuild() == null) {
			throw new IllegalArgumentException("Need to specify a target cluster and a build");
		}
		artifact.save(entry.getContainer());
		return artifact;
	}

	public static DeploymentInformation deployBuild(Resource buildZip, ResourceRepository target) throws IOException {
		BuildInformation buildInformation = BuildArtifact.getBuildInformation(buildZip);
		if (buildInformation == null) {
			throw new IllegalArgumentException("Not a valid build zip");
		}
		DeploymentInformation information = new DeploymentInformation();
		information.setTargetId("$local");
		information.setDeploymentId(buildInformation.getBuildId());
		information.setBuild(buildInformation);
		information.setAdded(new ArrayList<String>());
		information.setUnchanged(new ArrayList<String>());
		information.setRemoved(new ArrayList<String>());
		information.setMissing(new ArrayList<String>());
		information.setUpdated(new ArrayList<String>());
		information.setMerged(new ArrayList<String>());
		information.setCreated(new Date());
		cleanFolders(target, information);
		deploy(DeploymentArtifact.getAsRepository(target, true, buildZip), target, information);
		String commonToReload = DeploymentUtils.getCommonToReload(information);
		if (commonToReload == null) {
			target.reloadAll();
		}
		else {
			target.reload(commonToReload);
		}
		information.setDeployed(new Date());
		return information;
	}
	
	@SuppressWarnings("unchecked")
	private static void deploy(ResourceRepository source, ResourceRepository target, DeploymentInformation deploymentInformation) {
		List<String> elementsToReload = new ArrayList<String>();
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
					DeployContextMenu.deploy((ResourceEntry) entry, resolved.getArtifact(), resolved.getArtifactManager().newInstance(), target, meta.getEnvironmentId(), meta.getVersion(), meta.getLastModified(), false);
					elementsToReload.add(meta.getId());
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
		logger.info("Deployment performed, reloading local cluster repository");
		target.reloadAll(elementsToReload);
	}
	
	private void reload(ClusterArtifact artifact, DeploymentInformation deploymentInformation) {
		String commonToReload = DeploymentUtils.getCommonToReload(deploymentInformation);
		if (commonToReload == null) {
			artifact.reloadAll();
		}
		else {
			artifact.reload(commonToReload);
		}
		artifact.reload();
	}
	
	private static void cleanFolders(ResourceRepository target, DeploymentInformation deploymentInformation) {
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
		
		final DeploymentArtifact artifact = (DeploymentArtifact) entry.getNode().getArtifact();
		
		final VBox vbox = new VBox();
		final TabPane tabs = new TabPane();
		final HBox deployButtons = new HBox();
		final ComboBox<String> deploys = new ComboBox<String>();
		deploys.getItems().addAll(artifact.getDeployments());
		Button deploy = new Button("Deploy (Push)");
		deploy.disableProperty().bind(deploys.getSelectionModel().selectedItemProperty().isNull());
		deploy.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent arg0) {
				String deploymentId = deploys.getSelectionModel().getSelectedItem();
				if (deploymentId != null) {
					MainController.getInstance().offload(new Runnable() {
						public void run() {
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
										ResourceContainer<?> deploymentContainer = artifact.getDeploymentContainer();
										SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd-HH.mm.ss.SSS");
										Resource create = ((ManageableContainer<?>) deploymentContainer).create(deploymentId + "-" + formatter.format(deploymentInformation.getDeployed()) + ".xml", "application/xml");
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
					}, true, "Deploy " + deploymentId);
				}
			}
		});
		Button deployPush = new Button("Deploy (Drop)");
		deployPush.disableProperty().bind(deploys.getSelectionModel().selectedItemProperty().isNull());
		deployPush.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent arg0) {
				String deploymentId = deploys.getSelectionModel().getSelectedItem();
				MainController.getInstance().offload(new Runnable() {
					public void run() {
						try {
							List<String> hosts = artifact.getConfiguration().getTarget().getConfig().getHosts();
							Resource deploymentArchive = artifact.getDeploymentArchive(deploymentId);
							for (String host : hosts) {
								Date date = new Date();
								ServerConnection connection = artifact.getConfiguration().getTarget().getConnection(host);
								HTTPResponse execute = connection.getClient().execute(new DefaultHTTPRequest("POST", "/deploy", new PlainMimeContentPart(
										null, 
										((ReadableResource) deploymentArchive).getReadable(), 
										new MimeHeader("Content-Length", "" + ((FiniteResource) deploymentArchive).getSize()),
										new MimeHeader("Content-Type", "application/zip"),
										new MimeHeader("Host", host)
									)),
									connection.getPrincipal(), 
									connection.getContext() != null, 
									false
								);
								logger.info("Deployment to '" + host + "' took: " + (new Date().getTime() - date.getTime()) + "ms");
								if (execute.getCode() != 200) {
									logger.error("An exception occurred while deploying to '" + host + "': " + execute.getCode());
								}
								ModifiablePart content = execute.getContent();
								if (content instanceof ContentPart) {
									ReadableContainer<ByteBuffer> readable = ((ContentPart) content).getReadable();
									try {
										DeploymentInformation unmarshalled = DeploymentInformation.unmarshal(IOUtils.toInputStream(readable));
										StringBuilder builder = new StringBuilder();
										if (unmarshalled.getResults() != null) {
											for (DeploymentResult result : unmarshalled.getResults()) {
												if (result.getError() != null) {
													builder.append(result.getId()).append("\n").append(result.getError()).append("\n");
												}
											}
										}
										String string = builder.toString();
										if (!string.isEmpty()) {
											Platform.runLater(new Runnable() {
												public void run() {
													Confirm.confirm(ConfirmType.ERROR, "Errors during deployment", string, null);
												}
											});
										}
									}
									finally {
										readable.close();
									}
								}
							}
						}
						catch (Exception e) {
							logger.error("Could not perform deployment", e);
						}
					}
				}, true, "Deploy " + deploymentId);
			}
		});
		
		if (artifact.getConfig().getTarget().isSimulation()) {
			deployButtons.getChildren().addAll(new Label("Deployments: "), deploys, deploy);
		}
		else {
			deployButtons.getChildren().addAll(new Label("Deployments: "), deploys, deploy, deployPush);
		}
		
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
		
		updated.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
			@Override
			public void changed(ObservableValue<? extends String> arg0, String arg1, String arg2) {
				if (mergedRepository.get() != null && arg2 != null) {
					try {
						openCompare(mergedRepository.get(), artifact.getConfiguration().getTarget().getClusterRepository(), tabs, arg2);
					}
					catch (Exception e) {
						logger.error("Could not open compare", e);
					}
				}
			}
		});
		updated.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent arg0) {
				if (mergedRepository.get() != null && updated.getSelectionModel().getSelectedItem() != null) {
					try {
						openCompare(mergedRepository.get(), artifact.getConfiguration().getTarget().getClusterRepository(), tabs, updated.getSelectionModel().getSelectedItem());
					}
					catch (Exception e) {
						logger.error("Could not open compare", e);
					}
				}
			}
		});
		
		final Button prepare = new Button("Prepare");
		final Button createDeployment = new Button("Create");
		final Button cancelDeployment = new Button("Cancel");
		prepare.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
			@SuppressWarnings({ "rawtypes", "unchecked" })
			@Override
			public void handle(ActionEvent arg0) {
				MainController.getInstance().offload(new Runnable() {
					public void run() {
						try {
							List<PendingMerge> tmpRequiredMerges = new ArrayList<PendingMerge>();
							List<PendingMerge> tmpPossibleMerges = new ArrayList<PendingMerge>();
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
												if (added.getItems().contains(artifactMeta.getId()) || updated.getItems().contains(artifactMeta.getId())) {
													tmpRequiredMerges.add(new PendingMerge((ResourceEntry) sourceEntry, mergedArtifact, anchorPane));
												}
												else {
													tmpPossibleMerges.add(new PendingMerge((ResourceEntry) sourceEntry, mergedArtifact, anchorPane));
												}
											}
										}
									}
								}
							}
							Platform.runLater(new Runnable() {
								public void run() {
									pendingRequiredMerges.getItems().addAll(tmpRequiredMerges);
									pendingPossibleMerges.getItems().addAll(tmpPossibleMerges);
									builds.setDisable(true);
									refreshBuilds.setDisable(true);
								}
							});
						}
						catch (Exception e) {
							logger.error("Could not create merged repository", e);
						}
					}
				}, true, "Prepare build for artifact " + artifact.getId());
			}
		});
		prepare.disableProperty().bind(refreshBuilds.disabledProperty().or(builds.getSelectionModel().selectedItemProperty().isNull().or(createDeployment.disableProperty().not())));
		createDeployment.disableProperty().bind(mergedRepository.isNull());
		cancelDeployment.disableProperty().bind(mergedRepository.isNull());
		createDeployment.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent arg0) {
				MainController.getInstance().offload(new Runnable() {
					public void run() {
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
								if (!merge.isSaved()) {
									merge.save();
								}
								merged.add(merge.getId());
							}
							for (PendingMerge merge : pendingRequiredMerges.getItems()) {
								if (!merge.isSaved()) {
									logger.warn("No merge performed for required merge: " + merge.getId());
									merge.save();
								}
								merged.add(merge.getId());
							}
							information.setMerged(merged);
							
							ResourceContainer<?> deploymentContainer = artifact.getDeploymentContainer();
							String name = builds.getSelectionModel().getSelectedItem() + "-" + artifact.getConfiguration().getTarget().getId();
							Resource create = ((ManageableContainer<?>) deploymentContainer).create(name + ".zip", "application/zip");
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
							Platform.runLater(new Runnable() {
								public void run() {
									builds.setDisable(false);
									refreshBuilds.setDisable(false);
									pendingPossibleMerges.getItems().clear();
									pendingRequiredMerges.getItems().clear();
									tabs.getTabs().clear();
								}
							});
						}
						catch (Exception e) {
							logger.error("Could not create build", e);
						}
					}
				}, true, "Create deployment for " + artifact.getId());
			}
		});
		cancelDeployment.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent arg0) {
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
				builds.setDisable(true);
				refreshBuilds.setDisable(true);
				unchanged.getItems().clear();
				updated.getItems().clear();
				removed.getItems().clear();
				added.getItems().clear();
				missing.getItems().clear();
				pendingPossibleMerges.getItems().clear();
				pendingRequiredMerges.getItems().clear();
				mergedRepository.set(null);
				if (arg2 != null) {
					MainController.getInstance().offload(new Runnable() {
						public void run() {
							try {
								ResourceRepository target = artifact.getConfiguration().getTarget().getClusterRepository();
								buildInformation = artifact.getConfiguration().getBuild().getBuildInformation(arg2);
								List<String> artifactIds = new ArrayList<String>();
								if (buildInformation != null) {
									final List<String> tmpAdded = new ArrayList<String>();
									final List<String> tmpUnchanged = new ArrayList<String>();
									final List<String> tmpUpdated = new ArrayList<String>();
									final List<String> tmpRemoved = new ArrayList<String>();
									final List<String> tmpMissing = new ArrayList<String>();
									if (buildInformation.getArtifacts() != null) {
										for (ArtifactMetaData artifactBuild : buildInformation.getArtifacts()) {
											artifactIds.add(artifactBuild.getId());
											Node node = target.getNode(artifactBuild.getId());
											if (node == null) {
												tmpAdded.add(artifactBuild.getId());
											}
											else {
												boolean isUnchanged = isSame(artifactBuild.getEnvironmentId(), node.getEnvironmentId());
												isUnchanged &= isSame(artifactBuild.getLastModified(), node.getLastModified());
												isUnchanged &= isSame(Long.valueOf(artifactBuild.getVersion()), Long.valueOf(node.getVersion()));
												if (isUnchanged) {
													tmpUnchanged.add(artifactBuild.getId());
												}
												else {
													tmpUpdated.add(artifactBuild.getId());
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
														tmpRemoved.add(entry.getId());
													}
												}
											}
										}
									}
									if (buildInformation.getReferences() != null) {
										for (ArtifactMetaData reference : buildInformation.getReferences()) {
											Entry referenceEntry = target.getEntry(reference.getId());
											if (referenceEntry == null || !referenceEntry.isNode()) {
												tmpMissing.add(reference.getId());
											}
										}
									}
									Platform.runLater(new Runnable() {
										public void run() {
											added.getItems().addAll(tmpAdded);
											unchanged.getItems().addAll(tmpUnchanged);
											updated.getItems().addAll(tmpUpdated);
											removed.getItems().addAll(tmpRemoved);
											missing.getItems().addAll(tmpMissing);
										}
									});
								}
							}
							catch (IOException e) {
								logger.error("Could not open build: " + arg2, e);
							}
							refreshBuilds.setDisable(false);
							builds.setDisable(false);
						}
					}, true, "Compare " + artifact.getId());
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
		VBox.setVgrow(tabs, Priority.ALWAYS);
		
		scroll.setContent(vbox);
		
		vbox.prefWidthProperty().bind(scroll.widthProperty());
		vbox.prefHeightProperty().bind(scroll.heightProperty());
		
		AnchorPane.setLeftAnchor(scroll, 0d);
		AnchorPane.setRightAnchor(scroll, 0d);
		AnchorPane.setBottomAnchor(scroll, 0d);
		AnchorPane.setTopAnchor(scroll, 0d);
		pane.getChildren().add(scroll);
		return artifact;
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void openCompare(Repository source, Repository target, TabPane tabs, String id) throws IOException, ParseException {
		boolean alreadyOpen = false;
		for (Tab tab : tabs.getTabs()) {
			if (tab.getId().equals("Diff: " + id)) {
				alreadyOpen = true;
				tabs.getSelectionModel().select(tab);
				break;
			}
		}
		if (!alreadyOpen) {
			Entry entry = source.getEntry(id);
			if (entry != null && entry.isNode()) {
				ArtifactDiffer differ = ClusterContextMenu.getDiffer(entry);
				if (differ != null) {
					Entry targetEntry = target.getEntry(id);
					if (targetEntry != null) {
						AnchorPane pane = new AnchorPane();
						if (differ.diff(entry.getNode().getArtifact(), targetEntry.getNode().getArtifact(), pane)) {
							Tab tab = new Tab("Diff: " + id);
							tab.setId("Diff: " + id);
							tab.setContent(pane);
							tabs.getTabs().add(tab);
							tabs.getSelectionModel().select(tab);
						}
					}
				}
			}
		}
	}
	
	private void openMerge(TabPane tabs, PendingMerge arg2) {
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
			arg2.getPane().prefHeightProperty().bind(tabs.heightProperty().subtract(50d));
			tabs.getTabs().add(tab);
			tabs.getSelectionModel().select(tab);
		}
	}
	
	public class PendingMerge {
		private AnchorPane pane;
		private boolean merged;
		private Artifact artifact;
		private ResourceEntry entry;
		private boolean saved;

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
				saved = true;
			}
			catch (Exception e) {
				logger.error("Could not save merged version of: " + artifact.getId());
			}
		}

		public boolean isSaved() {
			return saved;
		}
	}
	
	private static boolean isSame(Object a, Object b) {
		return (a == null && b == null) || (a != null && a.equals(b));
	}
}
