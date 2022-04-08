/*
 * Copyright (c) 2022 AVI-SPL, Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.infrastructure.management.qsc.qsysreflect;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import com.avispl.symphony.dal.util.StringUtils;

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
 * @author Duy Nguyen, Ivan, Harry
 * @version 2.0.0
 * @since 2.0.0
 */
public class QSysReflectCommunicator extends RestCommunicator implements Aggregator, Monitorable {
	/**
	 * Process that is running constantly and triggers collecting data from Q-Sys API endpoints, based on the given timeouts and thresholds.
	 *
	 * @author Maksym.Rossiytsev, Ivan
	 * @since 1.0.0
	 */
	class QSysDeviceDataLoader implements Runnable {
		private volatile boolean inProgress;

		public QSysDeviceDataLoader() {
			inProgress = true;
		}

		@Override
		public void run() {
			mainloop:
			while (inProgress) {
				try {
					TimeUnit.MILLISECONDS.sleep(500);
				} catch (InterruptedException e) {
					// Ignore for now
				}

				if (!inProgress) {
					break mainloop;
				}

				// next line will determine whether QSys monitoring was paused
				updateAggregatorStatus();
				if (devicePaused) {
					continue mainloop;
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Fetching devices and system information list");
				}
				long currentTimestamp = System.currentTimeMillis();
				retrieveInfo(currentTimestamp);
				if (logger.isDebugEnabled()) {
					logger.debug("Fetching system devices list");
				}
				if (!systemResponseList.isEmpty() && validDeviceMetaDataRetrievalPeriodTimestamp <= currentTimestamp) {
					validDeviceMetaDataRetrievalPeriodTimestamp = currentTimestamp + deviceMetaDataRetrievalTimeout;
					synchronized (systemResponseList) {
						filterBySystemName();
						List<SystemResponse> systemResponseFilter = systemResponseFilterList;
						if (StringUtils.isNullOrEmpty(filterSystemName)) {
							systemResponseFilter = systemResponseList;
						}
						for (SystemResponse systemResponse : systemResponseFilter) {
							devicesExecutionPool.add(executorService.submit(() -> {
								try {
									populateDeviceDetails(String.valueOf(systemResponse.getId()));
								} catch (Exception e) {
									logger.error(String.format("Exception during retrieve '%s' data processing.", systemResponse.getName()), e);
								}
							}));
						}
						do {
							try {
								TimeUnit.MILLISECONDS.sleep(500);
							} catch (InterruptedException e) {
								if (!inProgress) {
									break;
								}
							}
							devicesExecutionPool.removeIf(Future::isDone);
						} while (!devicesExecutionPool.isEmpty());
					}
				}
				if (!inProgress) {
					break mainloop;
				}

				int aggregatedDevicesCount = aggregatedDeviceList.size();
				if (aggregatedDevicesCount == 0) {
					continue mainloop;
				}

				nextDevicesCollectionIterationTimestamp = System.currentTimeMillis();
				while (nextDevicesCollectionIterationTimestamp > System.currentTimeMillis()) {
					try {
						TimeUnit.MILLISECONDS.sleep(1000);
					} catch (InterruptedException e) {
						//
					}
				}

				if (!aggregatedDeviceList.isEmpty()) {
					if (logger.isDebugEnabled()) {
						logger.debug("Applying filter options");
					}

					if ((!StringUtils.isNullOrEmpty(filterSystemName) && !systemResponseFilterList.isEmpty()) || StringUtils.isNullOrEmpty(filterSystemName)) {
						getFilteredAggregatedDeviceList();
					} else {
						aggregatedDeviceList.clear();
					}
					if (logger.isDebugEnabled()) {
						logger.debug("Aggregated devices after applying filter: " + aggregatedDeviceList);
					}
				}

				// We don't want to fetch devices statuses too often, so by default it's currentTime + 30s
				// otherwise - the variable is reset by the retrieveMultipleStatistics() call, which
				// launches devices detailed statistics collection
				nextDevicesCollectionIterationTimestamp = System.currentTimeMillis() + 30000;

				if (logger.isDebugEnabled()) {
					logger.debug("Finished collecting devices statistics cycle at " + new Date());
				}
			}
			// Finished collecting
		}

		/**
		 * Triggers main loop to stop
		 */
		public void stop() {
			inProgress = false;
		}
	}

	/**
	 * Executor that runs all the async operations, that {@link #deviceDataLoader} is posting and
	 * {@link #devicesExecutionPool} is keeping track of
	 */
	private static ExecutorService executorService;

	/**
	 * Pool for keeping all the async operations in, to track any operations in progress and cancel them if needed
	 */
	private List<Future> devicesExecutionPool = new ArrayList<>();

	/**
	 * Update the status of the device.
	 * The device is considered as paused if did not receive any retrieveMultipleStatistics()
	 * calls during {@link QSysReflectCommunicator#validRetrieveStatisticsTimestamp}
	 */
	private synchronized void updateAggregatorStatus() {
		devicePaused = validRetrieveStatisticsTimestamp < System.currentTimeMillis();
	}

	/**
	 * Uptime time stamp to valid one
	 */
	private synchronized void updateValidRetrieveStatisticsTimestamp() {
		validRetrieveStatisticsTimestamp = System.currentTimeMillis() + retrieveStatisticsTimeOut;
		updateAggregatorStatus();
	}

	/**
	 * This parameter holds timestamp of when we need to stop performing API calls
	 * It used when device stop retrieving statistic. Updated each time of called #retrieveMultipleStatistics
	 */
	private volatile long validRetrieveStatisticsTimestamp;

	/**
	 * Indicates whether a device is considered as paused.
	 * True by default so if the system is rebooted and the actual value is lost -> the device won't start stats
	 * collection unless the {@link QSysReflectCommunicator#retrieveMultipleStatistics()} method is called which will change it
	 * to a correct value
	 */
	private volatile boolean devicePaused = true;

	/**
	 * Aggregator inactivity timeout. If the {@link QSysReflectCommunicator#retrieveMultipleStatistics()}  method is not
	 * called during this period of time - device is considered to be paused, thus the Cloud API
	 * is not supposed to be called
	 */
	private static final long retrieveStatisticsTimeOut = 3 * 60 * 1000;

	/**
	 * Device metadata retrieval timeout. The general devices list is retrieved once during this time period.
	 */
	private long deviceMetaDataRetrievalTimeout = 60 * 1000 / 2;

	/**
	 * If the {@link QSysReflectCommunicator#deviceMetaDataRetrievalTimeout} is set to a value that is too small -
	 * devices list will be fetched too frequently. In order to avoid this - the minimal value is based on this value.
	 */
	private static final long defaultMetaDataTimeout = 60 * 1000 / 2;


	/**
	 * Time period within which the device metadata (basic devices information) cannot be refreshed.
	 * Ignored if device list is not yet retrieved or the cached device list is empty {@link QSysReflectCommunicator#aggregatedDeviceList}
	 */
	private volatile long validDeviceMetaDataRetrievalPeriodTimestamp;

	/**
	 * We don't want the statistics to be collected constantly, because if there's not a big list of devices -
	 * new devices' statistics loop will be launched before the next monitoring iteration. To avoid that -
	 * this variable stores a timestamp which validates it, so when the devices' statistics is done collecting, variable
	 * is set to currentTime + 30s, at the same time, calling {@link #retrieveMultipleStatistics()} and updating the
	 * {@link #aggregatedDeviceList} resets it to the currentTime timestamp, which will re-activate data collection.
	 */
	private long nextDevicesCollectionIterationTimestamp;

	private AggregatedDeviceProcessor aggregatedDeviceProcessor;

	/**
	 * Runner service responsible for collecting data
	 */
	private QSysDeviceDataLoader deviceDataLoader;

	/**
	 * Q-Sys Reflect API Token
	 */
	private String apiToken;

	/**
	 * List of aggregated device
	 */
	private List<AggregatedDevice> aggregatedDeviceList = Collections.synchronizedList(new ArrayList<>());

	/**
	 * List of System Response
	 */
	private List<SystemResponse> systemResponseList = Collections.synchronizedList(new ArrayList<>());

	/**
	 * List of System Response filter
	 */
	private List<SystemResponse> systemResponseFilterList = Collections.synchronizedList(new ArrayList<>());

	/**
	 * List error message occur while fetching aggregated devices
	 */
	private Set<String> deviceErrorMessagesList = Collections.synchronizedSet(new LinkedHashSet<>());

	/**
	 * List of error message occur while fetching system information
	 */
	private Set<String> systemErrorMessagesList = Collections.synchronizedSet(new LinkedHashSet<>());

	private final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * Map of device id and status message
	 */
	private Map<String, String> deviceStatusMessageMap = new HashMap<>();

	/**
	 * Adapter Properties - (Optional) filter option: string of model names (separated by commas)
	 */
	private String filterModel;

	/**
	 * Adapter Properties - (Optional) filter option: string of status messages (separated by commas)
	 */
	private String filterDeviceStatusMessage;

	/**
	 * Adapter Properties - (Optional) filter option: string of system name (separated by commas)
	 */
	private String filterSystemName;

	/**
	 * Adapter Properties - (Optional) filter option: string of type (separated by commas)
	 */
	private String filterType;

	/**
	 * Retrieves {@code {@link #filterSystemName}}
	 *
	 * @return value of {@link #filterSystemName}
	 */
	public String getFilterSystemName() {
		return filterSystemName;
	}

	/**
	 * Sets {@code filterSystemName}
	 *
	 * @param filterSystemName the {@code java.lang.String} field
	 */
	public void setFilterSystemName(String filterSystemName) {
		this.filterSystemName = filterSystemName;
	}

	/**
	 * Retrieves {@code {@link #filterType}}
	 *
	 * @return value of {@link #filterType}
	 */
	public String getFilterType() {
		return filterType;
	}

	/**
	 * Sets {@code filterType}
	 *
	 * @param filterType the {@code java.lang.String} field
	 */
	public void setFilterType(String filterType) {
		this.filterType = filterType;
	}

	/**
	 * Retrieves {@code {@link #filterModel }}
	 *
	 * @return value of {@link #filterModel}
	 */
	public String getFilterModel() {
		return filterModel;
	}

	/**
	 * Sets {@code filterDeviceModel}
	 *
	 * @param filterModel the {@code java.lang.String} field
	 */
	public void setFilterModel(String filterModel) {
		this.filterModel = filterModel;
	}

	/**
	 * Retrieves {@code {@link #filterDeviceStatusMessage }}
	 *
	 * @return value of {@link #filterDeviceStatusMessage}
	 */
	public String getFilterDeviceStatusMessage() {
		return filterDeviceStatusMessage;
	}

	/**
	 * Sets {@code filterStatusMessage}
	 *
	 * @param filterDeviceStatusMessage the {@code java.lang.String} field
	 */
	public void setFilterDeviceStatusMessage(String filterDeviceStatusMessage) {
		this.filterDeviceStatusMessage = filterDeviceStatusMessage;
	}

	/**
	 * Retrieves {@code {@link #deviceMetaDataRetrievalTimeout }}
	 *
	 * @return value of {@link #deviceMetaDataRetrievalTimeout}
	 */
	public long getDeviceMetaDataRetrievalTimeout() {
		return deviceMetaDataRetrievalTimeout;
	}

	/**
	 * Sets {@code deviceMetaDataInformationRetrievalTimeout}
	 *
	 * @param deviceMetaDataRetrievalTimeout the {@code long} field
	 */
	public void setDeviceMetaDataRetrievalTimeout(long deviceMetaDataRetrievalTimeout) {
		this.deviceMetaDataRetrievalTimeout = Math.max(defaultMetaDataTimeout, deviceMetaDataRetrievalTimeout);
	}

	/**
	 * Build instance of QSysReflectCommunicator
	 * Setup aggregated devices processor
	 *
	 * @throws IOException if unable to locate mapping ymp file or properties file
	 */
	public QSysReflectCommunicator() throws IOException {
		Map<String, PropertiesMapping> mapping = new PropertiesMappingParser().loadYML("qsysreflect/model-mapping.yml", getClass());
		aggregatedDeviceProcessor = new AggregatedDeviceProcessor(mapping);
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
		if (checkValidApiToken()) {
			executorService = Executors.newFixedThreadPool(8);
			executorService.submit(deviceDataLoader = new QSysDeviceDataLoader());
			validDeviceMetaDataRetrievalPeriodTimestamp = System.currentTimeMillis();
		}
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

		if (deviceDataLoader != null) {
			deviceDataLoader.stop();
			deviceDataLoader = null;
		}

		if (executorService != null) {
			executorService.shutdownNow();
			executorService = null;
		}

		devicesExecutionPool.forEach(future -> future.cancel(true));
		devicesExecutionPool.clear();

		aggregatedDeviceList.clear();
		systemResponseList.clear();
		deviceErrorMessagesList.clear();
		systemErrorMessagesList.clear();
		super.internalDestroy();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void authenticate() {
		// Q-Sys Reflect only require API token for each request.
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected HttpHeaders putExtraRequestHeaders(HttpMethod httpMethod, String uri, HttpHeaders headers) {
		headers.setBearerAuth(apiToken);
		return headers;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Statistics> getMultipleStatistics() {
		if (!checkValidApiToken()) {
			throw new ResourceNotReachableException("API Token cannot be null or empty, please enter valid API token in the password field.");
		}
		Map<String, String> statistics = new HashMap<>();
		ExtendedStatistics extendedStatistics = new ExtendedStatistics();
		populateSystemData(statistics);
		extendedStatistics.setStatistics(statistics);
		if (!systemErrorMessagesList.isEmpty()) {
			synchronized (systemErrorMessagesList) {
				String errorMessage = systemErrorMessagesList.stream().map(Object::toString)
						.collect(Collectors.joining("\n"));
				systemErrorMessagesList.clear();
				throw new ResourceNotReachableException(errorMessage);
			}
		}
		return Collections.singletonList(extendedStatistics);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<AggregatedDevice> retrieveMultipleStatistics() {
		if (checkValidApiToken()) {
			if (executorService == null) {
				// Due to the bug that after changing properties on fly - the adapter is destroyed but adapter is not initialized properly,
				// so executor service is not running. We need to make sure executorService exists
				executorService = Executors.newSingleThreadExecutor();
				executorService.submit(deviceDataLoader = new QSysDeviceDataLoader());
			}
			nextDevicesCollectionIterationTimestamp = System.currentTimeMillis();
			updateValidRetrieveStatisticsTimestamp();
		}
		if (!deviceErrorMessagesList.isEmpty()) {
			synchronized (deviceErrorMessagesList) {
				String errorMessage = deviceErrorMessagesList.stream().map(Object::toString)
						.collect(Collectors.joining("\n"));
				deviceErrorMessagesList.clear();
				throw new ResourceNotReachableException(errorMessage);
			}
		}
		if (aggregatedDeviceList.isEmpty()) {
			return aggregatedDeviceList;
		}
		List<AggregatedDevice> resultAggregatedDeviceList = cloneAggregatedDeviceList();
		populateDeviceUptime(resultAggregatedDeviceList);
		return resultAggregatedDeviceList;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<AggregatedDevice> retrieveMultipleStatistics(List<String> listDeviceId) {
		return retrieveMultipleStatistics().stream().filter(aggregatedDevice -> listDeviceId.contains(aggregatedDevice.getDeviceId())).collect(Collectors.toList());
	}

	/**
	 * Clone an aggregated device list that based on aggregatedDeviceList variable
	 *
	 * @return List<AggregatedDevice>
	 */
	private List<AggregatedDevice> cloneAggregatedDeviceList() {
		List<AggregatedDevice> resultAggregatedDeviceList = new ArrayList<>();
		synchronized (aggregatedDeviceList) {
			for (AggregatedDevice aggregatedDevice : aggregatedDeviceList) {
				AggregatedDevice newClonedAggregatedDevice = new AggregatedDevice();
				newClonedAggregatedDevice.setDeviceId(aggregatedDevice.getDeviceId());
				newClonedAggregatedDevice.setDeviceModel(aggregatedDevice.getDeviceModel());
				newClonedAggregatedDevice.setDeviceName(aggregatedDevice.getDeviceName());
				newClonedAggregatedDevice.setSerialNumber(aggregatedDevice.getSerialNumber());
				Map<String, String> newProperties = new HashMap<>();
				for (Map.Entry<String, String> entry : aggregatedDevice.getProperties().entrySet()) {
					newProperties.put(entry.getKey(), entry.getValue());
				}
				boolean deviceOnline =
						deviceStatusMessageMap.get(aggregatedDevice.getDeviceId()).equals(QSysReflectConstant.RUNNING) || deviceStatusMessageMap.get(aggregatedDevice.getDeviceId()).equals(QSysReflectConstant.OK);
				newClonedAggregatedDevice.setDeviceOnline(deviceOnline);
				newClonedAggregatedDevice.setProperties(newProperties);
				resultAggregatedDeviceList.add(newClonedAggregatedDevice);
			}
		}
		return resultAggregatedDeviceList;
	}

	/**
	 * Filter list of aggregated devices based on device status message
	 */
	private void filterDeviceStatusMessage() {
		if (!StringUtils.isNullOrEmpty(filterDeviceStatusMessage) && !QSysReflectConstant.DOUBLE_QUOTES.equals(filterDeviceStatusMessage)) {
			List<String> filterStatusMessageValues = handleListFilterStatus();
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Applying device status message filter with values(s): %s", filterDeviceStatusMessage));
			}
			List<AggregatedDevice> filteredAggregatedDevice = new ArrayList<>();
			synchronized (aggregatedDeviceList) {
				for (AggregatedDevice aggregatedDevice : aggregatedDeviceList) {
					Map<String, String> properties = aggregatedDevice.getProperties();
					for (String deviceMessage : filterStatusMessageValues) {
						if (deviceMessage.equals(properties.get(QSysReflectConstant.DEVICE_STATUS_MESSAGE))) {
							filteredAggregatedDevice.add(aggregatedDevice);
						}
					}
				}
			}
			aggregatedDeviceList = filteredAggregatedDevice;
		}
	}

	/**
	 * Filter list of aggregated devices based on device type
	 */
	private void filterType() {
		if (!StringUtils.isNullOrEmpty(filterType) && !QSysReflectConstant.DOUBLE_QUOTES.equals(filterType)) {
			List<String> filterTypeValues = handleListFilterType();
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Applying device status message filter with values(s): %s", filterType));
			}
			List<AggregatedDevice> filteredAggregatedDevice = new ArrayList<>();
			synchronized (aggregatedDeviceList) {
				for (AggregatedDevice aggregatedDevice : aggregatedDeviceList) {
					Map<String, String> properties = aggregatedDevice.getProperties();
					for (String type : filterTypeValues) {
						if (type.equals(properties.get(QSysReflectConstant.DEVICE_TYPE))) {
							filteredAggregatedDevice.add(aggregatedDevice);
						}
					}
				}
			}
			aggregatedDeviceList = filteredAggregatedDevice;
		}
	}

	/**
	 * Filter list of aggregated devices based on device model
	 */
	private void filterDeviceModel() {
		if (!StringUtils.isNullOrEmpty(filterModel) && !QSysReflectConstant.DOUBLE_QUOTES.equals(filterModel)) {
			List<String> filterDeviceModelValues = handleListFilterModel();
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Applying device model filter with values(s): %s", filterModel));
			}
			List<AggregatedDevice> filteredAggregatedDevice = new ArrayList<>();
			synchronized (aggregatedDeviceList) {
				for (AggregatedDevice aggregatedDevice : aggregatedDeviceList) {
					for (String modelName : filterDeviceModelValues) {
						if (modelName.equals(aggregatedDevice.getDeviceModel())) {
							filteredAggregatedDevice.add(aggregatedDevice);
						}
					}
				}
			}
			aggregatedDeviceList = filteredAggregatedDevice;
		}
	}

	/**
	 * Populate status message from the API
	 */
	private void populateDeviceStatusMessage() {
		synchronized (aggregatedDeviceList) {
			for (int i = 0; i < aggregatedDeviceList.size(); i++) {
				AggregatedDevice aggregatedDevice = aggregatedDeviceList.get(i);
				Map<String, String> properties = aggregatedDevice.getProperties();
				properties.put(QSysReflectConstant.DEVICE_STATUS_MESSAGE, deviceStatusMessageMap.get(aggregatedDevice.getDeviceId()));
				aggregatedDeviceList.set(i, aggregatedDevice);
			}
		}
	}

	/**
	 * Populate device uptime from the API
	 * @param resultAggregatedDeviceList list of aggregated device that need to update device uptime
	 */
	private void populateDeviceUptime(List<AggregatedDevice> resultAggregatedDeviceList) {
		for (int i = 0; i < resultAggregatedDeviceList.size(); i++) {
			AggregatedDevice aggregatedDevice = resultAggregatedDeviceList.get(i);
			Map<String, String> properties = aggregatedDevice.getProperties();
			String startAt = properties.get(QSysReflectConstant.START_AT);
			String upTime = properties.get(QSysReflectConstant.DEVICE_UPTIME);

			if (!StringUtils.isNullOrEmpty(upTime)) {
				properties.put(QSysReflectConstant.DEVICE_UPTIME, handleNormalizeUptime(Long.parseLong(upTime)));
			} else if (!StringUtils.isNullOrEmpty(startAt)) {
				properties.put(QSysReflectConstant.START_AT, handleNormalizeUptime(Long.parseLong(startAt)));
			} else {
				properties.put(QSysReflectConstant.START_AT, QSysReflectConstant.NONE);
			}
			resultAggregatedDeviceList.set(i, aggregatedDevice);
		}
	}

	/**
	 * Retrieve aggregated devices and system information data -
	 * and set next device/system collection iteration timestamp
	 */
	private void retrieveInfo(long currentTimestamp) {
		if (validDeviceMetaDataRetrievalPeriodTimestamp > currentTimestamp) {
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Aggregated devices data and system information retrieval is in cool down. %s seconds left",
						(validDeviceMetaDataRetrievalPeriodTimestamp - currentTimestamp) / 1000));
				if (!aggregatedDeviceList.isEmpty()) {
					logger.debug(String.format("Old fetched devices list: %s", aggregatedDeviceList));
				}
				if (!systemResponseList.isEmpty()) {
					logger.debug(String.format("Old system information list: %s", systemResponseList));
				}
			}
			return;
		}
		retrieveDevices();
		if (logger.isDebugEnabled()) {
			logger.debug(String.format("New fetched devices list: %s", aggregatedDeviceList));
		}
		retrieveSystemInfo();
		if (logger.isDebugEnabled()) {
			logger.debug(String.format("New fetched system information list: %s", systemResponseList));
		}
	}

	/**
	 * Get list of device every 30 seconds
	 * API Endpoint: /cores
	 * Success: Return a list of devices(cores) within the organization
	 */
	private void retrieveDevices() {
		try {
			String responseDeviceList = this.doGet(QSysReflectConstant.QSYS_URL_CORES, String.class);
			JsonNode devices = objectMapper.readTree(responseDeviceList);
			for (int i = 0; i < devices.size(); i++) {
				JsonNode currentDevice = devices.get(i);
				deviceStatusMessageMap.put(currentDevice.get(QSysReflectConstant.ID).asText(), currentDevice.get(QSysReflectConstant.STATUS)
						.get(QSysReflectConstant.MESSAGE).asText());
			}
			aggregatedDeviceList = new ArrayList<>(aggregatedDeviceProcessor.extractDevices(devices));
			for (AggregatedDevice aggregatedDevice : aggregatedDeviceList) {
				Map<String, String> stats = aggregatedDevice.getProperties();
				stats.put(QSysReflectConstant.DEVICE_TYPE, QSysReflectConstant.CORE);
				aggregatedDevice.setProperties(stats);
			}
		} catch (Exception e) {
			String errorMessage = String.format("Aggregated Device Data Retrieval-Error: %s with cause: %s", e.getMessage(), e.getCause().getMessage());
			deviceErrorMessagesList.add(errorMessage);
			logger.error(errorMessage);
		}
	}

	/**
	 * Get list of device every 30 seconds
	 *
	 * API Endpoint: /systems/{id}/items
	 * Success: Return a list of devices within the organization
	 */
	private void populateDeviceDetails(String deviceId) {
		try {
			JsonNode responseDeviceList = this.doGet(QSysReflectConstant.QSYS_URL_SYSTEMS + "/" + deviceId + QSysReflectConstant.QSYS_URL_ITEMS, JsonNode.class);
			for (int i = 0; i < responseDeviceList.size(); i++) {
				JsonNode currentDevice = responseDeviceList.get(i);
				deviceStatusMessageMap.put(currentDevice.get(QSysReflectConstant.ID).asText(), currentDevice.get(QSysReflectConstant.STATUS)
						.get(QSysReflectConstant.MESSAGE).asText());
			}
			Map<String, PropertiesMapping> mapping = new PropertiesMappingParser().loadYML("qsysreflect/model-mapping-v2.yml", getClass());
			aggregatedDeviceProcessor = new AggregatedDeviceProcessor(mapping);
			aggregatedDeviceList.addAll(aggregatedDeviceProcessor.extractDevices(responseDeviceList));
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("New fetched aggregated device list: %s", aggregatedDeviceList));
			}
		} catch (Exception e) {
			String errorMessage = String.format("Aggregated Device Data Retrieval-Error: %s with cause: %s", e.getMessage(), e.getCause().getMessage());
			deviceErrorMessagesList.add(errorMessage);
			logger.error(errorMessage);
		}
	}

	/**
	 * Get system information every 30 seconds
	 * API Endpoint: /systems
	 * Success: return list of systems within the organization
	 */
	public void retrieveSystemInfo() {
		// Retrieve system information every 30 seconds
		try {
			String systemResponse = this.doGet(QSysReflectConstant.QSYS_URL_SYSTEMS, String.class);
			JsonNode systems = objectMapper.readTree(systemResponse);
			systemResponseList.clear();
			for (int i = 0; i < systems.size(); i++) {
				SystemResponse sysRes = objectMapper.treeToValue(systems.get(i), SystemResponse.class);
				systemResponseList.add(sysRes);
			}
		} catch (Exception e) {
			String errorMessage = String.format("System Information Data Retrieval-Error: %s with cause: %s", e.getMessage(), e.getCause().getMessage());
			systemErrorMessagesList.add(errorMessage);
			logger.error(errorMessage);
		}
	}

	/**
	 * Populate data to statistics
	 *
	 * @param stats Map of statistic
	 */
	private void populateSystemData(Map<String, String> stats) {
		if (!systemResponseList.isEmpty()) {
			if (logger.isDebugEnabled()) {
				logger.debug("Populating system information data");
			}
			Map<String, String> deviceNameAndModelMap = new HashMap<>();
			synchronized (aggregatedDeviceList) {
				for (AggregatedDevice aggregatedDevice : aggregatedDeviceList) {
					deviceNameAndModelMap.put(aggregatedDevice.getDeviceName(), aggregatedDevice.getDeviceModel());
				}
			}
			synchronized (systemResponseList) {
				for (SystemResponse systemResponse : systemResponseList) {
						stats.put(String.format("%s#%s", systemResponse.getName(), QSysReflectSystemMetric.SYSTEM_ID.getName()), String.valueOf(systemResponse.getId()));
						stats.put(String.format("%s#%s", systemResponse.getName(), QSysReflectSystemMetric.SYSTEM_CODE.getName()), String.valueOf(systemResponse.getCode()));
						stats.put(String.format("%s#%s", systemResponse.getName(), QSysReflectSystemMetric.SYSTEM_STATUS.getName()), String.valueOf(systemResponse.getStatusString()));
						if (systemResponse.getNormalAlert() != null && systemResponse.getFaultAlert() != null && systemResponse.getUnknownAlert() != null && systemResponse.getWarningAlert() != null) {
							stats.put(String.format("%s#%s", systemResponse.getName(), QSysReflectSystemMetric.ALERTS_NORMAL.getName()), String.valueOf(systemResponse.getNormalAlert()));
							stats.put(String.format("%s#%s", systemResponse.getName(), QSysReflectSystemMetric.ALERTS_WARNING.getName()), String.valueOf(systemResponse.getWarningAlert()));
							stats.put(String.format("%s#%s", systemResponse.getName(), QSysReflectSystemMetric.ALERTS_FAULT.getName()), String.valueOf(systemResponse.getFaultAlert()));
							stats.put(String.format("%s#%s", systemResponse.getName(), QSysReflectSystemMetric.ALERTS_UNKNOWN.getName()), String.valueOf(systemResponse.getUnknownAlert()));
						}
						stats.put(String.format("%s#%s", systemResponse.getName(), QSysReflectSystemMetric.DESIGN_NAME.getName()), String.valueOf(systemResponse.getDesignName()));
						stats.put(String.format("%s#%s", systemResponse.getName(), QSysReflectSystemMetric.DESIGN_PLATFORM.getName()), String.valueOf(systemResponse.getDesignPlatform()));
						stats.put(String.format("%s#%s", systemResponse.getName(), QSysReflectSystemMetric.UPTIME.getName()), handleNormalizeUptime(systemResponse.getUptime()));
						stats.put(String.format("%s#%s", systemResponse.getName(), QSysReflectSystemMetric.CORE_NAME.getName()), String.valueOf(systemResponse.getCoreName()));
						stats.put(String.format("%s#%s", systemResponse.getName(), QSysReflectSystemMetric.MODEL.getName()), deviceNameAndModelMap.get(systemResponse.getCoreName()));
				}
			}
		}
	}

	/**
	 * Filter the list of aggregated devices based on filter option in Adapter Properties
	 */
	private void getFilteredAggregatedDeviceList() {
		populateDeviceStatusMessage();
		filterDeviceModel();
		filterDeviceStatusMessage();
		filterType();
	}

	/**
	 * Filter list of aggregated devices by the name of system
	 */
	private void filterBySystemName() {
		systemResponseFilterList.clear();
		if (!StringUtils.isNullOrEmpty(filterSystemName) && !QSysReflectConstant.DOUBLE_QUOTES.equals(filterSystemName)) {
			List<String> filterSystemNameValues = handleListFilterSystemName();
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Applying system name filter with values(s): %s", filterSystemName));
			}
			List<SystemResponse> filteredSystemResponse = new ArrayList<>();
			synchronized (systemResponseList) {
				for (SystemResponse systemResponse : systemResponseList) {
					for (String systemName : filterSystemNameValues) {
						if (systemName.equals(systemResponse.getName())) {
							filteredSystemResponse.add(systemResponse);
						}
					}
				}
			}
			systemResponseFilterList = filteredSystemResponse;
			if (systemResponseFilterList.isEmpty()) {
				aggregatedDeviceList.clear();
			}
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
	 * Handle long uptime to human-readable string
	 *
	 * @param uptime uptime return by the API
	 * @return human-readable uptime string
	 */
	private String handleNormalizeUptime(Long uptime) {
		long timeDiff = (System.currentTimeMillis() - uptime) / 1000;
		return timeDiff > 0 ? normalizeUptime(timeDiff) : QSysReflectConstant.NONE;
	}

	/**
	 * Split filterModelName (separated by commas) to array of models
	 *
	 * @return list string of device model
	 */
	private List<String> handleListFilterModel() {
		try {
			List<String> resultList = new ArrayList<>();
			String[] listModel = this.getFilterModel().split(QSysReflectConstant.COMMA);
			for (int i = 0; i < listModel.length; i++) {
				listModel[i] = listModel[i].trim();
			}
			Collections.addAll(resultList, listModel);
			return resultList;
		} catch (Exception e) {
			throw new IllegalArgumentException("Fail to split string, input from adapter properties is wrong", e);
		}
	}

	/**
	 * Split filterStatusMessage (separated by commas) to array of status messages
	 *
	 * @return list string of status message
	 */
	private List<String> handleListFilterStatus() {
		try {
			List<String> resultList = new ArrayList<>();
			String[] listStatus = this.getFilterDeviceStatusMessage().split(QSysReflectConstant.COMMA);
			for (int i = 0; i < listStatus.length; i++) {
				listStatus[i] = listStatus[i].trim();
			}
			Collections.addAll(resultList, listStatus);
			return resultList;
		} catch (Exception e) {
			throw new IllegalArgumentException("Fail to split string, input from adapter properties is wrong", e);
		}
	}

	/**
	 * Split filterType (separated by commas) to array of status messages
	 *
	 * @return list string of type
	 */
	private List<String> handleListFilterType() {
		try {
			List<String> resultList = new ArrayList<>();
			String[] listStatus = this.getFilterType().split(QSysReflectConstant.COMMA);
			for (int i = 0; i < listStatus.length; i++) {
				listStatus[i] = listStatus[i].trim();
			}
			Collections.addAll(resultList, listStatus);
			return resultList;
		} catch (Exception e) {
			throw new IllegalArgumentException("Fail to split string, input from adapter properties is wrong", e);
		}
	}

	/**
	 * Check API token validation
	 *
	 * @return boolean
	 */
	private boolean checkValidApiToken() {
		return !StringUtils.isNullOrEmpty(apiToken);
	}

	/**
	 * Split filterSystemName (separated by commas) to array of system name
	 *
	 * @return list string of system name
	 */
	private List<String> handleListFilterSystemName() {
		try {
			List<String> resultList = new ArrayList<>();
			String[] listModel = this.getFilterSystemName().split(QSysReflectConstant.COMMA);
			for (int i = 0; i < listModel.length; i++) {
				listModel[i] = listModel[i].trim();
			}
			Collections.addAll(resultList, listModel);
			return resultList;
		} catch (Exception e) {
			throw new IllegalArgumentException("Fail to split string, input from adapter properties is wrong", e);
		}
	}
}