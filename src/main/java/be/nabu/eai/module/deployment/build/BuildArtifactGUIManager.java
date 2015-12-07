package be.nabu.eai.module.deployment.build;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.managers.base.BaseArtifactGUIInstance;
import be.nabu.eai.developer.managers.base.BaseGUIManager;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.jfx.control.spinner.DoubleSpinner;
import be.nabu.jfx.control.spinner.Spinner.Alignment;
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
		
		HBox version = new HBox();
		DoubleSpinner versionSpinner = new DoubleSpinner(Alignment.VERTICAL);
		versionSpinner.setMaxWidth(50);
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
		DoubleSpinner minorVersionSpinner = new DoubleSpinner(Alignment.VERTICAL);
		minorVersionSpinner.setMin(1d);
		minorVersionSpinner.setMaxWidth(50);
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
		vbox.getChildren().addAll(version);
		pane.getChildren().add(vbox);
		
		return instance;
	}
}
