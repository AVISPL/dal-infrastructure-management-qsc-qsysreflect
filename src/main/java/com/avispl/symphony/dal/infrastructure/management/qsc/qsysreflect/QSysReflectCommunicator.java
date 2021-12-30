/*
 * Copyright (c) 2021 AVI-SPL, Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.infrastructure.management.qsc.qsysreflect;

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

				long currentTimestamp = System.currentTimeMillis();
				try {
					if (logger.isDebugEnabled()) {
						logger.debug("Fetching devices list");
					}
					retrieveDevices(currentTimestamp);
					if (logger.isDebugEnabled()) {
						logger.debug("Fetched devices list: " + aggregatedDeviceList);
					}
				} catch (Exception e) {
					String errorMessage = "Error occurred during device list retrieval: " + e.getMessage() + " with cause: " + e.getCause().getMessage();
					logger.error(errorMessage, e);
				}

				try {
					if (logger.isDebugEnabled()) {
						logger.debug("Fetching system information list");
					}
					retrieveSystemInfo(currentTimestamp);
					if (logger.isDebugEnabled()) {
						logger.debug("Fetched system information list: " + systemResponseList);
					}
				} catch (Exception e) {
					String errorMessage = "Error occurred during system information list retrieval: " + e.getMessage() + " with cause: " + e.getCause().getMessage();
					logger.error(errorMessage, e);
				}
				if (!inProgress) {
					break mainloop;
				}

				int aggregatedDevicesCount = aggregatedDeviceList.size();
				if (aggregatedDevicesCount == 0) {
					continue mainloop;
				}
				while (nextDevicesCollectionIterationTimestamp > System.currentTimeMillis()) {
					try {
						TimeUnit.MILLISECONDS.sleep(1000);
					} catch (InterruptedException e) {
						//
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
	 * Update the status of the device.
	 * The device is considered as paused if did not receive any retrieveMultipleStatistics()
	 * calls during {@link QSysReflectCommunicator#validRetrieveStatisticsTimestamp}
	 */
	private synchronized void updateAggregatorStatus() {
		devicePaused = validRetrieveStatisticsTimestamp < System.currentTimeMillis();
	}

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
	private static long nextDevicesCollectionIterationTimestamp;

	/**
	 * Executor that runs all the async operations, that {@link #deviceDataLoader} is posting
	 */
	private static ExecutorService executorService;

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
	 * List error message occur while fetching aggregated devices
	 */
	private List<String> deviceErrorMessagesList = Collections.synchronizedList(new ArrayList<>());

	/**
	 * List of error message occur while fetching system information
	 */
	private List<String> systemErrorMessagesList = Collections.synchronizedList(new ArrayList<>());

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
	 * {@inheritDoc}
	 */
	@Override
	public List<Statistics> getMultipleStatistics() throws Exception {
		Map<String, String> statistics = new HashMap<>();
		ExtendedStatistics extendedStatistics = new ExtendedStatistics();
		populateSystemData(statistics);
		extendedStatistics.setStatistics(statistics);
		if (!systemErrorMessagesList.isEmpty()) {
			synchronized (systemErrorMessagesList) {
				// remove duplicate exception string here due to Thread running every 30 seconds,-
				// so it may push the same error to systemErrorMessagesList
				Set<String> noDuplicateExceptionSet = new LinkedHashSet<>(systemErrorMessagesList);
				systemErrorMessagesList.clear();
				systemErrorMessagesList.addAll(noDuplicateExceptionSet);
				String errorMessage = systemErrorMessagesList.stream().map(Object::toString)
						.collect(Collectors.joining("\n"));
				throw new ResourceNotReachableException(errorMessage);
			}
		}
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
		executorService = Executors.newSingleThreadExecutor();
		executorService.submit(deviceDataLoader = new QSysDeviceDataLoader());
		validDeviceMetaDataRetrievalPeriodTimestamp = System.currentTimeMillis();
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
	protected void authenticate() throws Exception {
		//
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<AggregatedDevice> retrieveMultipleStatistics() throws Exception {
		if (executorService == null) {
			// Due to the bug that after changing properties on fly - the adapter is destroyed but adapter is not initialized properly,
			// so executor service is not running. We need to make sure executorService exists
			executorService = Executors.newSingleThreadExecutor();
			executorService.submit(deviceDataLoader = new QSysDeviceDataLoader());
		}
		nextDevicesCollectionIterationTimestamp = System.currentTimeMillis();
		updateValidRetrieveStatisticsTimestamp();
		if (!deviceErrorMessagesList.isEmpty()) {
			synchronized (deviceErrorMessagesList) {
				// remove duplicate exception string here due to Thread running every 30 seconds,-
				// so it may push the same error to deviceErrorMessagesList
				Set<String> noDuplicateExceptionSet = new LinkedHashSet<>(deviceErrorMessagesList);
				deviceErrorMessagesList.clear();
				deviceErrorMessagesList.addAll(noDuplicateExceptionSet);
				String errorMessage = deviceErrorMessagesList.stream().map(Object::toString)
						.collect(Collectors.joining("\n"));
				throw new ResourceNotReachableException(errorMessage);
			}
		}
		if (!aggregatedDeviceList.isEmpty()) {
			if (logger.isDebugEnabled()) {
				logger.debug("Populating aggregated devices' data and applying filter options");
			}
			List<AggregatedDevice> resultAggregatedDeviceList = cloneAggregatedDeviceList();
			populateDeviceUptimeAndStatusMessage(resultAggregatedDeviceList);
			resultAggregatedDeviceList = filterDeviceModel(resultAggregatedDeviceList);
			resultAggregatedDeviceList = filterDeviceStatusMessage(resultAggregatedDeviceList);
			return resultAggregatedDeviceList;
		}
		return aggregatedDeviceList;
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
					String newKey = entry.getKey();
					String newValue = entry.getValue();
					newProperties.put(newKey, newValue);
				}
				newClonedAggregatedDevice.setProperties(newProperties);
				resultAggregatedDeviceList.add(newClonedAggregatedDevice);
			}
		}
		return resultAggregatedDeviceList;
	}

	/**
	 * Filter list of aggregated devices based on device status message
	 *
	 * @param resultAggregatedDeviceList list of aggregated device
	 */
	private List<AggregatedDevice> filterDeviceStatusMessage(List<AggregatedDevice> resultAggregatedDeviceList) {
		if (filterStatusMessage != null && !QSysReflectConstant.DOUBLE_QUOTES.equals(filterStatusMessage)) {
			handleListFilterStatus();
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Applying device status message filter with values(s): %s", filterStatusMessage));
			}
			List<AggregatedDevice> filteredAggregatedDevice = new ArrayList<>();
			for (AggregatedDevice aggregatedDevice : resultAggregatedDeviceList) {
				for (String filterStatusMessageValue : filterStatusMessageValues) {
					Map<String, String> properties = aggregatedDevice.getProperties();
					if (properties.get(QSysReflectConstant.DEVICE_STATUS_MESSAGE).equals(filterStatusMessageValue)) {
						filteredAggregatedDevice.add(aggregatedDevice);
					}
				}
			}
			return filteredAggregatedDevice;
		}
		return resultAggregatedDeviceList;
	}

	/**
	 * Filter list of aggregated devices based on device model
	 *
	 * @param resultAggregatedDeviceList list of aggregated device
	 */
	private List<AggregatedDevice> filterDeviceModel(List<AggregatedDevice> resultAggregatedDeviceList) {
		if (filterModelName != null && !QSysReflectConstant.DOUBLE_QUOTES.equals(filterModelName)) {
			handleListFilterModel();
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Applying device model filter with values(s): %s", filterModelName));
			}
			List<AggregatedDevice> filteredAggregatedDevice = new ArrayList<>();
			for (AggregatedDevice aggregatedDevice : resultAggregatedDeviceList) {
				for (String filterDeviceModelValue : filterDeviceModelValues) {
					if (aggregatedDevice.getDeviceModel().equals(filterDeviceModelValue)) {
						filteredAggregatedDevice.add(aggregatedDevice);
					}
				}
			}
			return filteredAggregatedDevice;
		}
		return resultAggregatedDeviceList;
	}

	/**
	 * Populate normalize uptime and status message from the API
	 */
	private void populateDeviceUptimeAndStatusMessage(List<AggregatedDevice> resultAggregatedDeviceList) {
		for (int i = 0; i < resultAggregatedDeviceList.size(); i++) {
			AggregatedDevice aggregatedDevice = resultAggregatedDeviceList.get(i);
			Map<String, String> properties = aggregatedDevice.getProperties();
			long timeDiff = (System.currentTimeMillis() - Long.parseLong(properties.get(QSysReflectConstant.DEVICE_UPTIME))) / 1000;
			String normalUptimeString = timeDiff > 0 ? normalizeUptime(timeDiff) : QSysReflectConstant.NONE;
			properties.put(QSysReflectConstant.DEVICE_UPTIME, normalUptimeString);
			properties.put(QSysReflectConstant.DEVICE_STATUS_MESSAGE, deviceStatusMessageMap.get(aggregatedDevice.getDeviceId()));
			resultAggregatedDeviceList.set(i, aggregatedDevice);
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
	 * @param currentTimestamp Current time stamp
	 * @throws Exception Throw exception when fail to get response device
	 */
	private void retrieveDevices(long currentTimestamp) throws Exception {
		if (!aggregatedDeviceList.isEmpty() && validDeviceMetaDataRetrievalPeriodTimestamp > currentTimestamp) {
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Aggregated devices data retrieval is in cool down. %s seconds left",
						(validDeviceMetaDataRetrievalPeriodTimestamp - currentTimestamp) / 1000));
			}
			return;
		}
		try {
			validDeviceMetaDataRetrievalPeriodTimestamp = currentTimestamp + deviceMetaDataRetrievalTimeout;
			Map<String, PropertiesMapping> mapping = new PropertiesMappingParser().loadYML("qsysreflect/model-mapping.yml", getClass());
			AggregatedDeviceProcessor aggregatedDeviceProcessor = new AggregatedDeviceProcessor(mapping);
			String responseDeviceList = this.doGet(QSysReflectConstant.QSYS_URL_CORES, String.class);
			JsonNode devices = objectMapper.readTree(responseDeviceList);
			for (int i = 0; i < devices.size(); i++) {
				JsonNode currentDevice = devices.get(i);
				deviceStatusMessageMap.put(currentDevice.get(QSysReflectConstant.ID).asText(), currentDevice.get(QSysReflectConstant.STATUS).get(QSysReflectConstant.MESSAGE).asText());
			}
			aggregatedDeviceList = new ArrayList<>(aggregatedDeviceProcessor.extractDevices(devices));
			logger.error("Fail to get list of QSys devices.");
			nextDevicesCollectionIterationTimestamp = System.currentTimeMillis();
		} catch (Exception e) {
			String errorMessage = "Aggregated Device Data Retrieval-Error:"
					+ e.getMessage() + " with cause: " + e.getCause().getMessage();
			deviceErrorMessagesList.add(errorMessage);
			throw e;
		}
	}

	/**
	 * Get system information from the API
	 *
	 * @param currentTimestamp Current time stamp
	 * @throws Exception Throw exception when fail to convert to JsonNode
	 */
	public void retrieveSystemInfo(long currentTimestamp) throws Exception {
		// Retrieve system information every 30 seconds
		if (!systemResponseList.isEmpty() && validDeviceMetaDataRetrievalPeriodTimestamp > currentTimestamp) {
			return;
		}
		try {
			String systemResponse = this.doGet(QSysReflectConstant.QSYS_URL_SYSTEMS, String.class);
			JsonNode systems = objectMapper.readTree(systemResponse);
			synchronized (systemResponseList) {
				for (int i = 0; i < systems.size(); i++) {
					SystemResponse systemResponse1 = objectMapper.treeToValue(systems.get(i), SystemResponse.class);
					systemResponseList.add(systemResponse1);
				}
			}
		} catch (Exception e) {
			String errorMessage = "System Information Data Retrieval-Error:"
					+ e.getMessage() + " with cause: " + e.getCause().getMessage();
			systemErrorMessagesList.add(errorMessage);
			throw e;
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
			synchronized (systemResponseList) {
				for (SystemResponse systemResponse : systemResponseList) {
					stats.put(systemResponse.getName() + QSysReflectConstant.HASH_TAG + QSysReflectSystemMetric.SYSTEM_ID.getName(), String.valueOf(systemResponse.getId()));
					stats.put(systemResponse.getName() + QSysReflectConstant.HASH_TAG + QSysReflectSystemMetric.SYSTEM_CODE.getName(), String.valueOf(systemResponse.getCode()));
					stats.put(systemResponse.getName() + QSysReflectConstant.HASH_TAG + QSysReflectSystemMetric.SYSTEM_STATUS.getName(), String.valueOf(systemResponse.getStatusString()));
					if (systemResponse.getNormalAlert() != null && systemResponse.getFaultAlert() != null && systemResponse.getUnknownAlert() != null && systemResponse.getWarningAlert() != null) {
						stats.put(systemResponse.getName() + QSysReflectConstant.HASH_TAG + QSysReflectSystemMetric.ALERTS_NORMAL.getName(), String.valueOf(systemResponse.getNormalAlert()));
						stats.put(systemResponse.getName() + QSysReflectConstant.HASH_TAG + QSysReflectSystemMetric.ALERTS_WARNING.getName(), String.valueOf(systemResponse.getWarningAlert()));
						stats.put(systemResponse.getName() + QSysReflectConstant.HASH_TAG + QSysReflectSystemMetric.ALERTS_FAULT.getName(), String.valueOf(systemResponse.getFaultAlert()));
						stats.put(systemResponse.getName() + QSysReflectConstant.HASH_TAG + QSysReflectSystemMetric.ALERTS_UNKNOWN.getName(), String.valueOf(systemResponse.getUnknownAlert()));
					}
					stats.put(systemResponse.getName() + QSysReflectConstant.HASH_TAG + QSysReflectSystemMetric.DESIGN_NAME.getName(), String.valueOf(systemResponse.getDesignName()));
					stats.put(systemResponse.getName() + QSysReflectConstant.HASH_TAG + QSysReflectSystemMetric.DESIGN_PLATFORM.getName(), String.valueOf(systemResponse.getDesignPlatform()));
					long timeDiff = (System.currentTimeMillis() - systemResponse.getUptime()) / 1000;
					String normalUptimeString = timeDiff > 0 ? normalizeUptime(timeDiff) : QSysReflectConstant.NONE;
					stats.put(systemResponse.getName() + QSysReflectConstant.HASH_TAG + QSysReflectSystemMetric.UPTIME.getName(), normalUptimeString);
					stats.put(systemResponse.getName() + QSysReflectConstant.HASH_TAG + QSysReflectSystemMetric.CORE_NAME.getName(), String.valueOf(systemResponse.getCoreName()));
				}
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