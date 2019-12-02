package com.adaptris.rest;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import javax.management.*;

import com.adaptris.core.*;
import com.adaptris.core.management.MgmtComponentImpl;
import com.adaptris.core.util.JmxHelper;
import com.adaptris.core.util.LifecycleHelper;
import com.adaptris.rest.health.AdapterHealth;
import com.adaptris.rest.health.ChannelHealth;
import com.adaptris.rest.health.CommonHealth;
import com.adaptris.rest.health.WorkflowHealth;

public class WorkflowHealthCheckComponent extends MgmtComponentImpl implements AdaptrisMessageListener {

	private static final Long DEFAULT_INITIAL_JETTY_CONTEXT_WAIT = (30l * 1000l);

	private static final String PATH = "/workflow-health-check/*";

	private static final String ADAPTER_OBJ_TYPE_WILD = "com.adaptris:type=Adapter,*";

	private static final String ADAPTER_OBJ_TYPE = "com.adaptris:type=Adapter,";

	private static final String CHANNEL_OBJ_TYPE = "com.adaptris:type=Channel,";

	private static final String WORKFLOW_OBJ_TYPE = "com.adaptris:type=Workflow,";

	private static final String ACCEPTED_FILTER = "GET";

	private static final String UNIQUE_ID = "UniqueId";

	private static final String CHILDREN_ATTRIBUTE = "Children";

	private static final String COMPONENT_STATE = "ComponentState";

	private static final String PATH_KEY = "jettyURI";

	private static final String INVALID_ADAPTER_ID = "The adapter id provided is invalid.\n";

	private static final String INVALID_CHANNEL_ID = "The channel id provided is invalid.\n";

	private static final String INVALID_WORKFLOW_ID = "The workflow id provided is invalid.\n";


	private transient WorkflowServicesConsumer consumer;

	private transient DefaultSerializableMessageTranslator messageTranslator;

	private transient WorkflowTargetTranslator targetTranslator;

	private transient Long initialJettyContextWaitMs;

	private transient MBeanServer interlokMBeanServer;

	private transient AdaptrisMessageFactory messageFactory;

	public WorkflowHealthCheckComponent() {
		this.setConsumer(new HttpRestWorkflowServicesConsumer());
		this.setMessageTranslator(new DefaultSerializableMessageTranslator());
		this.setMessageFactory(DefaultMessageFactory.getDefaultInstance());
	}

	@Override
	public void onAdaptrisMessage(AdaptrisMessage message) {
		try {
			this.healthPath(message);
			this.completeHealthCheck(message);
		} catch (ServiceException | OperationsException e) {
			log.error(message.getContent());
		}
	}

	private void healthPath(AdaptrisMessage message) {
		String pathValue = message.getMetadataValue(PATH_KEY);
		String[] pathItem = pathValue.split("/");
		adapterName = pathItem.length > 2 ? pathItem[2] : null;
		channelName = pathItem.length > 3 ? pathItem[3] : null;
		workflowName = pathItem.length > 4 ? pathItem[4] : null;
	}

	private String adapterName;

	private String channelName;

	private String workflowName;

	private ObjectName adapterObjectName() throws OperationsException, ReflectionException, MBeanException {
		if (adapterName != null) {
			return new ObjectName(ADAPTER_OBJ_TYPE + "id=" + adapterName);
		} else {
			Set<ObjectInstance> objectInstanceSet = this.getInterlokMBeanServer().queryMBeans(new ObjectName(ADAPTER_OBJ_TYPE_WILD), null);
			return new ObjectName(ADAPTER_OBJ_TYPE + "id=" + this.getInterlokMBeanServer().getAttribute(objectInstanceSet.iterator().next().getObjectName(), UNIQUE_ID).toString());
		}
	}

	private ObjectName channelObjectName(String channelName) throws OperationsException {
		return new ObjectName(CHANNEL_OBJ_TYPE + "adapter=" + adapterName + ",id=" + channelName);
	}

	private ObjectName workflowObjectName(String workflowName) throws OperationsException {
		return new ObjectName(WORKFLOW_OBJ_TYPE + "adapter=" + adapterName + ",channel=" + channelName + ",id=" + workflowName);
	}

	/*
	 * The following three methods could probably be inlined.
	 */

	private Boolean invalidAdapterId() throws ReflectionException, MBeanException, OperationsException {
		return adapterHealth() != null;
	}

	private Boolean invalidChannelId() throws ReflectionException, MBeanException, OperationsException {
		return channelHealth() != null;
	}

	private Boolean invalidWorkflowId() throws ReflectionException, MBeanException, OperationsException {
		return workflowHealth() != null;
	}

	private Object adapterInstanceAttribute(String attribute) throws OperationsException, ReflectionException, MBeanException {
		try {
			/*
			 * This method could probably be split into two; where one
			 * handle child attributes as a Set and another that
			 * returns a String in all other instances.
			 */
			return getInterlokMBeanServer().getAttribute(adapterObjectName(), attribute);
		} catch (InstanceNotFoundException e) {
			log.warn(e.toString());
			return null;
		}
	}

	private Set<ObjectName> getAdapterChildren() throws Exception {
		Object o = adapterInstanceAttribute(CHILDREN_ATTRIBUTE);
		if (o == null) {
			return new HashSet<>();
		}
		Set<ObjectName> children = (Set<ObjectName>)o;
		return children;
	}

	private Object channelInstanceAttribute(String attribute) throws OperationsException, ReflectionException, MBeanException {
		return getInterlokMBeanServer().getAttribute(channelObjectName(channelName), attribute);
	}

	private Set<ObjectName> getChannelChildren() throws Exception {
		Object o = channelInstanceAttribute(CHILDREN_ATTRIBUTE);
		Set<ObjectName> children = (Set<ObjectName>)o;
		return children;
	}

	private Object workflowInstanceAttribute(String attribute) throws OperationsException, ReflectionException, MBeanException {
		return getInterlokMBeanServer().getAttribute(workflowObjectName(workflowName), attribute);
	}

	/*
	 * The following three methods get a single health object.
	 */

	private AdapterHealth adapterHealth() throws ReflectionException, MBeanException, OperationsException {
		try {
			String messageAdapterId = adapterInstanceAttribute(UNIQUE_ID).toString();
			if (adapterName == null || adapterName.contains(messageAdapterId)) {
				String id = adapterInstanceAttribute(UNIQUE_ID).toString();
				String state = adapterInstanceAttribute(COMPONENT_STATE).toString();
				return new AdapterHealth(id, state);
			}
		} catch (InstanceNotFoundException e) {
			log.warn(e.toString());
		}
		return null;
	}

	private ChannelHealth channelHealth() throws OperationsException, ReflectionException, MBeanException {
		try {
			String messageChannelId = channelInstanceAttribute(UNIQUE_ID).toString();
			if (channelName.contains(messageChannelId)) {
				String id = channelInstanceAttribute(UNIQUE_ID).toString();
				String state = channelInstanceAttribute(COMPONENT_STATE).toString();
				return new ChannelHealth(id, state);
			}
		} catch (InstanceNotFoundException e) {
			log.warn(e.toString());
		}
		return null;
	}

	private WorkflowHealth workflowHealth() throws ReflectionException, MBeanException, OperationsException {
		try {
			if (channelInstanceAttribute(CHILDREN_ATTRIBUTE).toString().contains(workflowName)) {
				String id = workflowInstanceAttribute(UNIQUE_ID).toString();
				String state = workflowInstanceAttribute(COMPONENT_STATE).toString();
				return new WorkflowHealth(id, state);
			}
		} catch (InstanceNotFoundException e) {
			log.warn(e.toString());
		}
		return null;
	}

	/*
	 * The following three methods get the health for a given type,
	 * and any child types.
	 */

	private CommonHealth adapterHealthCheck() throws Exception {
		AdapterHealth adapter = adapterHealth();
		adapterName = adapterInstanceAttribute(UNIQUE_ID).toString();

		for (ObjectName adapterObject : getAdapterChildren()) {

			String channelId = adapterObject.getKeyProperty("id");
			String channelState = getInterlokMBeanServer().getAttribute(channelObjectName(channelId), COMPONENT_STATE).toString();
			ChannelHealth channel = new ChannelHealth(channelId, channelState);

			for (ObjectName channelObject : getChannelChildren()) {

				String workflowId = channelObject.getKeyProperty("id");
				String workflowState = getInterlokMBeanServer().getAttribute(workflowObjectName(workflowId), COMPONENT_STATE).toString();
				WorkflowHealth workflow = new WorkflowHealth(workflowId, workflowState);

				channel.addWorkflowHealth(workflow);
			}

			adapter.addChannelHealth(channel);
		}

		return adapter;
	}

	private void channelHealthCheck(AdaptrisMessage message) throws Exception {
		StringBuilder builder = new StringBuilder();
		builder.append(adapterHealth());
		if (invalidAdapterId()) {
			; // do nothing
		} else if (invalidChannelId()) {
			builder.append(channelHealth());
		} else {
			if (getAdapterChildren().size() > 0) {

				builder.append(channelHealth());

/*
 * FIXME This method needs rewriting to work in a similar manner to adapter/workflow above/below
 */

					Set<ObjectName> channelChildAttributes = getChannelChildren();
					for (ObjectName channelObject : channelChildAttributes) {

						builder.append("\t\tWorkflow: " + channelObject.getKeyProperty("id") + "\n");
						builder.append("\t\tWorkflow State: " + this.getInterlokMBeanServer().getAttribute(workflowObjectName(channelObject.getKeyProperty("id")), COMPONENT_STATE) + "\n");

					}

			}
		}
		message.setContent(builder.toString(), message.getContentEncoding());
		this.getConsumer().doResponse(message, message);
	}

	private CommonHealth workflowHealthCheck() throws OperationsException, ReflectionException, MBeanException {
		AdapterHealth adapter = adapterHealth();
		ChannelHealth channel = channelHealth();
		adapter.addChannelHealth(channel);
		channel.addWorkflowHealth(workflowHealth());
		return adapter;
	}

	private void completeHealthCheck(AdaptrisMessage message) throws ServiceException, OperationsException {
		try {
			CommonHealth healthResponse = null;
			/*
			 * Get a health object that we can serialize to JSON/XML as
			 * necessary. This allows the HTTP response to be set in
			 * just one place.
			 * TODO Get the channel method to do the same.
			 */
			if ((channelName == null)) {
				healthResponse = adapterHealthCheck();
			} else if (workflowName == null) {
				channelHealthCheck(message);
			} else {
				healthResponse = workflowHealthCheck();
			}
			log.debug("Successfully retrieved health status.");

			message.setContent(healthResponse.getJSON().toString(), message.getContentEncoding());
			this.getConsumer().doResponse(message, message);
		} catch (Exception e) {
			log.error("Failed to retrieve health status.", e);
			message.setContent(e.toString(), message.getContentEncoding());
			this.getConsumer().doErrorResponse(message, e);
		}
	}

	@Override
	public void init(Properties config) throws Exception {
		if (this.getInterlokMBeanServer() == null) {
			this.setInterlokMBeanServer(JmxHelper.findMBeanServer());
		}

		this.getConsumer().setAcceptedHttpMethods(ACCEPTED_FILTER);
		this.getConsumer().setConsumedUrlPath(PATH);
		this.getConsumer().setMessageListener(this);
		this.getConsumer().prepare();
		LifecycleHelper.init(getConsumer());
	}

	@Override
	public void start() throws Exception {
		new Thread(() -> {
			try {
				// Wait for the Jetty context to have been created.
				Thread.sleep(initialJettyContextWaitMs());
				LifecycleHelper.start(getConsumer());

				log.debug("Workflow REST services component started.");
			} catch (CoreException | InterruptedException e) {
				log.error("Could not start the Workflow REST services component.", e);
			}
		}).start();

	}

	@Override
	public void stop() throws Exception {
		LifecycleHelper.stop(getConsumer());
	}

	@Override
	public void destroy() throws Exception {
		LifecycleHelper.close(getConsumer());
	}

	@Override
	public String friendlyName() {
		return this.getClass().getSimpleName();
	}

	public WorkflowServicesConsumer getConsumer() {
		return consumer;
	}

	public void setConsumer(WorkflowServicesConsumer consumer) {
		this.consumer = consumer;
	}

	public WorkflowTargetTranslator getTargetTranslator() {
		return targetTranslator;
	}

	public void setTargetTranslator(WorkflowTargetTranslator targetTranslator) {
		this.targetTranslator = targetTranslator;
	}

	public DefaultSerializableMessageTranslator getMessageTranslator() {
		return messageTranslator;
	}

	public void setMessageTranslator(DefaultSerializableMessageTranslator messageTranslator) {
		this.messageTranslator = messageTranslator;
	}

	long initialJettyContextWaitMs() {
		return this.getInitialJettyContextWaitMs() == null ? DEFAULT_INITIAL_JETTY_CONTEXT_WAIT
				: this.getInitialJettyContextWaitMs();
	}

	public Long getInitialJettyContextWaitMs() {
		return initialJettyContextWaitMs;
	}

	public void setInitialJettyContextWaitMs(Long initialJettyContextWaitMs) {
		this.initialJettyContextWaitMs = initialJettyContextWaitMs;
	}

	public MBeanServer getInterlokMBeanServer() {
		return interlokMBeanServer;
	}

	public void setInterlokMBeanServer(MBeanServer interlokMBeanServer) {
		this.interlokMBeanServer = interlokMBeanServer;
	}

	public AdaptrisMessageFactory getMessageFactory() {
		return messageFactory;
	}

	public void setMessageFactory(AdaptrisMessageFactory messageFactory) {
		this.messageFactory = messageFactory;
	}
}
