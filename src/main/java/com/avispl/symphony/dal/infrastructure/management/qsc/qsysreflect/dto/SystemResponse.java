package com.avispl.symphony.dal.infrastructure.management.qsc.qsysreflect.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import com.avispl.symphony.dal.infrastructure.management.qsc.qsysreflect.util.SystemResponseDeserializer;

@JsonDeserialize(using = SystemResponseDeserializer.class)
public class SystemResponse {

	private int id;
	private String code;
	private String name;
	private String statusString;
	private int normalCore;
	private int warningCore;
	private int faultCore;
	private int unknownCore;

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
	 * Retrieves {@code {@link #normalCore}}
	 *
	 * @return value of {@link #normalCore}
	 */
	public int getNormalCore() {
		return normalCore;
	}

	/**
	 * Sets {@code normalCore}
	 *
	 * @param normalCore the {@code int} field
	 */
	public void setNormalCore(int normalCore) {
		this.normalCore = normalCore;
	}

	/**
	 * Retrieves {@code {@link #warningCore}}
	 *
	 * @return value of {@link #warningCore}
	 */
	public int getWarningCore() {
		return warningCore;
	}

	/**
	 * Sets {@code warningCore}
	 *
	 * @param warningCore the {@code int} field
	 */
	public void setWarningCore(int warningCore) {
		this.warningCore = warningCore;
	}

	/**
	 * Retrieves {@code {@link #faultCore}}
	 *
	 * @return value of {@link #faultCore}
	 */
	public int getFaultCore() {
		return faultCore;
	}

	/**
	 * Sets {@code faultCore}
	 *
	 * @param faultCore the {@code int} field
	 */
	public void setFaultCore(int faultCore) {
		this.faultCore = faultCore;
	}

	/**
	 * Retrieves {@code {@link #unknownCore}}
	 *
	 * @return value of {@link #unknownCore}
	 */
	public int getUnknownCore() {
		return unknownCore;
	}

	/**
	 * Sets {@code unknownCore}
	 *
	 * @param unknownCore the {@code int} field
	 */
	public void setUnknownCore(int unknownCore) {
		this.unknownCore = unknownCore;
	}

	@Override
	public String toString() {
		return "SystemResponse{" +
				"id=" + id +
				", code='" + code + '\'' +
				", name='" + name + '\'' +
				", statusString='" + statusString + '\'' +
				", normalCore=" + normalCore +
				", warningCore=" + warningCore +
				", faultCore=" + faultCore +
				", unknownCore=" + unknownCore +
				'}';
	}
}
