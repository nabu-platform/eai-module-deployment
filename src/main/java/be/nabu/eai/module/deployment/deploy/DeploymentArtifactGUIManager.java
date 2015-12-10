package be.nabu.eai.module.deployment.deploy;

import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.scene.control.Accordion;
import javafx.scene.control.ListView;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.managers.base.BaseArtifactGUIInstance;
import be.nabu.eai.developer.managers.base.BaseGUIManager;
import be.nabu.eai.developer.managers.util.SimpleProperty;
import be.nabu.eai.module.cluster.ClusterArtifact;
import be.nabu.eai.module.deployment.build.BuildArtifact;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;

public class DeploymentArtifactGUIManager extends BaseGUIManager<DeploymentArtifact, BaseArtifactGUIInstance<DeploymentArtifact>> {

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
		DeploymentArtifact artifact = (DeploymentArtifact) entry.getNode().getArtifact();
		
		Accordion accordion = new Accordion();
		final ListView<String> unchanged = new ListView<String>();
		final TitledPane unchangedPane = new TitledPane("Unchanged (0)", unchanged);
		unchanged.itemsProperty().addListener(new ChangeListener<ObservableList<String>>() {
			@Override
			public void changed(ObservableValue<? extends ObservableList<String>> arg0, ObservableList<String> arg1, ObservableList<String> arg2) {
				unchangedPane.setText("Unchanged (" + arg2.size() + ")");
			}
		});
		accordion.getPanes().add(unchangedPane);
		VBox vbox = new VBox();
		vbox.getChildren().addAll(accordion);
		AnchorPane.setLeftAnchor(vbox, 0d);
		AnchorPane.setRightAnchor(vbox, 0d);
		pane.getChildren().add(vbox);
		return artifact;
	}

}
