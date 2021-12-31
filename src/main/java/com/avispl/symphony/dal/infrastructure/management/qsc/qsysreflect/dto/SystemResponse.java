/*
 * Copyright (c) 2021 AVI-SPL, Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.infrastructure.management.qsc.qsysreflect.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import com.avispl.symphony.dal.infrastructure.management.qsc.qsysreflect.utils.SystemResponseDeserializer;

/**
 * System Response DTO class
 *
 * @author Duy Nguyen
 * @version 1.0.0
 * @since 1.0.0
 */
@JsonDeserialize(using = SystemResponseDeserializer.class)
public class SystemResponse {

	private int id;
	private String code;
	private String name;
	private String statusString;
	private Integer normalAlert;
	private Integer warningAlert;
	private Integer faultAlert;
	private Integer unknownAlert;
	private String designName;
	private String designPlatform;
	private Long uptime;
	private String coreName;

	/**
	 * Retrieves {@code {@link #id}}
	 *
	 * @return value of {@link #id}
	 */
	public int getId() {
		return id;
	}

	/**
	 * Sets {@code id}
	 *
	 * @param id the {@code int} field
	 */
	public void setId(int id) {
		this.id = id;
	}

	/**
	 * Retrieves {@code {@link #code}}
	 *
	 * @return value of {@link #code}
	 */
	public String getCode() {
		return code;
	}

	/**
	 * Sets {@code code}
	 *
	 * @param code the {@code java.lang.String} field
	 */
	public void setCode(String code) {
		this.code = code;
	}

	/**
	 * Retrieves {@code {@link #name}}
	 *
	 * @return value of {@link #name}
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets {@code name}
	 *
	 * @param name the {@code java.lang.String} field
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Retrieves {@code {@link #statusString}}
	 *
	 * @return value of {@link #statusString}
	 */
	public String getStatusString() {
		return statusString;
	}

	/**
	 * Sets {@code statusString}
	 *
	 * @param statusString the {@code java.lang.String} field
	 */
	public void setStatusString(String statusString) {
		this.statusString = statusString;
	}

	/**
	 * Retrieves {@code {@link #normalAlert}}
	 *
	 * @return value of {@link #normalAlert}
	 */
	public Integer getNormalAlert() {
		return normalAlert;
	}

	/**
	 * Sets {@code normalAlert}
	 *
	 * @param normalAlert the {@code int} field
	 */
	public void setNormalAlert(Integer normalAlert) {
		this.normalAlert = normalAlert;
	}

	/**
	 * Retrieves {@code {@link #warningAlert}}
	 *
	 * @return value of {@link #warningAlert}
	 */
	public Integer getWarningAlert() {
		return warningAlert;
	}

	/**
	 * Sets {@code warningAlert}
	 *
	 * @param warningAlert the {@code int} field
	 */
	public void setWarningAlert(Integer warningAlert) {
		this.warningAlert = warningAlert;
	}

	/**
	 * Retrieves {@code {@link #faultAlert}}
	 *
	 * @return value of {@link #faultAlert}
	 */
	public Integer getFaultAlert() {
		return faultAlert;
	}

	/**
	 * Sets {@code faultAlert}
	 *
	 * @param faultAlert the {@code int} field
	 */
	public void setFaultAlert(Integer faultAlert) {
		this.faultAlert = faultAlert;
	}

	/**
	 * Retrieves {@code {@link #unknownAlert}}
	 *
	 * @return value of {@link #unknownAlert}
	 */
	public Integer getUnknownAlert() {
		return unknownAlert;
	}

	/**
	 * Sets {@code unknownAlert}
	 *
	 * @param unknownAlert the {@code int} field
	 */
	public void setUnknownAlert(Integer unknownAlert) {
		this.unknownAlert = unknownAlert;
	}

	/**
	 * Retrieves {@code {@link #designName}}
	 *
	 * @return value of {@link #designName}
	 */
	public String getDesignName() {
		return designName;
	}

	/**
	 * Sets {@code designName}
	 *
	 * @param designName the {@code java.lang.String} field
	 */
	public void setDesignName(String designName) {
		this.designName = designName;
	}

	/**
	 * Retrieves {@code {@link #designPlatform}}
	 *
	 * @return value of {@link #designPlatform}
	 */
	public String getDesignPlatform() {
		return designPlatform;
	}

	/**
	 * Sets {@code designPlatform}
	 *
	 * @param designPlatform the {@code java.lang.String} field
	 */
	public void setDesignPlatform(String designPlatform) {
		this.designPlatform = designPlatform;
	}

	/**
	 * Retrieves {@code {@link #uptime}}
	 *
	 * @return value of {@link #uptime}
	 */
	public Long getUptime() {
		return uptime;
	}

	/**
	 * Sets {@code uptime}
	 *
	 * @param uptime the {@code java.lang.Long} field
	 */
	public void setUptime(Long uptime) {
		this.uptime = uptime;
	}

	/**
	 * Retrieves {@code {@link #coreName}}
	 *
	 * @return value of {@link #coreName}
	 */
	public String getCoreName() {
		return coreName;
	}

	/**
	 * Sets {@code coreName}
	 *
	 * @param coreName the {@code java.lang.String} field
	 */
	public void setCoreName(String coreName) {
		this.coreName = coreName;
	}
}
