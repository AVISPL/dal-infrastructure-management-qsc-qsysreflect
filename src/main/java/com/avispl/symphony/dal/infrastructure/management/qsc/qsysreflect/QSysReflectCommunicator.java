package com.avispl.symphony.dal.infrastructure.management.qsc.qsysreflect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.dto.monitor.aggregator.AggregatedDevice;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.api.dal.monitor.aggregator.Aggregator;

import com.avispl.symphony.dal.aggregator.parser.AggregatedDeviceProcessor;
import com.avispl.symphony.dal.aggregator.parser.PropertiesMapping;
import com.avispl.symphony.dal.aggregator.parser.PropertiesMappingParser;
import com.avispl.symphony.dal.communicator.RestCommunicator;
import com.avispl.symphony.dal.infrastructure.management.qsc.qsysreflect.dto.SystemResponse;

/**
 * Q-Sys Reflect Enterprise Management Communicator
 *
 * @author Duy Nguyen, Ivan
 * @since 1.0.0
 */
public class QSysReflectCommunicator extends RestCommunicator implements Aggregator, Monitorable {

	private String apiToken;
	private List<AggregatedDevice> aggregatedDeviceList;
	private final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Statistics> getMultipleStatistics() throws Exception {
		Map<String, String> statistics = new HashMap<>();
		ExtendedStatistics extendedStatistics = new ExtendedStatistics();
		populateSystemData(statistics);
		extendedStatistics.setStatistics(statistics);
		return Collections.singletonList(extendedStatistics);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void internalInit() throws Exception {
		apiToken = this.getPassword();
		this.setBaseUri("/api/public/v0");
		super.internalInit();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void authenticate() throws Exception {
		//
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<AggregatedDevice> retrieveMultipleStatistics() throws Exception {
		retrieveDevices();
		return aggregatedDeviceList;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected HttpHeaders putExtraRequestHeaders(HttpMethod httpMethod, String uri, HttpHeaders headers) throws Exception {
		headers.setBearerAuth(apiToken);
		return headers;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<AggregatedDevice> retrieveMultipleStatistics(List<String> listDeviceId) throws Exception {
		return retrieveMultipleStatistics().stream().filter(aggregatedDevice -> listDeviceId.contains(aggregatedDevice.getDeviceId())).collect(Collectors.toList());
	}

	/**
	 * Get list of device
	 *
	 * @throws Exception Throw exception when fail to get response device
	 */
	private void retrieveDevices() throws Exception {
		Map<String, PropertiesMapping> mapping = new PropertiesMappingParser().loadYML("qsysreflect/model-mapping.yml", getClass());
		AggregatedDeviceProcessor aggregatedDeviceProcessor = new AggregatedDeviceProcessor(mapping);
		String responseDeviceList = this.doGet("/cores", String.class);
		JsonNode devices = objectMapper.readTree(responseDeviceList);
		aggregatedDeviceList = new ArrayList<>(aggregatedDeviceProcessor.extractDevices(devices));
	}

	/**
	 * Get system information from the API
	 *
	 * @return This returns List of SystemResponse DTO.
	 * @throws Exception Throw exception when fail to convert to JsonNode
	 */
	public List<SystemResponse> getSystemInfo() throws Exception {
		String systemResponse = this.doGet("/systems", String.class);
		JsonNode systems = objectMapper.readTree(systemResponse);
		List<SystemResponse> systemResponseList = new ArrayList<>();
		for (int i = 0; i < systems.size(); i++) {
			SystemResponse systemResponse1 = objectMapper.treeToValue(systems.get(i), SystemResponse.class);
			systemResponseList.add(systemResponse1);
		}
		return systemResponseList;
	}

	/**
	 * Populate data to statistics
	 *
	 * @param stats Map of statistic
	 * @throws Exception Throw exception when fail to get system info
	 */
	private void populateSystemData(Map<String, String> stats) throws Exception {
		List<SystemResponse> systemResponseList = getSystemInfo();
		for (SystemResponse systemResponse : systemResponseList) {
			stats.put(systemResponse.getName() + "#" + "SystemId", String.valueOf(systemResponse.getId()));
			stats.put(systemResponse.getName() + "#" + "SystemCode", String.valueOf(systemResponse.getCode()));
			stats.put(systemResponse.getName() + "#" + "SystemStatus", String.valueOf(systemResponse.getStatusString()));
			stats.put(systemResponse.getName() + "#" + "NormalCore", String.valueOf(systemResponse.getNormalCore()));
			stats.put(systemResponse.getName() + "#" + "WarningCore", String.valueOf(systemResponse.getWarningCore()));
			stats.put(systemResponse.getName() + "#" + "FaultCore", String.valueOf(systemResponse.getFaultCore()));
			stats.put(systemResponse.getName() + "#" + "UnknownCore", String.valueOf(systemResponse.getUnknownCore()));
		}
	}
}

