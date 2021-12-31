/*
 * Copyright (c) 2021 AVI-SPL, Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.infrastructure.management.qsc.qsysreflect.utils;

/**
 * Metric for QSys Reflect System Response
 *
 * @author Duy Nguyen
 * @version 1.0.0
 * @since 1.0.0
 */
public enum QSysReflectSystemMetric {
	SYSTEM_ID("SystemId"),
	SYSTEM_CODE("SystemCode"),
	SYSTEM_STATUS("SystemStatus"),
	ALERTS_NORMAL("AlertsNormal"),
	ALERTS_WARNING("AlertsWarning"),
	ALERTS_FAULT("AlertsFault"),
	ALERTS_UNKNOWN("AlertsUnknown"),
	DESIGN_NAME("DesignName"),
	DESIGN_PLATFORM("DesignPlatform"),
	UPTIME("Uptime"),
	CORE_NAME("CoreName");

	private final String name;

	/**
	 * QSysReflectSystemMetric with args constructor
	 * @param name metric name
	 */
	QSysReflectSystemMetric(String name) {
		this.name = name;
	}

	/**
	 * Retrieves {@code {@link #name}}
	 *
	 * @return value of {@link #name}
	 */
	public String getName() {
		return name;
	}
}
