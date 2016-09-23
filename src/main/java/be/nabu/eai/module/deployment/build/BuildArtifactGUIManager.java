package be.nabu.eai.module.deployment.build;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.managers.base.BaseArtifactGUIInstance;
import be.nabu.eai.developer.managers.base.BaseGUIManager;
import be.nabu.eai.developer.managers.util.SimpleProperty;
import be.nabu.eai.module.cluster.ClusterArtifact;
import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.EAIRepositoryUtils.EntryFilter;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.DynamicEntry;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.api.ResourceRepository;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.jfx.control.spinner.DoubleSpinner;
import be.nabu.jfx.control.spinner.Spinner.Alignment;
import be.nabu.jfx.control.tree.Marshallable;
import be.nabu.jfx.control.tree.Tree;
import be.nabu.jfx.control.tree.TreeItem;
import be.nabu.jfx.control.tree.TreeUtils;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.resources.ResourceUtils;
import be.nabu.libs.resources.api.ManageableContainer;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.api.WritableResource;
import be.nabu.libs.validator.api.ValidationMessage;
import be.nabu.libs.validator.api.ValidationMessage.Severity;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.WritableContainer;
import be.nabu.utils.io.buffers.bytes.ByteBufferFactory;

public class BuildArtifactGUIManager extends BaseGUIManager<BuildArtifact, BaseArtifactGUIInstance<BuildArtifact>> {

	private Logger logger = LoggerFactory.getLogger(getClass());
	private boolean initializing;
	
	public BuildArtifactGUIManager() {
		super("Build Plan", BuildArtifact.class, new BuildArtifactManager());
	}

	@Override
	public String getCategory() {
		return "Environments";
	}
	
	@Override
	protected List<Property<?>> getCreateProperties() {
		return Arrays.asList(new SimpleProperty<ClusterArtifact>("Source", ClusterArtifact.class, false));
	}

	@Override
	protected BaseArtifactGUIInstance<BuildArtifact> newGUIInstance(Entry entry) {
		return new BaseArtifactGUIInstance<BuildArtifact>(this, entry);
	}

	@Override
	protected void setEntry(BaseArtifactGUIInstance<BuildArtifact> guiInstance, ResourceEntry entry) {
		guiInstance.setEntry(entry);
	}

	@Override
	protected BuildArtifact newInstance(MainController controller, RepositoryEntry entry, Value<?>...values) throws IOException {
		ClusterArtifact source = null;
		if (values != null) {
			for (Value<?> value : values) {
				if ("Source".equals(value.getProperty().getName())) {
					source = (ClusterArtifact) value.getValue();
				}
			}
		}
		BuildArtifact buildArtifact = new BuildArtifact(entry.getId(), entry.getContainer(), entry.getRepository());
		buildArtifact.getConfiguration().setSource(source);
		buildArtifact.save(entry.getContainer());
		return buildArtifact;
	}

	@Override
	protected void setInstance(BaseArtifactGUIInstance<BuildArtifact> guiInstance, BuildArtifact instance) {
		guiInstance.setArtifact(instance);
	}
	
	@Override
	protected BuildArtifact display(MainController controller, AnchorPane pane, Entry entry) throws IOException, ParseException {
		BuildArtifact instance = (BuildArtifact) entry.getNode().getArtifact();
		
		Tree<Entry> tree = new Tree<Entry>(new Marshallable<Entry>() {
			@Override
			public String marshal(Entry instance) {
				return instance.getName();
			}
		});
		Repository source;
		if (instance.getConfiguration().getSource() != null) {
			source = instance.getConfiguration().getSource().getClusterRepository();
		}
		else {
			source = instance.getRepository();
		}
		tree.rootProperty().set(new DeploymentTreeItem(instance, tree, null, source.getRoot(), false));
		tree.prefWidthProperty().bind(pane.widthProperty());
		
		initializing = true;
		// select all the already selected items
		if (instance.getConfiguration().getArtifacts() != null) {
			List<String> ids = new ArrayList<String>(instance.getConfiguration().getArtifacts());
			for (String selected : ids) {
				TreeItem<Entry> resolve = tree.resolve(selected.replace(".", "/"), false);
				if (resolve == null) {
					instance.getConfiguration().getArtifacts().remove(selected);
					MainController.getInstance().setChanged();
					MainController.getInstance().notify(new ValidationMessage(Severity.WARNING, "Can not select: " + selected));
				}
				else {
					((DeploymentTreeItem) resolve).check.setSelected(true);
					// if the parent item is also selected (due to auto-calculation) and it is _not_ in the "folders to be deleted" list, it has to be set to indeterminate
					DeploymentTreeItem parent = ((DeploymentTreeItem) resolve).getParent();
					while (parent != null) {
						if (parent.check.isSelected()) {
							if (!instance.getConfiguration().getFoldersToClean().contains(parent.itemProperty().get().getId())) {
								parent.check.setIndeterminate(true);
							}
						}
						parent = parent.getParent();
					}
				}
			}
		}
		initializing = false;
		
		Label sourceLabel = new Label(instance.getConfiguration().getSource() == null ? "$self" : instance.getConfiguration().getSource().getId());
		if (source instanceof ResourceRepository) {
			URI uri = ResourceUtils.getURI(((ResourceRepository) source).getRoot());
			if (uri != null) {
				sourceLabel.setText(sourceLabel.getText() + ": " + uri);
			}
		}
		
		HBox version = new HBox();
		DoubleSpinner versionSpinner = new DoubleSpinner(Alignment.LEFT);
		versionSpinner.setPrefHeight(20);
		versionSpinner.setMin(1d);
		versionSpinner.valueProperty().set(instance.getConfiguration().getVersion() == null ? 1d : (double) (int) instance.getConfiguration().getVersion());
		versionSpinner.valueProperty().addListener(new ChangeListener<Double>() {
			@Override
			public void changed(ObservableValue<? extends Double> arg0, Double arg1, Double arg2) {
				try {
					instance.getConfiguration().setVersion((int) (double) arg2);
					MainController.getInstance().setChanged();
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		DoubleSpinner minorVersionSpinner = new DoubleSpinner(Alignment.RIGHT);
		minorVersionSpinner.setPrefHeight(20);
		minorVersionSpinner.setMin(1d);
		minorVersionSpinner.valueProperty().set(instance.getConfiguration().getMinorVersion() == null ? 1d : (double) (int) instance.getConfiguration().getMinorVersion());
		minorVersionSpinner.valueProperty().addListener(new ChangeListener<Double>() {
			@Override
			public void changed(ObservableValue<? extends Double> arg0, Double arg1, Double arg2) {
				try {
					instance.getConfiguration().setMinorVersion((int) (double) arg2);
					MainController.getInstance().setChanged();
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		final ListView<String> builds = new ListView<String>();
		builds.getItems().addAll(instance.getBuilds());
		builds.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		
		version.getChildren().addAll(new Label("Version: "), versionSpinner, minorVersionSpinner);
		
		if (entry instanceof ResourceEntry) {
			Button build = new Button("Build");
			build.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
				@Override
				public void handle(MouseEvent arg0) {
					try {
						ResourceContainer<?> container = ((ResourceEntry) entry).getContainer();
						ResourceContainer<?> privateDirectory = ResourceUtils.mkdirs(container, EAIResourceRepository.PRIVATE);
						SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd-HH.mm.ss.SSS");
						Integer version = instance.getConfiguration().getVersion() == null ? 1 : instance.getConfiguration().getVersion();
						Integer minorVersion = instance.getConfiguration().getMinorVersion() == null ? 1 : instance.getConfiguration().getMinorVersion();
						BuildInformation information = new BuildInformation(version, minorVersion, instance.getId(), instance.getConfiguration().getSource() == null ? null : instance.getConfiguration().getSource().getId(), InetAddress.getLocalHost().getHostName());
						Set<String> referenceIds = new HashSet<String>();
						List<ArtifactMetaData> references = new ArrayList<ArtifactMetaData>();
						for (String artifactId : instance.getConfiguration().getArtifacts()) {
							// for each reference, check if it is has an originating artifact or not
							for (String reference : source.getReferences(artifactId)) {
								// we skip references like [B pertaining to java byte arrays
								if (reference == null || reference.startsWith("[")) {
									continue;
								}
								if (!referenceIds.contains(reference) && !instance.getConfiguration().getArtifacts().contains(reference)) {
									referenceIds.add(reference);
									Entry referenceEntry = source.getEntry(reference);
									if (referenceEntry instanceof DynamicEntry) {
										referenceEntry = source.getEntry(((DynamicEntry) referenceEntry).getOriginatingArtifact());
										// it could be that the parent is in the deployment 
										if (referenceEntry != null && instance.getConfiguration().getArtifacts().contains(referenceEntry.getId())) {
											continue;
										}
									}
									if (referenceEntry != null && referenceEntry.isNode()) {
										references.add(new ArtifactMetaData(referenceEntry.getId(), referenceEntry.getNode().getEnvironmentId(), referenceEntry.getNode().getVersion(), referenceEntry.getNode().getLastModified(), referenceEntry.getNode().getArtifactManager()));
									}
								}
							}
						}
						information.setReferences(references);
						information.setFoldersToClean(new ArrayList<String>(instance.getConfiguration().getFoldersToClean()));
						List<ArtifactMetaData> artifacts = new ArrayList<ArtifactMetaData>();
						for (String artifactId : instance.getConfiguration().getArtifacts()) {
							be.nabu.eai.repository.api.Node node = source.getNode(artifactId);
							if (node == null) {
								throw new IllegalStateException("Can not find node: " + artifactId);
							}
							artifacts.add(new ArtifactMetaData(artifactId, node.getEnvironmentId(), node.getVersion(), node.getLastModified(), node.getArtifactManager()));
						}
						information.setArtifacts(artifacts);
						ByteBuffer buffer = ByteBufferFactory.getInstance().newInstance();
						ZipOutputStream zip = new ZipOutputStream(IOUtils.toOutputStream(buffer));
						try {
							ZipEntry zipEntry = new ZipEntry("build.xml");
							zip.putNextEntry(zipEntry);
							information.marshal(zip);
							EAIRepositoryUtils.zip(zip, (ResourceEntry) source.getRoot(), new BuildEntryFilter(instance));
						}
						finally {
							zip.close();
						}
						Resource create = ((ManageableContainer<?>) privateDirectory).create(version + "." + minorVersion + "-" + formatter.format(information.getCreated()) + ".zip", "application/zip");
						WritableContainer<ByteBuffer> output = ((WritableResource) create).getWritable();
						try {
							output.write(buffer);
							builds.getItems().add(create.getName().replace(".zip", ""));
						}
						finally {
							output.close();
						}
					}
					catch (IOException e) {
						logger.error("Could not create build", e);
					}
				}
			});
			version.getChildren().add(build);
		}
		
		Button delete = new Button("Delete");
		delete.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent arg0) {
				ResourceContainer<?> privateDirectory = (ResourceContainer<?>) ((ResourceEntry) entry).getContainer().getChild(EAIResourceRepository.PRIVATE);
				if (privateDirectory != null) {
					builds.disableProperty().set(true);
					List<String> selectedItems = new ArrayList<String>(builds.getSelectionModel().getSelectedItems());
					for (String name : selectedItems) {
						if (privateDirectory.getChild(name + ".zip") != null) {
							try {
								((ManageableContainer<?>) privateDirectory).delete(name + ".zip");
								builds.getItems().remove(name);
							}
							catch (IOException e) {
								logger.error("Could not delete: " + name + ".zip", e);
							}
						}
					}
					builds.disableProperty().set(false);
				}
			}
		});
		delete.disableProperty().bind(builds.getSelectionModel().selectedItemProperty().isNull());
		
		HBox buildButtons = new HBox();
		buildButtons.getChildren().add(delete);
		
		VBox vbox = new VBox();
		vbox.getChildren().addAll(sourceLabel, tree, version, builds, buildButtons);
		pane.getChildren().add(vbox);
		
		return instance;
	}
	
	private class DeploymentTreeItem implements TreeItem<Entry> {

		private ObjectProperty<Entry> itemProperty;
		private BooleanProperty editableProperty = new SimpleBooleanProperty(false), leafProperty;
		private DeploymentTreeItem parent;
		private ObservableList<TreeItem<Entry>> children;
		private ObjectProperty<Node> graphicProperty = new SimpleObjectProperty<Node>();
		private boolean isNode;
		private CheckBox check;
		private Tree<Entry> tree;
		private BuildArtifact artifact;
		
		public DeploymentTreeItem(BuildArtifact artifact, Tree<Entry> tree, DeploymentTreeItem parent, Entry entry, boolean isNode) {
			this.artifact = artifact;
			this.tree = tree;
			this.parent = parent;
			this.isNode = isNode;
			this.itemProperty = new SimpleObjectProperty<Entry>(entry);
			this.leafProperty = new SimpleBooleanProperty(entry.isLeaf() || isNode);
			HBox box = new HBox();
			this.check = new CheckBox();
			box.getChildren().add(check);
			if (isNode) {
				box.getChildren().add(MainController.getInstance().getGUIManager(entry.getNode().getArtifactClass()).getGraphic());
			}
			else {
				box.getChildren().add(MainController.loadGraphic("folder.png"));
			}
			// allow explicit setting to indeterminate so we don't wipe the folder even if everything is selected
			check.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
				@Override
				public void handle(MouseEvent event) {
					if (MouseButton.SECONDARY.equals(event.getButton()) && !leafProperty.get()) {
						if (check.isSelected()) {
							try {
								if (artifact.getConfiguration().getFoldersToClean() != null && artifact.getConfiguration().getFoldersToClean().contains(itemProperty.get().getId())) {
									artifact.getConfiguration().getFoldersToClean().remove(itemProperty.get().getId());
								}
								check.setIndeterminate(true);
								MainController.getInstance().setChanged();
							}
							catch (Exception e) {
								logger.error("Could not unset folder", e);
							}
						}
						event.consume();
					}
				}
			});
			// if you check something, select/deselect everything underneath it (recursively)
			check.selectedProperty().addListener(new ChangeListener<Boolean>() {
				@Override
				public void changed(ObservableValue<? extends Boolean> arg0, Boolean arg1, Boolean arg2) {
					try {
						if (!isInitializing()) {
							if (itemProperty.get().isNode()) {
								if (arg2 && !artifact.getConfiguration().getArtifacts().contains(itemProperty.get().getId())) {
									artifact.getConfiguration().getArtifacts().add(itemProperty.get().getId());
									MainController.getInstance().setChanged();
								}
								else if (!arg2 && artifact.getConfiguration().getArtifacts().contains(itemProperty.get().getId())) {
									artifact.getConfiguration().getArtifacts().remove(itemProperty.get().getId());
									MainController.getInstance().setChanged();
								}
							}
							// if we have selected a folder entirely, add it to the "to clean" list
							if (!itemProperty.get().isLeaf()) {
								if (arg2 && !artifact.getConfiguration().getFoldersToClean().contains(itemProperty.get().getId())) {
									artifact.getConfiguration().getFoldersToClean().add(itemProperty.get().getId());
									MainController.getInstance().setChanged();
								}
								else if (!arg2 && artifact.getConfiguration().getFoldersToClean().contains(itemProperty.get().getId())) {
									artifact.getConfiguration().getFoldersToClean().remove(itemProperty.get().getId());
									MainController.getInstance().setChanged();
								}
							}
						}
						if (arg2) {
							tree.forceLoad(DeploymentTreeItem.this, true);
						}
						// first recurse value
						for (TreeItem<Entry> child : getChildren()) {
							((DeploymentTreeItem) child).check.selectedProperty().set(arg2);
						}
						// then make sure all the parents are in correct state
						DeploymentTreeItem parentToReset = parent;
						while (parentToReset != null) {
							boolean noneSelected = true;
							boolean allSelected = true;
							boolean someIndeterminate = false;
							for (TreeItem<Entry> child : parentToReset.getChildren()) {
								if (((DeploymentTreeItem) child).check.isIndeterminate()) {
									someIndeterminate = true;
								}
								// if something is selected, we can't have "none selected"
								else if (((DeploymentTreeItem) child).check.isSelected()) {
									noneSelected = false;
								}
								// if something is not selected, we can't have "all selected"
								else {
									allSelected = false;
								}
							}
							if (someIndeterminate) {
								if (!isInitializing() && artifact.getConfiguration().getFoldersToClean().contains(parentToReset.itemProperty.get().getId())) {
									artifact.getConfiguration().getFoldersToClean().remove(parentToReset.itemProperty.get().getId());
									MainController.getInstance().setChanged();
								}
								parentToReset.check.setIndeterminate(true);
							}
							else if (noneSelected) {
								if (!isInitializing() && artifact.getConfiguration().getFoldersToClean().contains(parentToReset.itemProperty.get().getId())) {
									artifact.getConfiguration().getFoldersToClean().remove(parentToReset.itemProperty.get().getId());
									MainController.getInstance().setChanged();
								}
								parentToReset.check.setIndeterminate(false);
								parentToReset.check.setSelected(false);
							}
							else if (allSelected) {
								parentToReset.check.setIndeterminate(false);
								parentToReset.check.setSelected(true);
								if (!isInitializing() && !artifact.getConfiguration().getFoldersToClean().contains(parentToReset.itemProperty.get().getId())) {
									artifact.getConfiguration().getFoldersToClean().add(parentToReset.itemProperty.get().getId());
									MainController.getInstance().setChanged();
								}
							}
							else {
								if (!isInitializing() && artifact.getConfiguration().getFoldersToClean().contains(parentToReset.itemProperty.get().getId())) {
									artifact.getConfiguration().getFoldersToClean().remove(parentToReset.itemProperty.get().getId());
									MainController.getInstance().setChanged();
								}
								parentToReset.check.setIndeterminate(true);
							}
							parentToReset = parentToReset.parent;
						}
					}
					catch (Exception e) {
						logger.error("Could not update selected", e);
					}
				}
			});
			graphicProperty.set(box);
		}

		@Override
		public BooleanProperty editableProperty() {
			return editableProperty;
		}

		@Override
		public BooleanProperty leafProperty() {
			return leafProperty;
		}

		@Override
		public ObjectProperty<Entry> itemProperty() {
			return itemProperty;
		}

		@Override
		public ObjectProperty<Node> graphicProperty() {
			return graphicProperty;
		}

		@Override
		public ObservableList<TreeItem<Entry>> getChildren() {
			if (children == null) {
				children = FXCollections.observableArrayList(loadChildren());
			}
			return children;
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (isNode ? 1231 : 1237);
			result = prime * result
					+ ((itemProperty.get() == null) ? 0 : itemProperty.get().hashCode());
			return result;
		}

		public boolean equals(Object object) {
			return object instanceof DeploymentTreeItem 
				&& ((DeploymentTreeItem) object).isNode == isNode
				&& ((DeploymentTreeItem) object).itemProperty.get().equals(itemProperty.get());
		}

		private List<TreeItem<Entry>> loadChildren() {
			List<TreeItem<Entry>> items = new ArrayList<TreeItem<Entry>>();
			// for nodes we have created a duplicate map entry so don't recurse!
			if (isNode) {
				return items;
			}
			for (Entry entry : itemProperty.get()) {
				if (!(entry instanceof ResourceEntry)) {
					continue;
				}
				// if the non-leaf is a repository, it will not be shown as a dedicated map
				// note that we should check that the entry has at least one deployable artifact, otherwise no sense in showing it
				if (!entry.isLeaf() && hasResourceChildren(entry) && (!entry.isNode() || !Repository.class.isAssignableFrom(entry.getNode().getArtifactClass()))) {
					items.add(new DeploymentTreeItem(artifact, tree, this, entry, false));
				}
				// for nodes we add two entries: one for the node, and one for the folder
				if (entry.isNode()) {
					items.add(new DeploymentTreeItem(artifact, tree, this, entry, true));	
				}
			}
			Collections.sort(items, new Comparator<TreeItem<Entry>>() {
				@Override
				public int compare(TreeItem<Entry> arg0, TreeItem<Entry> arg1) {
					DeploymentTreeItem item1 = (DeploymentTreeItem) arg0;
					DeploymentTreeItem item2 = (DeploymentTreeItem) arg1;
					if (item1.isNode && !item2.isNode) {
						return 1;
					}
					else if (!item1.isNode && item2.isNode) {
						return -1;
					}
					else {
						return item1.getName().compareTo(item2.getName());
					}
				}
			});
			return items;
		}

		@Override
		public void refresh() {
			TreeUtils.refreshChildren(this, loadChildren());
		}

		@Override
		public DeploymentTreeItem getParent() {
			return parent;
		}

		@Override
		public String getName() {
			return itemProperty.get().getName();
		}
		
		private boolean hasResourceChildren(Entry entry) {
			for (Entry child : entry) {
				if (child instanceof ResourceEntry) {
					return true;
				}
			}
			return false;
		}
	}
	
	private static class BuildEntryFilter implements EntryFilter {
		private BuildArtifact artifact;

		public BuildEntryFilter(BuildArtifact artifact) {
			this.artifact = artifact;
		}

		@Override
		public boolean accept(ResourceEntry entry) {
			try {
				return artifact.getConfiguration().getArtifacts().contains(entry.getId());
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public boolean recurse(ResourceEntry entry) {
			// always recurse, the acceptance filter will pick out the correct entries
			return true;
		}
	}

	public boolean isInitializing() {
		return initializing;
	}
	
	public static void sort(List<String> artifacts, Repository repository) {
		while(true) {
			boolean changed = false;
			for (int i = 0; i < artifacts.size(); i++) {
				int currentIndex = i;
				for (String reference : repository.getReferences(artifacts.get(i))) {
					if (repository.getReferences(reference).contains(artifacts.get(currentIndex))) {
						throw new RuntimeException("Circular dependency detected between '" + artifacts.get(currentIndex) + "' and '" + reference + "'");
					}
					int indexOf = artifacts.indexOf(reference);
					if (indexOf >= 0 && indexOf < currentIndex) {
						changed = true;
						artifacts.set(indexOf, artifacts.get(currentIndex));
						artifacts.set(currentIndex, reference);
						currentIndex = indexOf;
					}
				}
			}
			if (!changed) {
				break;
			}
		}
	}
}
