package be.nabu.eai.module.deployment.action;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.artifacts.jaxb.JAXBArtifact;
import be.nabu.eai.repository.util.SystemPrincipal;
import be.nabu.libs.resources.api.ManageableContainer;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.api.WritableResource;
import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.ServiceUtils;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.api.ServiceResult;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.binding.api.Window;
import be.nabu.libs.types.binding.xml.XMLBinding;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.io.api.WritableContainer;

public class DeploymentAction extends JAXBArtifact<DeploymentActionConfiguration> {

	private Logger logger = LoggerFactory.getLogger(getClass());
	
	public DeploymentAction(String id, ResourceContainer<?> directory, Repository repository) {
		super(id, directory, repository, "deployment-runner.xml", DeploymentActionConfiguration.class);
	}

	// we run the source service with the (optionally) configured input
	// and we save the output in the resource folder so it is included in the build
	public void runSource() throws IOException, ServiceException, InterruptedException, ExecutionException {
		Resource state = getDirectory().getChild("state.xml");
		DefinedService source = getConfig().getSource();
		DefinedService target = getConfig().getTarget();
		if (source != null && target != null) {
			ServiceRuntime serviceRuntime = new ServiceRuntime(source, getRepository().newExecutionContext(SystemPrincipal.ROOT));
			ServiceUtils.setServiceContext(serviceRuntime, getId());
			ComplexContent input = source.getServiceInterface().getInputDefinition().newInstance();
			Map<String, String> properties = getConfig().getProperties();
			if (properties != null) {
				for (String key : properties.keySet()) {
					input.set(key, properties.get(key));
				}
			}
			Future<ServiceResult> run = getRepository().getServiceRunner().run(source, getRepository().newExecutionContext(SystemPrincipal.ROOT), input);
			ServiceResult serviceResult = run.get();
			if (serviceResult.getException() != null) {
				throw serviceResult.getException();
			}
			ComplexContent output = serviceResult.getOutput();
			
			// we now map the output of the source to the input of the target
			ComplexContent targetInput = target.getServiceInterface().getInputDefinition().newInstance();
			for (Element<?> child : TypeUtils.getAllChildren(target.getServiceInterface().getInputDefinition())) {
				targetInput.set(child.getName(), output.get(child.getName()));
			}
			XMLBinding binding = new XMLBinding(target.getServiceInterface().getInputDefinition(), Charset.forName("UTF-8"));
			if (state == null) {
				state = ((ManageableContainer<?>) getDirectory()).create("state.xml", "application/xml");
			}
			WritableContainer<ByteBuffer> writable = ((WritableResource) state).getWritable();
			OutputStream outputStream = IOUtils.toOutputStream(writable);
			try {
				binding.marshal(outputStream, targetInput);
			}
			finally {
				outputStream.close();
			}
		}
		// if there is no source, we delete any run file that might exist from a previous run
		else if (state != null) {
			((ManageableContainer<?>) getDirectory()).delete(state.getName());
		}
	}
	
	public void runTarget() throws IOException, ParseException, ServiceException {
		DefinedService target = getConfig().getTarget();
		if (target != null) {
			Resource state = getDirectory().getChild("state.xml");
			ComplexContent input;
			if (state != null) {
				logger.info("Running target with state: " + target.getId());
				XMLBinding binding = new XMLBinding(target.getServiceInterface().getInputDefinition(), Charset.forName("UTF-8"));
				ReadableContainer<ByteBuffer> readable = ((ReadableResource) state).getReadable();
				InputStream inputStream = IOUtils.toInputStream(readable);
				try {
					input = binding.unmarshal(inputStream, new Window[0]);
				}
				finally {
					inputStream.close();
				}
			}
			else {
				input = target.getServiceInterface().getInputDefinition().newInstance();
				logger.info("Running target stateless: " + target.getId());
			}
			// can't set service context this way :'(
			// it has to be done though cause it has to run on the server, not the developer (even if he is coordinating it)
			// technically speaking the runTarget "should" be run by the server, not the developer (unlike runSource) but still...
			getRepository().getServiceRunner().run(target, getRepository().newExecutionContext(SystemPrincipal.ROOT), input);
		}
		else {
			logger.error("Could not find target of deployment action: " + getId());
		}
	}
}
