/*
 * Copyright (c) 2022 AVI-SPL, Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.infrastructure.management.qsc.qsysreflect;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.aggregator.AggregatedDevice;
import com.avispl.symphony.api.dal.error.ResourceNotReachableException;
import com.avispl.symphony.dal.communicator.HttpCommunicator.AuthenticationScheme;

/**
 * Unit test for {@link QSysReflectCommunicator}.
 * Test monitoring data with all systems and aggregator device
 *
 * @author Ivan
 * @version 1.0
 * @since 1.0.0
 */
class QSysReflectCommunicatorTest {
	static QSysReflectCommunicator qSysReflectCommunicator;
	private static final int HTTP_PORT = 8088;
	private static final int HTTPS_PORT = 8443;
	private static final String HOST_NAME = "127.0.0.1";
	private static final String PROTOCOL = "http";

	@Rule
	WireMockRule wireMockRule = new WireMockRule(options().port(HTTP_PORT).httpsPort(HTTPS_PORT)
			.bindAddress(HOST_NAME));

	@BeforeEach
	public void init() throws Exception {
		wireMockRule.start();
		qSysReflectCommunicator = new QSysReflectCommunicator();
		qSysReflectCommunicator.setTrustAllCertificates(false);
		qSysReflectCommunicator.setProtocol(PROTOCOL);
		qSysReflectCommunicator.setPort(wireMockRule.port());
		qSysReflectCommunicator.setHost(HOST_NAME);
		qSysReflectCommunicator.setContentType("application/json");
		qSysReflectCommunicator.setPassword("57cfe39c35d7df9fde6f24008854e078225ddbd4bbf8cbdbd04284c8f333c454");
		qSysReflectCommunicator.init();
		qSysReflectCommunicator.authenticate();
	}

	@AfterEach
	void stopWireMockRule() {
		qSysReflectCommunicator.destroy();
		wireMockRule.stop();
	}

	/**
	 * Test getMultipleStatistics get all current system
	 * Expect getMultipleStatistics successfully with three systems
	 */
	@Tag("Mock")
	@Test
	void testGetMultipleStatistics() throws Exception {
		qSysReflectCommunicator.retrieveMultipleStatistics();
		Thread.sleep(30000);
		ExtendedStatistics extendedStatistics = (ExtendedStatistics) qSysReflectCommunicator.getMultipleStatistics().get(0);
		Map<String, String> stats = extendedStatistics.getStatistics();
		Assert.assertEquals(24, stats.size());

		Assert.assertEquals("9468", stats.get("AVISPL Test Core110f" + "#" + "SystemId"));
		Assert.assertEquals("3-440F59FA6034C59670FF3C0928929607", stats.get("AVISPL Test Core110f" + "#" + "SystemCode"));
		Assert.assertEquals("Running", stats.get("AVISPL Test Core110f" + "#" + "SystemStatus"));
		Assert.assertEquals("15", stats.get("AVISPL Test Core110f" + "#" + "AlertsNormal"));
		Assert.assertEquals("0", stats.get("AVISPL Test Core110f" + "#" + "AlertsWarning"));
		Assert.assertEquals("0", stats.get("AVISPL Test Core110f" + "#" + "AlertsFault"));
		Assert.assertEquals("0", stats.get("AVISPL Test Core110f" + "#" + "AlertsUnknown"));
		Assert.assertEquals("CeeSalt_TestCore_v3.1", stats.get("AVISPL Test Core110f" + "#" + "DesignName"));
		Assert.assertEquals("Core 110f", stats.get("AVISPL Test Core110f" + "#" + "DesignPlatform"));
		Assert.assertEquals("CeeSalt-Core110f", stats.get("AVISPL Test Core110f" + "#" + "CoreName"));

		Assert.assertEquals("10028", stats.get("Base Classroom Updated v7" + "#" + "SystemId"));
		Assert.assertEquals("3-06AC3AB31F07DD0118B29EE65183499E", stats.get("Base Classroom Updated v7" + "#" + "SystemCode"));
		Assert.assertEquals("Running", stats.get("Base Classroom Updated v7" + "#" + "SystemStatus"));
		Assert.assertEquals("8", stats.get("Base Classroom Updated v7" + "#" + "AlertsNormal"));
		Assert.assertEquals("0", stats.get("Base Classroom Updated v7" + "#" + "AlertsWarning"));
		Assert.assertEquals("2", stats.get("Base Classroom Updated v7" + "#" + "AlertsFault"));
		Assert.assertEquals("0", stats.get("Base Classroom Updated v7" + "#" + "AlertsUnknown"));
		Assert.assertEquals("Base Classroom Updated v7", stats.get("Base Classroom Updated v7" + "#" + "DesignName"));
		Assert.assertEquals("NV-32-H (Core Mode)", stats.get("Base Classroom Updated v7" + "#" + "DesignPlatform"));
		Assert.assertEquals("nv-32-h-e159", stats.get("Base Classroom Updated v7" + "#" + "CoreName"));
	}

	/**
	 * Test retrieveMultipleStatistics
	 * Expect retrieveMultipleStatistics successfully with five aggregator device
	 */
	@Tag("Mock")
	@Test
	void testGetAggregatorData() throws Exception {
		qSysReflectCommunicator.retrieveMultipleStatistics();
		Thread.sleep(30000);
		List<AggregatedDevice> aggregatedDeviceList = qSysReflectCommunicator.retrieveMultipleStatistics();
		Assert.assertEquals(5, aggregatedDeviceList.size());
		Assert.assertEquals("Core 510i", aggregatedDeviceList.get(0).getDeviceModel());
		Assert.assertEquals("Core 110f", aggregatedDeviceList.get(1).getDeviceModel());
		Assert.assertEquals("Core 110", aggregatedDeviceList.get(2).getDeviceModel());
		Assert.assertEquals("AC-32-H (Core Mode)", aggregatedDeviceList.get(3).getDeviceModel());
		Assert.assertEquals("NV-32-H (Core Mode)", aggregatedDeviceList.get(4).getDeviceModel());
	}

	/**
	 * Test retrieveMultipleStatistics with FilterModelName is running
	 * Expect retrieveMultipleStatistics successfully with aggregator device running
	 */
	@Tag("Mock")
	@Test
	void testFilterStatusMessageIsRunning() throws Exception {
		qSysReflectCommunicator.setFilterDeviceStatusMessage("Running");
		qSysReflectCommunicator.retrieveMultipleStatistics();
		Thread.sleep(30000);
		List<AggregatedDevice> aggregatedDeviceList = qSysReflectCommunicator.retrieveMultipleStatistics();
		Assert.assertEquals(4, aggregatedDeviceList.size());
		Assert.assertEquals("Core 510i", aggregatedDeviceList.get(0).getDeviceModel());
		Assert.assertEquals("Core 110f", aggregatedDeviceList.get(1).getDeviceModel());
		Assert.assertEquals("Core 110", aggregatedDeviceList.get(2).getDeviceModel());
		Assert.assertEquals("NV-32-H (Core Mode)", aggregatedDeviceList.get(3).getDeviceModel());
	}

	/**
	 * Test retrieveMultipleStatistics with FilterModelName
	 * Expect retrieveMultipleStatistics successfully with AggregatorData
	 */
	@Tag("Mock")
	@Test
	void testFilterModelName() throws Exception {
		qSysReflectCommunicator.setFilterModel("Core 110f,Core 510i");
		qSysReflectCommunicator.retrieveMultipleStatistics();
		Thread.sleep(30000);
		List<AggregatedDevice> aggregatedDeviceList = qSysReflectCommunicator.retrieveMultipleStatistics();
		Assert.assertEquals(2, aggregatedDeviceList.size());
		Assert.assertEquals("Core 510i", aggregatedDeviceList.get(0).getDeviceModel());
		Assert.assertEquals("Core 110f", aggregatedDeviceList.get(1).getDeviceModel());
	}

	/**
	 * Test retrieveMultipleStatistics with listDeviceId
	 * Expect retrieveMultipleStatistics with listDeviceId successfully
	 */
	@Tag("Mock")
	@Test
	void testRetrieveMultipleStatisticsWithListDeviceId() throws Exception {
		List<String> deviceList = new ArrayList<>();
		deviceList.add("9440");
		deviceList.add("11928");
		qSysReflectCommunicator.retrieveMultipleStatistics(deviceList);
		qSysReflectCommunicator.setDeviceMetaDataRetrievalTimeout(90000);
		Thread.sleep(30000);
		List<AggregatedDevice> aggregatedDeviceList = qSysReflectCommunicator.retrieveMultipleStatistics(deviceList);

		AggregatedDevice aggregatedDevice = aggregatedDeviceList.get(0);
		Assert.assertEquals("9440", aggregatedDevice.getDeviceId());
		Assert.assertEquals("3-3F23AA07A6C4E22F526A88C3A5B0D217", aggregatedDevice.getSerialNumber());
		Assert.assertEquals("Running", aggregatedDevice.getProperties().get("deviceStatusMessage"));
		Assert.assertEquals("9.2.1-2110.001", aggregatedDevice.getProperties().get("firmwareVersion"));
		Assert.assertEquals("Schaumburg Office", aggregatedDevice.getProperties().get("siteName"));
		Assert.assertEquals("Core 510i", aggregatedDevice.getDeviceModel());
		Assert.assertEquals("CHI-MillPark-DSP01", aggregatedDevice.getDeviceName());

		Assert.assertEquals("11928", aggregatedDeviceList.get(1).getDeviceId());
		Assert.assertEquals("3-440F59FA6034C59670FF3C0928929607", aggregatedDeviceList.get(1).getSerialNumber());
		Assert.assertEquals("Running", aggregatedDeviceList.get(1).getProperties().get("deviceStatusMessage"));
		Assert.assertEquals("9.2.1-2110.001", aggregatedDeviceList.get(1).getProperties().get("firmwareVersion"));
		Assert.assertEquals("AVI-SPL-LAB", aggregatedDeviceList.get(1).getProperties().get("siteName"));
		Assert.assertEquals("Core 110f", aggregatedDeviceList.get(1).getDeviceModel());
		Assert.assertEquals("CeeSalt-Core110f", aggregatedDeviceList.get(1).getDeviceName());
	}

	/**
	 * Test retrieveMultipleStatistics with FilterModelName not exists or empty
	 * Expect retrieveMultipleStatistics is empty
	 */
	@Tag("Mock")
	@Test
	void testFilterModelNameNotExists() throws Exception {
		qSysReflectCommunicator.setFilterModel(",Core 100");
		qSysReflectCommunicator.retrieveMultipleStatistics();
		Thread.sleep(30000);
		List<AggregatedDevice> aggregatedDeviceList = qSysReflectCommunicator.retrieveMultipleStatistics();
		Assert.assertTrue(aggregatedDeviceList.isEmpty());
		Assert.assertEquals(0, aggregatedDeviceList.size());
	}

	/**
	 * Test retrieveMultipleStatistics with listDeviceId not exits
	 * Expect retrieveMultipleStatistics is empty
	 */
	@Tag("Mock")
	@Test
	void testRetrieveMultipleStatisticsWithListDeviceIdIsNotExists() throws Exception {
		List<String> deviceList = new ArrayList<>();
		deviceList.add("94400");
		qSysReflectCommunicator.retrieveMultipleStatistics(deviceList);
		Thread.sleep(30000);
		List<AggregatedDevice> aggregatedDeviceList = qSysReflectCommunicator.retrieveMultipleStatistics(deviceList);
		Assert.assertTrue(aggregatedDeviceList.isEmpty());
		Assert.assertEquals(0, aggregatedDeviceList.size());
	}

	/**
	 * Test retrieveMultipleStatistics with FilterModelName is Idle: no device installed
	 * Expect retrieveMultipleStatistics successfully with aggregator device
	 */
	@Tag("Mock")
	@Test
	void testFilterStatusMessageNoDeviceInstalled() throws Exception {
		qSysReflectCommunicator.setFilterDeviceStatusMessage("Idle: no device installed");
		qSysReflectCommunicator.retrieveMultipleStatistics();
		Thread.sleep(30000);
		List<AggregatedDevice> aggregatedDeviceList = qSysReflectCommunicator.retrieveMultipleStatistics();
		Assert.assertFalse(aggregatedDeviceList.isEmpty());
		Assert.assertEquals(1, aggregatedDeviceList.size());
		Assert.assertEquals("AC-32-H (Core Mode)", aggregatedDeviceList.get(0).getDeviceModel());
	}

	/**
	 * Test retrieveMultipleStatistics with Device metadata retrieval timeout
	 * Expect retrieveMultipleStatistics with listDeviceId successfully
	 */
	@Tag("Mock")
	@Test
	void testRetrieveMultipleStatisticsWithDeviceMetaDataRetrievalTimeout() throws Exception {
		List<String> deviceList = new ArrayList<>();
		deviceList.add("9440");
		qSysReflectCommunicator.retrieveMultipleStatistics(deviceList);
		qSysReflectCommunicator.setDeviceMetaDataRetrievalTimeout(90000);
		Thread.sleep(30000);
		qSysReflectCommunicator.retrieveMultipleStatistics(deviceList);
		Thread.sleep(30000);
		qSysReflectCommunicator.retrieveMultipleStatistics(deviceList);
		Thread.sleep(30000);
		qSysReflectCommunicator.retrieveMultipleStatistics(deviceList);
		Thread.sleep(30000);
		List<AggregatedDevice> aggregatedDeviceList = qSysReflectCommunicator.retrieveMultipleStatistics(deviceList);

		AggregatedDevice aggregatedDevice = aggregatedDeviceList.get(0);
		Assert.assertEquals("9440", aggregatedDevice.getDeviceId());
		Assert.assertEquals("3-3F23AA07A6C4E22F526A88C3A5B0D217", aggregatedDevice.getSerialNumber());
		Assert.assertEquals("Running", aggregatedDevice.getProperties().get("deviceStatusMessage"));
		Assert.assertEquals("9.2.1-2110.001", aggregatedDevice.getProperties().get("firmwareVersion"));
		Assert.assertEquals("Schaumburg Office", aggregatedDevice.getProperties().get("siteName"));
		Assert.assertEquals("Core 510i", aggregatedDevice.getDeviceModel());
		Assert.assertEquals("CHI-MillPark-DSP01", aggregatedDevice.getDeviceName());
	}

	/**
	 * Test getMultipleStatistics with invalid authentication
	 * Expect getMultipleStatistics failed
	 */
	@Tag("RealDevice")
	@Test
	void testEnterTokenNotCorrect() throws Exception {
		qSysReflectCommunicator.destroy();
		qSysReflectCommunicator.setTrustAllCertificates(false);
		qSysReflectCommunicator.setProtocol("https");
		qSysReflectCommunicator.setContentType("application/json");
		qSysReflectCommunicator.setHost("reflect.qsc.com");
		qSysReflectCommunicator.setPort(443);
		qSysReflectCommunicator.setAuthenticationScheme(AuthenticationScheme.Basic);
		qSysReflectCommunicator.setLogin("tokenString");
		qSysReflectCommunicator.setPassword("57cfe39c35d7df9fde6f24008854e078225ddbd4bbf8cbdbd04284c8f333c454222");
		qSysReflectCommunicator.init();
		qSysReflectCommunicator.retrieveMultipleStatistics();
		Thread.sleep(30000);
		assertThrows(Exception.class, () -> qSysReflectCommunicator.getMultipleStatistics(), "Expect fail here due to api token not correct");
	}

	/**
	 * Test getMultipleStatistics with invalid authentication
	 * Expect getMultipleStatistics failed
	 */
	@Tag("RealDevice")
	@Test
	void testEnterTokenIsEmpty() throws Exception {
		qSysReflectCommunicator.destroy();
		qSysReflectCommunicator.setTrustAllCertificates(false);
		qSysReflectCommunicator.setProtocol("https");
		qSysReflectCommunicator.setContentType("application/json");
		qSysReflectCommunicator.setHost("reflect.qsc.com");
		qSysReflectCommunicator.setPort(443);
		qSysReflectCommunicator.setAuthenticationScheme(AuthenticationScheme.Basic);
		qSysReflectCommunicator.setLogin("tokenString");
		qSysReflectCommunicator.setPassword("");
		// Worker thread won't be initialized due to API token is empty
		qSysReflectCommunicator.init();
		qSysReflectCommunicator.retrieveMultipleStatistics();
		assertThrows(ResourceNotReachableException.class, () -> qSysReflectCommunicator.getMultipleStatistics(), "Expect fail here due to api token is empty");
	}

	/**
	 * Test getMultipleStatistics get all current system with real device
	 * Expect getMultipleStatistics successfully with three systems
	 */
	@Tag("RealDevice")
	@Test
	void testGetMultipleStatisticsWithRealDevice() throws Exception {
		qSysReflectCommunicator.destroy();
		qSysReflectCommunicator.setTrustAllCertificates(false);
		qSysReflectCommunicator.setProtocol("https");
		qSysReflectCommunicator.setContentType("application/json");
		qSysReflectCommunicator.setHost("reflect.qsc.com");
		qSysReflectCommunicator.setPort(443);
		qSysReflectCommunicator.setAuthenticationScheme(AuthenticationScheme.Basic);
		qSysReflectCommunicator.setLogin("tokenString");
		qSysReflectCommunicator.setPassword("57cfe39c35d7df9fde6f24008854e078225ddbd4bbf8cbdbd04284c8f333c454");
		qSysReflectCommunicator.init();
		qSysReflectCommunicator.retrieveMultipleStatistics();
		Thread.sleep(30000);
		ExtendedStatistics extendedStatistics = (ExtendedStatistics) qSysReflectCommunicator.getMultipleStatistics().get(0);
		Map<String, String> stats = extendedStatistics.getStatistics();
		Assert.assertEquals(36, stats.size());

		Assert.assertEquals("7725", stats.get("MillenniumPark" + "#" + "SystemId"));
		Assert.assertEquals("3-3F23AA07A6C4E22F526A88C3A5B0D217", stats.get("MillenniumPark" + "#" + "SystemCode"));
		Assert.assertEquals("Running", stats.get("MillenniumPark" + "#" + "SystemStatus"));
		Assert.assertEquals("10", stats.get("MillenniumPark" + "#" + "AlertsNormal"));
		Assert.assertEquals("0", stats.get("MillenniumPark" + "#" + "AlertsWarning"));
		Assert.assertEquals("0", stats.get("MillenniumPark" + "#" + "AlertsFault"));
		Assert.assertEquals("0", stats.get("MillenniumPark" + "#" + "AlertsUnknown"));
		Assert.assertEquals("AVISPL_Millennium_Park_v4.3", stats.get("MillenniumPark" + "#" + "DesignName"));
		Assert.assertEquals("Core 510i", stats.get("MillenniumPark" + "#" + "DesignPlatform"));
		Assert.assertEquals("CHI-MillPark-DSP01", stats.get("MillenniumPark" + "#" + "CoreName"));

		Assert.assertEquals("10028", stats.get("Base Classroom Updated v7" + "#" + "SystemId"));
		Assert.assertEquals("3-06AC3AB31F07DD0118B29EE65183499E", stats.get("Base Classroom Updated v7" + "#" + "SystemCode"));
		Assert.assertEquals("Running", stats.get("Base Classroom Updated v7" + "#" + "SystemStatus"));
		Assert.assertEquals("8", stats.get("Base Classroom Updated v7" + "#" + "AlertsNormal"));
		Assert.assertEquals("0", stats.get("Base Classroom Updated v7" + "#" + "AlertsWarning"));
		Assert.assertEquals("2", stats.get("Base Classroom Updated v7" + "#" + "AlertsFault"));
		Assert.assertEquals("0", stats.get("Base Classroom Updated v7" + "#" + "AlertsUnknown"));
		Assert.assertEquals("Base Classroom Updated v7", stats.get("Base Classroom Updated v7" + "#" + "DesignName"));
		Assert.assertEquals("NV-32-H (Core Mode)", stats.get("Base Classroom Updated v7" + "#" + "DesignPlatform"));
		Assert.assertEquals("nv-32-h-e159", stats.get("Base Classroom Updated v7" + "#" + "CoreName"));

		Assert.assertEquals("9468", stats.get("AVISPL Test Core110f" + "#" + "SystemId"));
		Assert.assertEquals("3-440F59FA6034C59670FF3C0928929607", stats.get("AVISPL Test Core110f" + "#" + "SystemCode"));
		Assert.assertEquals("Running", stats.get("AVISPL Test Core110f" + "#" + "SystemStatus"));
		Assert.assertEquals("14", stats.get("AVISPL Test Core110f" + "#" + "AlertsNormal"));
		Assert.assertEquals("1", stats.get("AVISPL Test Core110f" + "#" + "AlertsWarning"));
		Assert.assertEquals("0", stats.get("AVISPL Test Core110f" + "#" + "AlertsFault"));
		Assert.assertEquals("0", stats.get("AVISPL Test Core110f" + "#" + "AlertsUnknown"));
		Assert.assertEquals("CeeSalt_TestCore_v3.1", stats.get("AVISPL Test Core110f" + "#" + "DesignName"));
		Assert.assertEquals("Core 110f", stats.get("AVISPL Test Core110f" + "#" + "DesignPlatform"));
		Assert.assertEquals("CeeSalt-Core110f", stats.get("AVISPL Test Core110f" + "#" + "CoreName"));
	}
}