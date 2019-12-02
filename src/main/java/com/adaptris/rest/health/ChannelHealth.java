package com.adaptris.rest.health;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ChannelHealth extends Health {
	private final List<WorkflowHealth> workflows = new ArrayList<>();

	public ChannelHealth(String id, String state) {
		super (id, state);
	}

	public void addWorkflowHealth(WorkflowHealth workflowHealth) {
		workflows.add(workflowHealth);
	}

	public List<WorkflowHealth> getWorkflows() {
		return workflows;
	}

	public JSONObject getJSON() throws JSONException {
		JSONObject json = super.getJSON();
		JSONArray workflowsJson = new JSONArray();
		for (WorkflowHealth workflow : workflows) {
			workflowsJson.put(workflow.getJSON());
		}
		json.put("Workflows", workflowsJson);
		return json;
	}
}
