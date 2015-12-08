package be.nabu.eai.module.deployment.build;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.managers.base.BaseArtifactGUIInstance;
import be.nabu.eai.developer.managers.base.BaseGUIManager;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.jfx.control.spinner.DoubleSpinner;
import be.nabu.jfx.control.spinner.Spinner.Alignment;
import be.nabu.jfx.control.tree.Marshallable;
import be.nabu.jfx.control.tree.Tree;
import be.nabu.jfx.control.tree.TreeItem;
import be.nabu.jfx.control.tree.TreeUtils;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;

public class BuildArtifactGUIManager extends BaseGUIManager<BuildArtifact, BaseArtifactGUIInstance<BuildArtifact>> {

	public BuildArtifactGUIManager() {
		super("Build", BuildArtifact.class, new BuildArtifactManager());
	}

	@Override
	protected List<Property<?>> getCreateProperties() {
		return null;
	}

	@Override
	protected BaseArtifactGUIInstance<BuildArtifact> newGUIInstance(Entry entry) {
		return new BaseArtifactGUIInstance<BuildArtifact>(this, getArtifactManager(), entry);
	}

	@Override
	protected void setEntry(BaseArtifactGUIInstance<BuildArtifact> guiInstance, ResourceEntry entry) {
		guiInstance.setEntry(entry);
	}

	@Override
	protected BuildArtifact newInstance(MainController controller, RepositoryEntry entry, Value<?>... values) throws IOException {
		return new BuildArtifact(entry.getId(), entry.getContainer(), entry.getRepository());
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
		tree.rootProperty().set(new DeploymentTreeItem(tree, null, instance.getRepository().getRoot(), false));
		tree.prefWidthProperty().bind(pane.widthProperty());
		// need to force load the tree, otherwise we can't easily select items (items that are not loaded will not be selected)
//		tree.forceLoad();
		
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
		version.getChildren().addAll(new Label("Version: "), versionSpinner, minorVersionSpinner);
		
		VBox vbox = new VBox();
		vbox.getChildren().addAll(tree, version);
		pane.getChildren().add(vbox);
		
		return instance;
	}
	
	private static class DeploymentTreeItem implements TreeItem<Entry> {

		private ObjectProperty<Entry> itemProperty;
		private BooleanProperty editableProperty = new SimpleBooleanProperty(false), leafProperty;
		private DeploymentTreeItem parent;
		private ObservableList<TreeItem<Entry>> children;
		private ObjectProperty<Node> graphicProperty = new SimpleObjectProperty<Node>();
		private boolean isNode;
		private CheckBox check;
		private Tree<Entry> tree;
		
		public DeploymentTreeItem(Tree<Entry> tree, DeploymentTreeItem parent, Entry entry, boolean isNode) {
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
			// if you check something, select/deselect everything underneath it (recursively)
			check.selectedProperty().addListener(new ChangeListener<Boolean>() {
				@Override
				public void changed(ObservableValue<? extends Boolean> arg0, Boolean arg1, Boolean arg2) {
					if (arg2) {
						tree.forceLoad(DeploymentTreeItem.this, true);
					}
					for (TreeItem<Entry> child : getChildren()) {
						((DeploymentTreeItem) child).check.selectedProperty().set(arg2);
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
					items.add(new DeploymentTreeItem(tree, this, entry, false));
				}
				// for nodes we add two entries: one for the node, and one for the folder
				if (entry.isNode()) {
					items.add(new DeploymentTreeItem(tree, this, entry, true));	
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
}
