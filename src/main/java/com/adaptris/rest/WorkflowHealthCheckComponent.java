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
		} catch (ServiceException e) {
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

	private String adapterName;  /* the requested adapter; if null get all adapters */
	private String channelName;  /* the requested channel; if null get all channels for the adapter */
	private String workflowName; /* the requested workflow; if null get all workflows for the channel */

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

	private Boolean invalidAdapterId() {
		return adapterHealth() != null;
	}

	private Boolean invalidChannelId() {
		return channelHealth() != null;
	}

	private Boolean invalidWorkflowId() {
		return workflowHealth() != null;
	}

	private String getAdapterAttribute(String attribute) {
		try {
			return (String)getInterlokMBeanServer().getAttribute(adapterObjectName(), attribute);
		} catch (Exception e) {
			log.error("Could not get adapter attribute [" + attribute + "]", e);
			return null;
		}
	}

	private Set<ObjectName> getAdapterChildren() {
		try {
			return (Set<ObjectName>)getInterlokMBeanServer().getAttribute(adapterObjectName(), CHILDREN_ATTRIBUTE);
		} catch (Exception e) {
			log.error("Could not get adapter children", e);
			return new HashSet<>(); // is it better to return an empty set than null?
		}
	}

	/*
	 * TODO The following methods to get channel attributes/children should match the above for adapters.
	 */

	private Object channelInstanceAttribute(String attribute) throws OperationsException, ReflectionException, MBeanException {
		return getInterlokMBeanServer().getAttribute(channelObjectName(channelName), attribute);
	}

	private Set<ObjectName> getChannelChildren() throws Exception {
		return (Set<ObjectName>)channelInstanceAttribute(CHILDREN_ATTRIBUTE);
	}

	private String workflowInstanceAttribute(String attribute) {
		try {
			return (String)getInterlokMBeanServer().getAttribute(workflowObjectName(workflowName), attribute);
		} catch (Exception e) {
			log.error("Could not get workflow attribute [" + attribute + "]", e);
			return null;
		}
	}

	/*
	 * The following three methods get a single health object.
	 */

	private AdapterHealth adapterHealth() {
		String messageAdapterId = getAdapterAttribute(UNIQUE_ID);
		if (adapterName == null || adapterName.contains(messageAdapterId)) {
			String id = getAdapterAttribute(UNIQUE_ID);
			String state = getAdapterAttribute(COMPONENT_STATE);
			return new AdapterHealth(id, state);
		}
		return null;
	}

	private ChannelHealth channelHealth() {
		try {
			String messageChannelId = channelInstanceAttribute(UNIQUE_ID).toString();
			if (channelName.contains(messageChannelId)) {
				String id = channelInstanceAttribute(UNIQUE_ID).toString();
				String state = channelInstanceAttribute(COMPONENT_STATE).toString();
				return new ChannelHealth(id, state);
			}
		} catch (Exception e) {
			log.warn(e.toString());
		}
		return null;
	}

	private WorkflowHealth workflowHealth() {
		try {
			if (getChannelChildren().contains(workflowName)) {
				String id = workflowInstanceAttribute(UNIQUE_ID);
				String state = workflowInstanceAttribute(COMPONENT_STATE);
				return new WorkflowHealth(id, state);
			}
		} catch (Exception e) {
			log.warn(e.toString());
		}
		return null;
	}

	/*
	 * The following three methods get the health for a given type,
	 * and any child types.
	 */

	private CommonHealth adapterHealthCheck() throws Exception {
		AdapterHealth adapter = adapterHealth(); // get the adapter health
		adapterName = getAdapterAttribute(UNIQUE_ID);

		for (ObjectName adapterObject : getAdapterChildren()) {

			String channelId = adapterObject.getKeyProperty("id");
			String channelState = channelInstanceAttribute(COMPONENT_STATE).toString();
			ChannelHealth channel = new ChannelHealth(channelId, channelState);

			for (ObjectName channelObject : getChannelChildren()) {

				String workflowId = channelObject.getKeyProperty("id");
				String workflowState = workflowInstanceAttribute(COMPONENT_STATE);
				WorkflowHealth workflow = new WorkflowHealth(workflowId, workflowState);

				channel.addWorkflowHealth(workflow); // for each workflow, add its health
			}

			adapter.addChannelHealth(channel); // for each channel, add its health
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
 *
 * (I may have broken this method.)
 */

					Set<ObjectName> channelChildAttributes = getChannelChildren();
					for (ObjectName channelObject : channelChildAttributes) {

						builder.append("\t\tWorkflow: " + channelObject.getKeyProperty("id") + "\n");
						builder.append("\t\tWorkflow State: " + workflowInstanceAttribute(COMPONENT_STATE) + "\n");

					}

			}
		}
		message.setContent(builder.toString(), message.getContentEncoding());
		this.getConsumer().doResponse(message, message);
	}

	private CommonHealth workflowHealthCheck() {
		AdapterHealth adapter = adapterHealth(); // get the health of the parent adapter
		ChannelHealth channel = channelHealth(); // get the health of the parent channel
		adapter.addChannelHealth(channel);
		channel.addWorkflowHealth(workflowHealth());
		return adapter;
	}

	private void completeHealthCheck(AdaptrisMessage message) throws ServiceException {
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
	public void start() {
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
	public void stop() {
		LifecycleHelper.stop(getConsumer());
	}

	@Override
	public void destroy() {
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
