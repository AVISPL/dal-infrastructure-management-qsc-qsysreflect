/*
 * Copyright (c) 2021 AVI-SPL, Inc. All Rights Reserved.
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
		systemResponse.setId(jsonNode.get("id").asInt());
		systemResponse.setCode(jsonNode.get("code").asText());
		systemResponse.setName(jsonNode.get("name").asText());
		systemResponse.setStatusString(jsonNode.get("status").get("message").asText());
		JsonNode detailsNode = jsonNode.get("status").get("details");
		if (!detailsNode.isNull()) {
			systemResponse.setNormalAlert(detailsNode.get("items").get("normal").asInt());
			systemResponse.setWarningAlert(detailsNode.get("items").get("warning").asInt());
			systemResponse.setFaultAlert(detailsNode.get("items").get("fault").asInt());
			systemResponse.setUnknownAlert(detailsNode.get("items").get("unknown").asInt());
		}
		JsonNode designNode = jsonNode.get("design");
		systemResponse.setDesignName(designNode.get("name").asText());
		systemResponse.setDesignPlatform(designNode.get("platform").asText());
		systemResponse.setUptime(designNode.get("uptime").asLong());
		systemResponse.setCoreName(jsonNode.get("core").get("name").asText());
		return systemResponse;
	}
}
