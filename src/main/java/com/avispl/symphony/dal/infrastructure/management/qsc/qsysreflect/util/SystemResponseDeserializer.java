package com.avispl.symphony.dal.infrastructure.management.qsc.qsysreflect.util;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import com.avispl.symphony.dal.infrastructure.management.qsc.qsysreflect.dto.SystemResponse;

public class SystemResponseDeserializer extends StdDeserializer<SystemResponse> {

	public SystemResponseDeserializer() {
		this(null);
	}

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
		systemResponse.setNormalCore(jsonNode.get("status").get("details").get("items").get("normal").asInt());
		systemResponse.setWarningCore(jsonNode.get("status").get("details").get("items").get("warning").asInt());
		systemResponse.setFaultCore(jsonNode.get("status").get("details").get("items").get("fault").asInt());
		systemResponse.setUnknownCore(jsonNode.get("status").get("details").get("items").get("unknown").asInt());
		return systemResponse;
	}
}