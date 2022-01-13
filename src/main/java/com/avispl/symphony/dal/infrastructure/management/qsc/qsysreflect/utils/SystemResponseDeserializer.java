/*
 * Copyright (c) 2022 AVI-SPL, Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.infrastructure.management.qsc.qsysreflect.utils;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import com.avispl.symphony.dal.infrastructure.management.qsc.qsysreflect.dto.SystemResponse;

/**
 * Custom Deserializer class for SystemResponse
 *
 * @author Duy Nguyen
 * @version 1.0.0
 * @since 1.0.0
 */
public class SystemResponseDeserializer extends StdDeserializer<SystemResponse> {

	/**
	 * SystemResponseDeserializer no arg constructor
	 */
	public SystemResponseDeserializer() {
		this(null);
	}

	/**
	 * SystemResponseDeserializer with arg constructor
	 * @param vc Class name
	 */
	protected SystemResponseDeserializer(Class<?> vc) {
		super(vc);
	}

	@Override
	public SystemResponse deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
		JsonNode jsonNode = jsonParser.getCodec().readTree(jsonParser);
		SystemResponse systemResponse = new SystemResponse();
		JsonNode idNode = jsonNode.get("id");
		if (idNode != null) {
			systemResponse.setId(jsonNode.get("id").asInt());
		}
		JsonNode codeNode = jsonNode.get("code");
		if (codeNode != null) {
			systemResponse.setCode(codeNode.asText());
		}
		JsonNode nameNode = jsonNode.get("name");
		if (nameNode != null) {
			systemResponse.setName(nameNode.asText());
		}
		JsonNode messageNode = jsonNode.get("status").get("message");
		if (messageNode != null) {
			systemResponse.setStatusString(messageNode.asText());
		}
		JsonNode detailsNode = jsonNode.get("status").get("details");
		JsonNode itemsNode = detailsNode.get("items");
		if (itemsNode != null) {
			systemResponse.setNormalAlert(itemsNode.get("normal").asInt());
			systemResponse.setWarningAlert(itemsNode.get("warning").asInt());
			systemResponse.setFaultAlert(itemsNode.get("fault").asInt());
			systemResponse.setUnknownAlert(itemsNode.get("unknown").asInt());
		}
		JsonNode designNode = jsonNode.get("design");
		if (designNode.get("name") != null) {
			systemResponse.setDesignName(designNode.get("name").asText());
		}
		if (designNode.get("platform") != null) {
			systemResponse.setDesignPlatform(designNode.get("platform").asText());
		}
		if (designNode.get("uptime") != null) {
			systemResponse.setUptime(designNode.get("uptime").asLong());
		}
		if (jsonNode.get("core").get("name") != null) {
			systemResponse.setCoreName(jsonNode.get("core").get("name").asText());
		}
		return systemResponse;
	}
}
