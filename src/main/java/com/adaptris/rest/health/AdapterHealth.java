package com.adaptris.rest.health;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class AdapterHealth extends Health {
	private final List<ChannelHealth> channels = new ArrayList<>();

	public AdapterHealth(String id, String state) {
		super (id, state);
	}

	public void addChannelHealth(ChannelHealth channelHealth) {
		channels.add(channelHealth);
	}

	public List<ChannelHealth> getChannels() {
		return channels;
	}

	public JSONObject getJSON() throws JSONException {
		JSONObject json = super.getJSON();
		JSONArray channelsJson = new JSONArray();
		for (ChannelHealth channel : channels) {
			channelsJson.put(channel.getJSON());
		}
		json.put("Channels", channelsJson);
		return json;
	}
}
