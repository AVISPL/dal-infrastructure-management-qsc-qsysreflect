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
		// path() returns a missing node instead of null, so an absent container (status, details, design, core) doesn't throw
		JsonNode idNode = jsonNode.path("id");
		if (hasValue(idNode)) {
			systemResponse.setId(idNode.asInt());
		}
		JsonNode codeNode = jsonNode.path("code");
		if (hasValue(codeNode)) {
			systemResponse.setCode(codeNode.asText());
		}
		JsonNode nameNode = jsonNode.path("name");
		if (hasValue(nameNode)) {
			systemResponse.setName(nameNode.asText());
		}
		JsonNode statusNode = jsonNode.path("status");
		JsonNode messageNode = statusNode.path("message");
		if (hasValue(messageNode)) {
			systemResponse.setStatusString(messageNode.asText());
		}
		JsonNode itemsNode = statusNode.path("details").path("items");
		if (hasValue(itemsNode.path("normal"))) {
			systemResponse.setNormalAlert(itemsNode.path("normal").asInt());
		}
		if (hasValue(itemsNode.path("warning"))) {
			systemResponse.setWarningAlert(itemsNode.path("warning").asInt());
		}
		if (hasValue(itemsNode.path("fault"))) {
			systemResponse.setFaultAlert(itemsNode.path("fault").asInt());
		}
		if (hasValue(itemsNode.path("unknown"))) {
			systemResponse.setUnknownAlert(itemsNode.path("unknown").asInt());
		}
		JsonNode designNode = jsonNode.path("design");
		if (hasValue(designNode.path("name"))) {
			systemResponse.setDesignName(designNode.path("name").asText());
		}
		if (hasValue(designNode.path("platform"))) {
			systemResponse.setDesignPlatform(designNode.path("platform").asText());
		}
		if (hasValue(designNode.path("uptime"))) {
			systemResponse.setUptime(designNode.path("uptime").asLong());
		}
		JsonNode coreNode = jsonNode.path("core");
		if (hasValue(coreNode.path("name"))) {
			systemResponse.setCoreName(coreNode.path("name").asText());
		}
		if (hasValue(coreNode.path("id"))) {
			systemResponse.setCoreId(coreNode.path("id").asInt());
		}
		return systemResponse;
	}

	/**
	 * Check that a node is present and not a JSON null.
	 *
	 * @param node node to check
	 * @return true if the node has a value
	 */
	private static boolean hasValue(JsonNode node) {
		return !node.isMissingNode() && !node.isNull();
	}
}
