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
		// at() returns a missing node instead of null, so an absent status, details, design or core doesn't throw
		JsonNode id = value(jsonNode, "/id");
		if (id != null) {
			systemResponse.setId(id.asInt());
		}
		systemResponse.setCode(text(jsonNode, "/code"));
		systemResponse.setName(text(jsonNode, "/name"));
		systemResponse.setStatusString(text(jsonNode, "/status/message"));
		systemResponse.setNormalAlert(integer(jsonNode, "/status/details/items/normal"));
		systemResponse.setWarningAlert(integer(jsonNode, "/status/details/items/warning"));
		systemResponse.setFaultAlert(integer(jsonNode, "/status/details/items/fault"));
		systemResponse.setUnknownAlert(integer(jsonNode, "/status/details/items/unknown"));
		systemResponse.setDesignName(text(jsonNode, "/design/name"));
		systemResponse.setDesignPlatform(text(jsonNode, "/design/platform"));
		JsonNode uptime = value(jsonNode, "/design/uptime");
		systemResponse.setUptime(uptime != null ? uptime.asLong() : null);
		systemResponse.setCoreName(text(jsonNode, "/core/name"));
		systemResponse.setCoreId(integer(jsonNode, "/core/id"));
		return systemResponse;
	}

	/**
	 * Node at a JSON pointer, or null when it is missing or a JSON null.
	 */
	private static JsonNode value(JsonNode node, String pointer) {
		JsonNode value = node.at(pointer);
		return value.isMissingNode() || value.isNull() ? null : value;
	}

	private static String text(JsonNode node, String pointer) {
		JsonNode value = value(node, pointer);
		return value != null ? value.asText() : null;
	}

	private static Integer integer(JsonNode node, String pointer) {
		JsonNode value = value(node, pointer);
		return value != null ? value.asInt() : null;
	}
}
