package com.adaptris.rest;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import javax.management.*;

import com.adaptris.core.*;
import com.adaptris.core.management.MgmtComponentImpl;
import com.adaptris.core.util.JmxHelper;
import com.adaptris.core.util.LifecycleHelper;

public class WorkflowHealthCheckComponent extends MgmtComponentImpl implements AdaptrisMessageListener {

	private static final Long DEFAULT_INITIAL_JETTY_CONTEXT_WAIT = (30l * 1000l);

	private static final String PATH = "/workflow-health-check/*";

	private static final String ADAPTER_OBJ_NAME = "*com.adaptris:type=Adapter,*";

	private static final String CHANNEL_OBJ_NAME = "*com.adaptris:type=Channel,*";

	private static final String WORKFLOW_OBJ_NAME = "*com.adaptris:type=Workflow,*";

	private static final String CHANNEL_OBJ_TYPE = "com.adaptris:type=Channel,";

	private static final String WORKFLOW_OBJ_TYPE = "com.adaptris:type=Workflow,";

	private static final String ACCEPTED_FILTER = "GET";

	private static final String PARENT_ID = "ParentId";

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

			this.completeHealthCheck(message);
		} catch (ServiceException | OperationsException e) {
			log.error(message.getContent());
		}

	}

	private String[] healthPath(AdaptrisMessage message) {

		String pathValue = message.getMetadataValue(PATH_KEY);
		String[] pathItem = pathValue.split("/");

		return pathItem;
	}

	private ObjectName channelObjectName(AdaptrisMessage message) throws OperationsException {
		return new ObjectName(CHANNEL_OBJ_TYPE + "adapter=" + healthPath(message)[2] + ",id=" + healthPath(message)[3]);
	}

	private ObjectName workflowObjectName(AdaptrisMessage message) throws OperationsException {
		return new ObjectName(WORKFLOW_OBJ_TYPE + "adapter=" + healthPath(message)[2] + ",channel="	+ healthPath(message)[3] + ",id=" + healthPath(message)[4]);
	}

	private Boolean childFreeAdapter() throws ReflectionException, MBeanException, OperationsException {
		return this.getInterlokMBeanServer().getAttribute(adapterInstance().getObjectName(), CHILDREN_ATTRIBUTE).toString().equalsIgnoreCase("[]");
	}

	private Boolean childFreeChannel(AdaptrisMessage message) throws ReflectionException, MBeanException, OperationsException {
		return channelInstanceAttribute(message, CHILDREN_ATTRIBUTE).toString().equalsIgnoreCase("[]");
	}

	private Boolean invalidAdapterId(AdaptrisMessage message) throws ReflectionException, MBeanException, OperationsException {
		return adapterHealth(message).equalsIgnoreCase(INVALID_ADAPTER_ID);
	}

	private Boolean invalidChannelId(AdaptrisMessage message) throws ReflectionException, MBeanException, OperationsException {
		return channelHealth(message).equalsIgnoreCase(INVALID_CHANNEL_ID);
	}

	private Boolean invalidWorkflowId(AdaptrisMessage message) throws ReflectionException, MBeanException, OperationsException {
		return workflowHealth(message).equalsIgnoreCase(INVALID_WORKFLOW_ID);
	}

	private Object workflowInstanceAttribute(AdaptrisMessage message, String attribute)	throws OperationsException, ReflectionException, MBeanException {
		return this.getInterlokMBeanServer().getAttribute(workflowObjectName(message), attribute);
	}

	private Object channelInstanceAttribute(AdaptrisMessage message, String attribute) throws OperationsException, ReflectionException, MBeanException {
		return this.getInterlokMBeanServer().getAttribute(channelObjectName(message), attribute);
	}

	private ObjectInstance adapterInstance() throws OperationsException {

		Set<ObjectInstance> adapterObjInstSet = this.getInterlokMBeanServer().queryMBeans(new ObjectName(ADAPTER_OBJ_NAME), null);
		Iterator<ObjectInstance> adapterIterator = adapterObjInstSet.iterator();
		ObjectInstance adapterObject = adapterIterator.next();

		return adapterObject;
	}

	private String adapterHealth(AdaptrisMessage message) throws ReflectionException, MBeanException, OperationsException {

		String messageAdapterId = this.getInterlokMBeanServer().getAttribute(adapterInstance().getObjectName(), UNIQUE_ID).toString();
		String[] adapterIdPassed = healthPath(message);
		String adapterId = ("Adapter: "	+ this.getInterlokMBeanServer().getAttribute(adapterInstance().getObjectName(), UNIQUE_ID) + "\n").toString();
		String adapterState = ("Adapter State: " + this.getInterlokMBeanServer().getAttribute(adapterInstance().getObjectName(), COMPONENT_STATE) + "\n").toString();

		if ((Arrays.toString(adapterIdPassed).contains(messageAdapterId)) || (adapterIdPassed.length <= 2)) {
			return adapterId + adapterState;
		} else {
			return INVALID_ADAPTER_ID;
		}
	}

	private String workflowHealth(AdaptrisMessage message) throws ReflectionException, MBeanException, OperationsException {
		try {

			String workflowId = ("\t\tWorkflow: " + workflowInstanceAttribute(message, UNIQUE_ID) + "\n").toString();
			String workflowState = ("\t\tWorkflow State: " + workflowInstanceAttribute(message, COMPONENT_STATE) + "\n").toString();

			if((healthPath(message).length == 5) && (channelInstanceAttribute(message, CHILDREN_ATTRIBUTE).toString().contains(workflowObjectName(message).toString()))) {
				return workflowId + workflowState;
			} else {
				return INVALID_WORKFLOW_ID;
			}
		} catch (InstanceNotFoundException e) {
			log.warn(e.toString());
			return INVALID_WORKFLOW_ID;
		}

	}

	private String channelHealth(AdaptrisMessage message) throws OperationsException, ReflectionException, MBeanException {
		try {

			String messageChannelId = channelInstanceAttribute(message, UNIQUE_ID).toString();
			String[] channelIdPassed = healthPath(message);
			String channelId = ("\tChannel: " + channelInstanceAttribute(message, UNIQUE_ID) + "\n").toString();
			String channelState = ("\tChannel State: " + channelInstanceAttribute(message, COMPONENT_STATE) + "\n").toString();

			if ((healthPath(message).length <= 5) && (Arrays.toString(channelIdPassed).contains(messageChannelId))) {
				return channelId + channelState;
			} else {
				return INVALID_CHANNEL_ID;
			}
		} catch (InstanceNotFoundException e) {
			log.warn(e.toString());
			return INVALID_CHANNEL_ID;
		}

	}

	private void adapterHealthCheck(AdaptrisMessage message) throws ServiceException, OperationsException, ReflectionException, MBeanException {

		Set<ObjectInstance> channelObjInstSet = this.getInterlokMBeanServer().queryMBeans(new ObjectName(CHANNEL_OBJ_NAME), null);
		Iterator<ObjectInstance> channelIterator = channelObjInstSet.iterator();
		StringBuilder builder = new StringBuilder();
		if(invalidAdapterId(message)) {
			builder.append(adapterHealth(message));
		}
		else {
			if(childFreeAdapter()) {
				builder.append(adapterHealth(message));
				builder.append("\nNo channels or workflows were found for this adapter.");
				message.setContent(builder.toString(), message.getContentEncoding());
				this.getConsumer().doResponse(message, message);
			}
			else {
				while (channelIterator.hasNext()) {
					Set<ObjectInstance> workflowObjInstSet = this.getInterlokMBeanServer().queryMBeans(new ObjectName(WORKFLOW_OBJ_NAME), null);
					Iterator<ObjectInstance> workflowIterator = workflowObjInstSet.iterator();
					ObjectInstance channelInstance = channelIterator.next();
					if (!childFreeAdapter()) {
						builder.append(adapterHealth(message));
						builder.append("\tChannel: " + this.getInterlokMBeanServer().getAttribute(channelInstance.getObjectName(), UNIQUE_ID).toString() + "\n");
						builder.append("\tChannel State: " + this.getInterlokMBeanServer().getAttribute(channelInstance.getObjectName(), COMPONENT_STATE).toString() + "\n");
						while (workflowIterator.hasNext()) {
							ObjectInstance workflowInstance = workflowIterator.next();
							if (!this.getInterlokMBeanServer().getAttribute(channelInstance.getObjectName(), UNIQUE_ID).toString().equalsIgnoreCase(this.getInterlokMBeanServer().getAttribute(workflowInstance.getObjectName(), PARENT_ID).toString())) continue;
							builder.append("\t\tWorkflow: " + this.getInterlokMBeanServer().getAttribute(workflowInstance.getObjectName(), UNIQUE_ID).toString() + "\n");
							builder.append("\t\tWorkflow State: " + this.getInterlokMBeanServer().getAttribute(workflowInstance.getObjectName(), COMPONENT_STATE).toString() + "\n");
						}
					}else {
						builder.append(adapterHealth(message));
						builder.append("\tChannel: " + this.getInterlokMBeanServer().getAttribute(channelInstance.getObjectName(), UNIQUE_ID).toString() + "\n");
						builder.append("\tChannel State: " + this.getInterlokMBeanServer().getAttribute(channelInstance.getObjectName(), COMPONENT_STATE).toString() + "\n");
						builder.append("\tNo workflows were found for this channel.\n");
					}
				}
			}
		}

		message.setContent(builder.toString(), message.getContentEncoding());
		this.getConsumer().doResponse(message, message);
	}

	private void channelHealthCheck(AdaptrisMessage message) throws ServiceException, OperationsException, ReflectionException, MBeanException {

		StringBuilder builder = new StringBuilder();

		if (invalidAdapterId(message)) {
			builder.append(adapterHealth(message));
		} else if (invalidChannelId(message)) {
			builder.append(adapterHealth(message));
			builder.append(channelHealth(message));
		} else {
			if (!childFreeAdapter()) {
				builder.append(adapterHealth(message));
				if (!childFreeChannel(message)) {
					builder.append(channelHealth(message));
					TreeSet<ObjectName> channelChildAttributes = (TreeSet<ObjectName>) (channelInstanceAttribute(message, CHILDREN_ATTRIBUTE));
					for (ObjectName channelObject : channelChildAttributes) {
						builder.append("\t\tWorkflow: " + channelObject.getKeyProperty("id") + "\n");
					}
				} else {
					builder.append(channelHealth(message));
					builder.append("\tNo workflows were found for this channel. \n");
				}

			} else {
				builder.append("This adapter has no channels or workflows");
			}
		}
		message.setContent(builder.toString(), message.getContentEncoding());
		this.getConsumer().doResponse(message, message);
	}

	private void workflowHealthCheck(AdaptrisMessage message) throws ServiceException, OperationsException, ReflectionException, MBeanException {

		StringBuilder builder = new StringBuilder();

		if (invalidAdapterId(message)) {
			builder.append(adapterHealth(message));
		} else if (invalidChannelId(message)) {
			builder.append(adapterHealth(message));
			builder.append(channelHealth(message));
		} else if (invalidWorkflowId(message)) {
			builder.append(adapterHealth(message));
			builder.append(channelHealth(message));
			builder.append(workflowHealth(message));
		} else {
			if (!childFreeAdapter()) {
				builder.append(adapterHealth(message));
				builder.append(channelHealth(message));
				builder.append(workflowHealth(message));
			} else {
				builder.append("This adapter has no workflows.");
			}

		}
		message.setContent(builder.toString(), message.getContentEncoding());
		this.getConsumer().doResponse(message, message);
	}

	private void completeHealthCheck(AdaptrisMessage message) throws ServiceException, OperationsException {

		try {
			if (healthPath(message).length <= 3) {
				adapterHealthCheck(message);
			} else if (healthPath(message).length == 4) {
				channelHealthCheck(message);
			} else if (healthPath(message).length == 5) {
				workflowHealthCheck(message);
			}
			log.debug("Successfully retrieved health status.");

		} catch (MalformedObjectNameException | MBeanException | AttributeNotFoundException | InstanceNotFoundException
				| ReflectionException e) {
			StringBuilder builder = new StringBuilder();
			log.error("Failed to retrieve health status.", e);
			builder.append(e.toString());
			message.setContent(builder.toString(), message.getContentEncoding());
			this.getConsumer().doErrorResponse(message, e);
		}
	}

	@Override
	public void init(Properties config) throws Exception {
		if (this.getInterlokMBeanServer() == null)
			this.setInterlokMBeanServer(JmxHelper.findMBeanServer());

		this.getConsumer().setAcceptedHttpMethods(ACCEPTED_FILTER);
		this.getConsumer().setConsumedUrlPath(PATH);
		this.getConsumer().setMessageListener(this);
		this.getConsumer().prepare();
		LifecycleHelper.init(getConsumer());
	}

	@Override
	public void start() throws Exception {
		new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					// Wait for the Jetty context to have been created.
					Thread.sleep(initialJettyContextWaitMs());
					LifecycleHelper.start(getConsumer());

					log.debug("Workflow REST services component started.");
				} catch (CoreException | InterruptedException e) {
					log.error("Could not start the Workflow REST services component.", e);
				}
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
