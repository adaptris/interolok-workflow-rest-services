package com.adaptris.rest.health;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

public abstract class CommonHealth
{
	protected final String id;
	protected final String state;

	public CommonHealth(String id, String state) {
		this.id = id;
		this.state = state;
	}

	public String getId() {
		return id;
	}

	public String getState() {
		return state;
	}

	public JSONObject getJSON() throws JSONException {
		JSONObject json = new JSONObject();
		json.put("ID", id);
		json.put("State", state);
		return json;
	}
}
