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

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.managers.base.BaseJAXBGUIManager;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.Element;

public class DeploymentActionGUIManager extends BaseJAXBGUIManager<DeploymentActionConfiguration, DeploymentAction> {

	public DeploymentActionGUIManager() {
		super("Action", DeploymentAction.class, new DeploymentActionManager(), DeploymentActionConfiguration.class);
	}

	@Override
	protected List<Property<?>> getCreateProperties() {
		return null;
	}

	@Override
	protected DeploymentAction newInstance(MainController controller, RepositoryEntry entry, Value<?>...values) throws IOException {
		return new DeploymentAction(entry.getId(), entry.getContainer(), entry.getRepository());
	}

	@Override
	public String getCategory() {
		return "Deployment";
	}

	@SuppressWarnings("unchecked")
	@Override
	public <V> void setValue(DeploymentAction instance, Property<V> property, V value) {
		if ("source".equals(property.getName())) {
			Map<String, String> properties = getConfiguration(instance).getProperties();
			if (properties == null) {
				properties = new LinkedHashMap<String, String>();
			}
			else {
				properties.clear();
			}
			if (value != null) {
				DefinedService service = (DefinedService) value;
				for (Element<?> element : TypeUtils.getAllChildren(service.getServiceInterface().getInputDefinition())) {
					properties.put(element.getName(), properties.get(element.getName()));
				}
			}
			getConfiguration(instance).setProperties(properties);
		}
		if (!"properties".equals(property.getName())) {
			super.setValue(instance, property, value);
		}
		else if (value instanceof Map) {
			getConfiguration(instance).getProperties().putAll(((Map<? extends String, ? extends String>) value));
		}
	}
	
}
