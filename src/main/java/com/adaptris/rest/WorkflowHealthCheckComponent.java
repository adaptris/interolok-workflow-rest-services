package com.adaptris.rest;

import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.OperationsException;
import javax.management.ReflectionException;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.AdaptrisMessageFactory;
import com.adaptris.core.AdaptrisMessageListener;
import com.adaptris.core.CoreException;
import com.adaptris.core.DefaultMessageFactory;
import com.adaptris.core.DefaultSerializableMessageTranslator;
import com.adaptris.core.ServiceException;
import com.adaptris.core.management.MgmtComponentImpl;
import com.adaptris.core.util.JmxHelper;
import com.adaptris.core.util.LifecycleHelper;

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
		if(adapterName != null) {
			return new ObjectName(ADAPTER_OBJ_TYPE + "id=" + adapterName);
		}
		else {
			Set<ObjectInstance> objectInstanceSet = this.getInterlokMBeanServer().queryMBeans(new ObjectName(ADAPTER_OBJ_TYPE_WILD), null);
			return new ObjectName(ADAPTER_OBJ_TYPE + "id=" + this.getInterlokMBeanServer().getAttribute(objectInstanceSet.iterator().next().getObjectName(), UNIQUE_ID).toString());
		}
	}

	private ObjectName channelObjectName() throws OperationsException {
		return new ObjectName(CHANNEL_OBJ_TYPE + "adapter=" + adapterName + ",id=" + channelName);
	}

	private ObjectName workflowObjectName() throws OperationsException {
		return new ObjectName(WORKFLOW_OBJ_TYPE + "adapter=" + adapterName + ",channel=" + channelName + ",id=" + workflowName);
	}

	private Boolean childFreeAdapter() throws ReflectionException, MBeanException, OperationsException {
		return adapterInstanceAttribute(CHILDREN_ATTRIBUTE).toString().equalsIgnoreCase("[]");
	}

	private Boolean childFreeChannel() throws ReflectionException, MBeanException, OperationsException {
		return channelInstanceAttribute(CHILDREN_ATTRIBUTE).toString().equalsIgnoreCase("[]");
	}

	private Boolean invalidAdapterId() throws ReflectionException, MBeanException, OperationsException {
		return adapterHealth().equalsIgnoreCase(INVALID_ADAPTER_ID);
	}

	private Boolean invalidChannelId() throws ReflectionException, MBeanException, OperationsException {
		return channelHealth().equalsIgnoreCase(INVALID_CHANNEL_ID);
	}

	private Boolean invalidWorkflowId() throws ReflectionException, MBeanException, OperationsException {
		return workflowHealth().equalsIgnoreCase(INVALID_WORKFLOW_ID);
	}

	private Object adapterInstanceAttribute(String attribute)	throws OperationsException, ReflectionException, MBeanException {
		try { 
			return this.getInterlokMBeanServer().getAttribute(adapterObjectName(), attribute);
		} catch (InstanceNotFoundException e) {
			log.warn(e.toString());
			return INVALID_ADAPTER_ID;
		}
	}

	private Object channelInstanceAttribute(String attribute) throws OperationsException, ReflectionException, MBeanException {
		return this.getInterlokMBeanServer().getAttribute(channelObjectName(), attribute);
	}

	private Object workflowInstanceAttribute(String attribute)	throws OperationsException, ReflectionException, MBeanException {
		return this.getInterlokMBeanServer().getAttribute(workflowObjectName(), attribute);
	}

	private String adapterHealth() throws ReflectionException, MBeanException, OperationsException {

		try {
			String messageAdapterId = adapterInstanceAttribute(UNIQUE_ID).toString();
			String adapterId = ("Adapter: " + adapterInstanceAttribute(UNIQUE_ID) + "\n").toString();
			String adapterState = ("Adapter State: " + adapterInstanceAttribute(COMPONENT_STATE) + "\n").toString();
			if (adapterName == null || adapterName.contains(messageAdapterId)) {
				return adapterId + adapterState;
			}else {
				return INVALID_ADAPTER_ID;
			}
		} catch (InstanceNotFoundException e) {
			log.warn(e.toString());
			return INVALID_ADAPTER_ID;
		}
	}

	private String channelHealth() throws OperationsException, ReflectionException, MBeanException {
		try {

			String messageChannelId = channelInstanceAttribute(UNIQUE_ID).toString();
			String channelId = ("\tChannel: " + channelInstanceAttribute(UNIQUE_ID) + "\n").toString();
			String channelState = ("\tChannel State: " + channelInstanceAttribute(COMPONENT_STATE) + "\n").toString();

			if (channelName.contains(messageChannelId)) {
				return channelId + channelState;
			} else {
				return INVALID_CHANNEL_ID;
			}
		} catch (InstanceNotFoundException e) {
			log.warn(e.toString());
			return INVALID_CHANNEL_ID;
		}

	}

	private String workflowHealth() throws ReflectionException, MBeanException, OperationsException {
		try {

			String workflowId = ("\t\tWorkflow: " + workflowInstanceAttribute(UNIQUE_ID) + "\n").toString();
			String workflowState = ("\t\tWorkflow State: " + workflowInstanceAttribute(COMPONENT_STATE) + "\n").toString();

			if(channelInstanceAttribute(CHILDREN_ATTRIBUTE).toString().contains(workflowName.toString())) {
				return workflowId + workflowState;
			} else {
				return INVALID_WORKFLOW_ID;
			}
		} catch (InstanceNotFoundException e) {
			log.warn(e.toString());
			return INVALID_WORKFLOW_ID;
		}

	}

	private void adapterHealthCheck(AdaptrisMessage message) throws ServiceException, OperationsException, ReflectionException, MBeanException {
		StringBuilder builder = new StringBuilder();
		builder.append(adapterHealth());
		if(adapterInstanceAttribute(CHILDREN_ATTRIBUTE) != INVALID_ADAPTER_ID) {
			if(!childFreeAdapter()) {
				adapterName = adapterInstanceAttribute(UNIQUE_ID).toString();
				TreeSet<ObjectName> adapterChildAttributes = (TreeSet<ObjectName>) (adapterInstanceAttribute(CHILDREN_ATTRIBUTE));
				for (ObjectName adapterObject : adapterChildAttributes) {
					if(!(this.getInterlokMBeanServer().getAttribute(new ObjectName (CHANNEL_OBJ_TYPE + "adapter=" + adapterName + ",id=" + adapterObject.getKeyProperty("id")), CHILDREN_ATTRIBUTE)).toString().contains("[]")) {
						builder.append("\tChannel: " + adapterObject.getKeyProperty("id") + "\n");
						builder.append("\tChannel State: " + this.getInterlokMBeanServer().getAttribute(new ObjectName(CHANNEL_OBJ_TYPE + "adapter=" + adapterName + ",id=" + adapterObject.getKeyProperty("id")), COMPONENT_STATE) + "\n");
						TreeSet<ObjectName> channelChildAttributes = (TreeSet<ObjectName>) this.getInterlokMBeanServer().getAttribute((new ObjectName(CHANNEL_OBJ_TYPE + "adapter=" + adapterName + ",id=" + adapterObject.getKeyProperty("id"))), CHILDREN_ATTRIBUTE);
						for (ObjectName channelObject : channelChildAttributes) {				
							builder.append("\t\tWorkflow: " + channelObject.getKeyProperty("id") + "\n");
							builder.append("\t\tWorkflow State: " + this.getInterlokMBeanServer().getAttribute(new ObjectName(WORKFLOW_OBJ_TYPE + "adapter=" + adapterName + ",channel=" + adapterObject.getKeyProperty("id") + ",id=" + channelObject.getKeyProperty("id")), COMPONENT_STATE) + "\n");
						}
					}
					else {
						builder.append("\tChannel: " + adapterObject.getKeyProperty("id") + "\n");
						builder.append("\tChannel State: " + this.getInterlokMBeanServer().getAttribute(new ObjectName(CHANNEL_OBJ_TYPE + "adapter=" + adapterName + ",id=" + adapterObject.getKeyProperty("id")), COMPONENT_STATE) + "\n");
						builder.append("\tNo workflows were found for this channel. \n");
					}
				}

			}
			else {
				builder.append("No channels or workflows were found for this channel. \n");
			}
		}
		else {
			invalidAdapterId();
		}


		message.setContent(builder.toString(), message.getContentEncoding());
		this.getConsumer().doResponse(message, message);
	}

	private void channelHealthCheck(AdaptrisMessage message) throws ServiceException, OperationsException, ReflectionException, MBeanException {

		StringBuilder builder = new StringBuilder();

		if (invalidAdapterId()) {
			builder.append(adapterHealth());
		} else if (invalidChannelId()) {
			builder.append(adapterHealth());
			builder.append(channelHealth());
		} else {
			if (!childFreeAdapter()) {
				builder.append(adapterHealth());
				if (!childFreeChannel()) {
					builder.append(channelHealth());
					TreeSet<ObjectName> channelChildAttributes = (TreeSet<ObjectName>) (channelInstanceAttribute(CHILDREN_ATTRIBUTE));
					for (ObjectName channelObject : channelChildAttributes) {
						builder.append("\t\tWorkflow: " + channelObject.getKeyProperty("id") + "\n");
						builder.append("\t\tWorkflow State: " + this.getInterlokMBeanServer().getAttribute(new ObjectName(WORKFLOW_OBJ_TYPE + "adapter=" + adapterName + ",channel=" + channelName + ",id=" + channelObject.getKeyProperty("id")), COMPONENT_STATE) + "\n");
					}
				} else {
					builder.append(channelHealth());
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

		builder.append(adapterHealth());
		builder.append(channelHealth());
		builder.append(workflowHealth());

		message.setContent(builder.toString(), message.getContentEncoding());
		this.getConsumer().doResponse(message, message);
	}

	private void completeHealthCheck(AdaptrisMessage message) throws ServiceException, OperationsException {

		try {
			if ((channelName == null)) {
				adapterHealthCheck(message);

			} else if (workflowName == null) {
				channelHealthCheck(message);
			} else {
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
	}

	@Override
	public void start() throws Exception {
	  WorkflowHealthCheckComponent instance = this;
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
				  if (getInterlokMBeanServer() == null)
		            setInterlokMBeanServer(JmxHelper.findMBeanServer());

    		        getConsumer().setAcceptedHttpMethods(ACCEPTED_FILTER);
    		        getConsumer().setConsumedUrlPath(PATH);
    		        getConsumer().setMessageListener(instance);
    		        getConsumer().prepare();
    		        LifecycleHelper.init(getConsumer());
					LifecycleHelper.start(getConsumer());

					log.debug("Workflow REST services component started.");
				} catch (CoreException e) {
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
