/*
 * Copyright (c) 2021 AVI-SPL, Inc. All Rights Reserved.
 */
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
import com.avispl.symphony.api.dal.error.ResourceNotReachableException;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.api.dal.monitor.aggregator.Aggregator;

import com.avispl.symphony.dal.aggregator.parser.AggregatedDeviceProcessor;
import com.avispl.symphony.dal.aggregator.parser.PropertiesMapping;
import com.avispl.symphony.dal.aggregator.parser.PropertiesMappingParser;
import com.avispl.symphony.dal.communicator.RestCommunicator;
import com.avispl.symphony.dal.infrastructure.management.qsc.qsysreflect.dto.SystemResponse;
import com.avispl.symphony.dal.infrastructure.management.qsc.qsysreflect.utils.QSysReflectConstant;
import com.avispl.symphony.dal.infrastructure.management.qsc.qsysreflect.utils.QSysReflectSystemMetric;

/**
 * Q-Sys Reflect Enterprise Management Communicator
 * Supported features are:
 * Monitoring Aggregated Device:
 * <ul>
 * <li> - Online / Offline Status</li>
 * <li> - Firmware Version</li>
 * <li> - Device ID</li>
 * <li> - Device Model</li>
 * <li> - Device Name</li>
 * <li> - Serial Number</li>
 * </ul>
 *
 * @author Duy Nguyen, Ivan
 * @since 1.0.0
 */
public class QSysReflectCommunicator extends RestCommunicator implements Aggregator, Monitorable {

	/**
	 * Q-Sys Reflect API Token
	 */
	private String apiToken;

	/**
	 * List of aggregated device
	 */
	private List<AggregatedDevice> aggregatedDeviceList = Collections.synchronizedList(new ArrayList<>());

	private final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * Map of device id and status message
	 */
	private Map<String, String> deviceStatusMessageMap = new HashMap<>();

	/**
	 * Adapter Properties - (Optional) filter option: string of model names (separated by commas)
	 */
	private String filterModelName;

	/**
	 * Adapter Properties - (Optional) filter option: string of status messages (separated by commas)
	 */
	private String filterStatusMessage;

	private List<String> filterDeviceModelValues;
	private List<String> filterStatusMessageValues;

	/**
	 * Retrieves {@code {@link #filterModelName }}
	 *
	 * @return value of {@link #filterModelName}
	 */
	public String getFilterModelName() {
		return filterModelName;
	}

	/**
	 * Sets {@code filterDeviceModel}
	 *
	 * @param filterModelName the {@code java.lang.String} field
	 */
	public void setFilterModelName(String filterModelName) {
		this.filterModelName = filterModelName;
	}

	/**
	 * Retrieves {@code {@link #filterStatusMessage}}
	 *
	 * @return value of {@link #filterStatusMessage}
	 */
	public String getFilterStatusMessage() {
		return filterStatusMessage;
	}

	/**
	 * Sets {@code filterStatusMessage}
	 *
	 * @param filterStatusMessage the {@code java.lang.String} field
	 */
	public void setFilterStatusMessage(String filterStatusMessage) {
		this.filterStatusMessage = filterStatusMessage;
	}

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
		if (logger.isDebugEnabled()) {
			logger.debug("Internal init is called.");
		}
		apiToken = this.getPassword();
		this.setBaseUri(QSysReflectConstant.QSYS_BASE_URL);
		super.internalInit();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void internalDestroy() {
		if (logger.isDebugEnabled()) {
			logger.debug("Internal destroy is called.");
		}
		aggregatedDeviceList.clear();

		super.internalDestroy();
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
		if (!aggregatedDeviceList.isEmpty()) {
			if (logger.isDebugEnabled()) {
				logger.debug("Populating aggregated devices' data and applying filter options");
			}
			populateDeviceUptimeAndStatusMessage();
			filterDeviceModel();
			filterDeviceStatusMessage();
		}
		return aggregatedDeviceList;
	}

	/**
	 * Filter list of aggregated devices based on device status message
	 */
	private void filterDeviceStatusMessage() {
		if (filterStatusMessage != null && !QSysReflectConstant.DOUBLE_QUOTES.equals(filterStatusMessage)) {
			handleListFilterStatus();
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Applying device status message filter with values(s): %s", filterStatusMessage));
			}
			List<AggregatedDevice> filteredAggregatedDevice = new ArrayList<>();
			synchronized (aggregatedDeviceList) {
				for (AggregatedDevice aggregatedDevice : aggregatedDeviceList) {
					for (String filterStatusMessageValue : filterStatusMessageValues) {
						Map<String, String> properties = aggregatedDevice.getProperties();
						if (properties.get(QSysReflectConstant.DEVICE_STATUS_MESSAGE).equals(filterStatusMessageValue)) {
							filteredAggregatedDevice.add(aggregatedDevice);
						}
					}
					aggregatedDeviceList = filteredAggregatedDevice;
				}
			}
		}
	}

	/**
	 * Filter list of aggregated devices based on device model
	 */
	private void filterDeviceModel() {
		if (filterModelName != null && !QSysReflectConstant.DOUBLE_QUOTES.equals(filterModelName)) {
			handleListFilterModel();
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Applying device model filter with values(s): %s", filterModelName));
			}
			List<AggregatedDevice> filteredAggregatedDevice = new ArrayList<>();
			synchronized (aggregatedDeviceList) {
				for (AggregatedDevice aggregatedDevice : aggregatedDeviceList) {
					for (String filterDeviceModelValue : filterDeviceModelValues) {
						if (aggregatedDevice.getDeviceModel().equals(filterDeviceModelValue)) {
							filteredAggregatedDevice.add(aggregatedDevice);
						}
					}
				}
			}
			aggregatedDeviceList = filteredAggregatedDevice;
		}
	}

	/**
	 * Populate normalize uptime and status message from the API
	 */
	private void populateDeviceUptimeAndStatusMessage() {
		synchronized (aggregatedDeviceList) {
			for (int i = 0; i < aggregatedDeviceList.size(); i++) {
				AggregatedDevice aggregatedDevice = aggregatedDeviceList.get(i);
				Map<String, String> properties = aggregatedDevice.getProperties();
				properties.put(QSysReflectConstant.DEVICE_UPTIME, normalizeUptime((System.currentTimeMillis() - Long.parseLong(properties.get(QSysReflectConstant.DEVICE_UPTIME))) / 1000));
				properties.put(QSysReflectConstant.DEVICE_STATUS_MESSAGE, deviceStatusMessageMap.get(aggregatedDevice.getDeviceId()));
				aggregatedDeviceList.set(i, aggregatedDevice);
			}
		}
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
		String responseDeviceList = this.doGet(QSysReflectConstant.QSYS_URL_CORES, String.class);
		JsonNode devices = objectMapper.readTree(responseDeviceList);
		for (int i = 0; i < devices.size(); i++) {
			JsonNode currentDevice = devices.get(i);
			if (currentDevice == null) {
				throw new ResourceNotReachableException(String.format("Fail to get device at index %s", i));
			}
			deviceStatusMessageMap.put(currentDevice.get(QSysReflectConstant.ID).asText(), currentDevice.get(QSysReflectConstant.STATUS).get(QSysReflectConstant.MESSAGE).asText());
		}
		try {
			aggregatedDeviceList = new ArrayList<>(aggregatedDeviceProcessor.extractDevices(devices));
		} catch (Exception e) {
			logger.error("Fail to get list of QSys devices.");
		}
	}

	/**
	 * Get system information from the API
	 *
	 * @return This returns List of SystemResponse DTO.
	 * @throws Exception Throw exception when fail to convert to JsonNode
	 */
	public List<SystemResponse> getSystemInfo() throws Exception {
		String systemResponse = this.doGet(QSysReflectConstant.QSYS_URL_SYSTEMS, String.class);
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
			stats.put(systemResponse.getName() + QSysReflectConstant.HASH_TAG + QSysReflectSystemMetric.SYSTEM_ID.getName(), String.valueOf(systemResponse.getId()));
			stats.put(systemResponse.getName() + QSysReflectConstant.HASH_TAG + QSysReflectSystemMetric.SYSTEM_CODE.getName(), String.valueOf(systemResponse.getCode()));
			stats.put(systemResponse.getName() + QSysReflectConstant.HASH_TAG + QSysReflectSystemMetric.SYSTEM_STATUS.getName(), String.valueOf(systemResponse.getStatusString()));
			if (systemResponse.getNormalAlert() != null) {
				stats.put(systemResponse.getName() + QSysReflectConstant.HASH_TAG + QSysReflectSystemMetric.ALERTS_NORMAL.getName(), String.valueOf(systemResponse.getNormalAlert()));
				stats.put(systemResponse.getName() + QSysReflectConstant.HASH_TAG + QSysReflectSystemMetric.ALERTS_WARNING.getName(), String.valueOf(systemResponse.getWarningAlert()));
				stats.put(systemResponse.getName() + QSysReflectConstant.HASH_TAG + QSysReflectSystemMetric.ALERTS_FAULT.getName(), String.valueOf(systemResponse.getFaultAlert()));
				stats.put(systemResponse.getName() + QSysReflectConstant.HASH_TAG + QSysReflectSystemMetric.ALERTS_UNKNOWN.getName(), String.valueOf(systemResponse.getUnknownAlert()));
			}
			stats.put(systemResponse.getName() + QSysReflectConstant.HASH_TAG + QSysReflectSystemMetric.DESIGN_NAME.getName(), String.valueOf(systemResponse.getDesignName()));
			stats.put(systemResponse.getName() + QSysReflectConstant.HASH_TAG + QSysReflectSystemMetric.DESIGN_PLATFORM.getName(), String.valueOf(systemResponse.getDesignPlatform()));
			stats.put(systemResponse.getName() + QSysReflectConstant.HASH_TAG + QSysReflectSystemMetric.UPTIME.getName(), normalizeUptime((System.currentTimeMillis() - systemResponse.getUptime()) / 1000));
			stats.put(systemResponse.getName() + QSysReflectConstant.HASH_TAG + QSysReflectSystemMetric.CORE_NAME.getName(), String.valueOf(systemResponse.getCoreName()));
		}
	}

	/**
	 * Uptime is received in seconds, need to normalize it and make it human-readable, like
	 * 1 day(s) 5 hour(s) 12 minute(s) 55 minute(s)
	 * Incoming parameter is may have a decimal point, so in order to safely process this - it's rounded first.
	 * We don't need to add a segment of time if it's 0.
	 *
	 * @param uptimeSeconds value in seconds
	 * @return string value of format 'x day(s) x hour(s) x minute(s) x minute(s)'
	 * @author Maksym.Rossiytsev
	 */
	private String normalizeUptime(long uptimeSeconds) {
		StringBuilder normalizedUptime = new StringBuilder();

		long seconds = uptimeSeconds % 60;
		long minutes = uptimeSeconds % 3600 / 60;
		long hours = uptimeSeconds % 86400 / 3600;
		long days = uptimeSeconds / 86400;

		if (days > 0) {
			normalizedUptime.append(days).append(" day(s) ");
		}
		if (hours > 0) {
			normalizedUptime.append(hours).append(" hour(s) ");
		}
		if (minutes > 0) {
			normalizedUptime.append(minutes).append(" minute(s) ");
		}
		if (seconds > 0) {
			normalizedUptime.append(seconds).append(" second(s)");
		}
		return normalizedUptime.toString().trim();
	}

	/**
	 * Split filterModelName (separated by commas) to array of models
	 */
	private void handleListFilterModel() {
		try {
			List<String> resultList = new ArrayList<>();
			String[] listModel = this.getFilterModelName().split(QSysReflectConstant.COMMA);
			for (int i = 0; i < listModel.length; i++) {
				listModel[i] = listModel[i].trim();
			}
			Collections.addAll(resultList, listModel);
			filterDeviceModelValues = resultList;
		} catch (Exception e) {
			throw new IllegalArgumentException("Fail to split string, input from adapter properties is wrong", e);
		}
	}

	/**
	 * Split filterStatusMessage (separated by commas) to array of status messages
	 */
	private void handleListFilterStatus() {
		try {
			List<String> resultList = new ArrayList<>();
			String[] listStatus = this.getFilterStatusMessage().split(QSysReflectConstant.COMMA);
			for (int i = 0; i < listStatus.length; i++) {
				listStatus[i] = listStatus[i].trim();
			}
			Collections.addAll(resultList, listStatus);
			filterStatusMessageValues = resultList;
		} catch (Exception e) {
			throw new IllegalArgumentException("Fail to split string, input from adapter properties is wrong", e);
		}
	}
}

