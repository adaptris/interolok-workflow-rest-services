package com.adaptris.rest;

import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

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

	private static final String WORKFLOW_MANAGER = "com.adaptris.core.runtime.WorkflowManager";

	private static final String ACCEPTED_FILTER = "GET";
	
	private static final String PARENT_ID = "ParentId";
	
	private static final String UNIQUE_ID = "UniqueId";
	
	private static final String COMPONENT_STATE = "ComponentState";

	private transient WorkflowServicesConsumer consumer;

	private transient DefaultSerializableMessageTranslator messageTranslator;

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
		} catch (ServiceException e) {
			log.error(message.getContent());
		}

		// DIN - YOUR CODE GOES HERE.

		/**
		 * This method will look a bit like the one in WorkflowServicesComponent.
		 * 
		 * You'll notice I modified the PATH and ACCEPTED_FILTER above so that this
		 * method will get triggered when a user fires a GET request to
		 * "http://host:port/workflow-health-check/..."
		 * 
		 * I would say if the user only gives you an adapter, so basically a url like
		 * this; "http://host:port/workflow-health-check/myAdapter"
		 * 
		 * Then you could prepare a response that shows the status (started, stopped
		 * etc) for all workflows in all channels.
		 * 
		 * If you get an adapter with a channel, so something like this;
		 * "http://host:port/workflow-health-check/myAdapter"/myChannel"
		 * 
		 * Then you would prepare a response that shows that status all of the workflows
		 * in that channel.
		 * 
		 * If you get an adapter with a channel and a workflow, so something like this;
		 * "http://host:port/workflow-health-check/myAdapter"/myChannel/myWorkflow"
		 * 
		 * Then prepare a response that shows the status for that single workflow.
		 * 
		 * Where do you get the statuses for each of the adapter, channels and
		 * workflows? JMX of course. If you start an instance of Interlok that has at
		 * least one configured workflow, then open up JConsole, connect to the instance
		 * and you'll be able to see all of the MBeans available. Shout if you need a
		 * quick guide around this bit.
		 * 
		 * You can use this.getInterlokMBeanServer(), to get access to the MBeans.
		 * 
		 * How do you send your response back to the user? Simply write your response to
		 * the message payload and then call this method;
		 * this.getConsumer().doResponse(message, message);
		 * 
		 * What happens if you want to send an error response back to the user; for
		 * example they have given a channel that doesn't exist? Simply write your
		 * response to the message payload and then call this method;
		 * this.getConsumer().doErrorResponse(message, new Exception("Your channel
		 * doesn't exist"));
		 * 
		 */
	}
	
	
	private void completeHealthCheck(AdaptrisMessage message) throws ServiceException{
		
		try {
			
			/* TODO remove hardcoded path and take path_key (I.E the URI) parameter instead
			 * if null returns this else returns based on arg given */
			Set<ObjectInstance> channelObjInstSet = this.getInterlokMBeanServer().queryMBeans(new ObjectName(CHANNEL_OBJ_NAME), null);
			Iterator<ObjectInstance> channelIterator = channelObjInstSet.iterator();
			StringBuilder builder = new StringBuilder();
			if(!channelIterator.hasNext()) {
				Set<ObjectInstance> adapterObjInstSet = this.getInterlokMBeanServer().queryMBeans(new ObjectName(ADAPTER_OBJ_NAME), null);
				builder.append("Adapter: ");
				builder.append(adapterObjInstSet.iterator().next().getObjectName().getKeyProperty("id"));
				builder.append("\nNo channels or workflows were found for this adapter.");				
				message.setContent(builder.toString(), message.getContentEncoding());
				this.getConsumer().doResponse(message, message);
			}
			else {
			while(channelIterator.hasNext()) {
				Set<ObjectInstance> workflowObjInstSet = this.getInterlokMBeanServer().queryMBeans(new ObjectName(WORKFLOW_OBJ_NAME), null);
				Iterator<ObjectInstance> workflowIterator = workflowObjInstSet.iterator();
				ObjectInstance channelInstance = channelIterator.next();
				if (!(this.getInterlokMBeanServer().getAttribute(channelInstance.getObjectName(), "Children").toString().equalsIgnoreCase("[]"))) {
					
					builder.append("Adapter: " + this.getInterlokMBeanServer().getAttribute(channelInstance.getObjectName(), PARENT_ID).toString() + "\n");
					builder.append("\t" + "Channel: " + this.getInterlokMBeanServer().getAttribute(channelInstance.getObjectName(), UNIQUE_ID).toString() + "\n");
					builder.append("\t" + "Channel State: " + this.getInterlokMBeanServer().getAttribute(channelInstance.getObjectName(), COMPONENT_STATE).toString() + "\n");
					while (workflowIterator.hasNext()) {
						ObjectInstance workflowInstance = workflowIterator.next();
						if (this.getInterlokMBeanServer().getAttribute(channelInstance.getObjectName(), UNIQUE_ID).toString().equalsIgnoreCase(this.getInterlokMBeanServer().getAttribute(workflowInstance.getObjectName(), PARENT_ID).toString())) {
					builder.append("\t\t" + "Workflow: " + this.getInterlokMBeanServer().getAttribute(workflowInstance.getObjectName(), UNIQUE_ID).toString() + "\n");
					builder.append("\t\t\t" + "Workflow State: " + this.getInterlokMBeanServer().getAttribute(workflowInstance.getObjectName(), COMPONENT_STATE).toString() + "\n");
						}
					}
											
				}
				else {
							builder.append("Adapter: ");
							builder.append(this.getInterlokMBeanServer().getAttribute(channelInstance.getObjectName(), PARENT_ID).toString());
							builder.append("\n");
							builder.append("\t" + "Channel: ");
							builder.append(this.getInterlokMBeanServer().getAttribute(channelInstance.getObjectName(), UNIQUE_ID).toString());
							builder.append("\n");
							builder.append("\t" + "Channel State: ");
							builder.append(this.getInterlokMBeanServer().getAttribute(channelInstance.getObjectName(), COMPONENT_STATE).toString());
							builder.append("\n");
							builder.append("\tNo workflows were found for this channel. \n");			
				}
				
			}
			log.debug("Successfully retrieved health status.");
			
			message.setContent(builder.toString(), message.getContentEncoding());				
			this.getConsumer().doResponse(message, message);
			}			
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
