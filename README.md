# Q-SYS Reflect Enterprise Manager Integration - Capabilities & Configuration

This document covers Q-SYS Reflect Enterprise Manager Aggregator Capabilities and Configuration.

Note: Not to be confused with Q-SYS Core Aggregator.

Symphony integrates with Q-SYS Reflect Enterprise Manager to provide comprehensive monitoring of the Q-SYS audio, video, and control (AV&C) ecosystem.

## Main use cases for Q-SYS Reflect Integration
- **Monitor** Q-SYS Core system health, device status, and alert conditions across the organization
- **Track** individual device details - firmware version, uptime, serial number, site information, and device status messages
- **Filter** monitored devices by model, type, system name, or device status message to focus on relevant assets
- **Inventory** keep Q-SYS Cores and associated AV devices (streaming I/O, cameras, conferencing endpoints, touch screens, etc.) in check

## Prerequisites for Q-SYS Reflect Device Connection Setup 
Q-SYS Reflect Aggregator communicates with the Q-SYS Reflect Enterprise Manager API on behalf of an authenticated account using an API Token.

To get started:
- Log in to Q-SYS Reflect Enterprise Manager at https://reflect.qsc.com
- Generate a Q-SYS Reflect API Token from your account settings
- Use the API Token as the Password field during Symphony device configuration (Username is not required)

For detailed information on obtaining the API token, refer to the Q-SYS Reflect documentation -> https://reflect.qsc.com/help/Content/QREM_API_Example.htm

## Q-SYS Reflect Device Connection Setup and Provisioning

Note: The connection configuration below describe a successful Q-SYS Reflect Enterprise Manager integration setup, these should not be confused with the adapter configuration properties. They are not to be infered as troubleshooting checks and should not be used when diagnosing specific errors unless a troubleshooting entry (provided in the Troubleshooting section) explicitly references them.

Once the API Token is obtained, use it as the Password for the Symphony device configuration.

Devices and models supported by the Q-SYS Reflect Enterprise Manager integration are Q-SYS Core devices and Non Q-SYS peripheral devices, including Streaming I/O, AV Source devices, Cameras, HDMI Displays, Conferencing devices, Touch Screens, and more.

The aggregated Q-SYS devices will be available as aggregated devices with different models.

Devices that have the Q-SYS Reflect Aggregator device set as Monitoring Proxy are devices monitored by the Q-SYS Reflect Aggregator.

Create the Reflect device with Monitoring Service -> Advanced Monitoring, the HTTP management protocol must be selected:

| Field | Value |
|---|---|
| Device Type | Infrastructure |
| Category | Management |
| Manufacturer | QSC |
| Model | Reflect |
| Monitoring Service | Advanced Monitoring |
| Monitoring Source | Direct |
| Management Address | reflect.qsc.com |
| Protocol | HTTP |
| Username | (not required - leave blank) |
| Password | Q-SYS Reflect API Token |
| Port Number | 443 by default |

When the device is configured, saved, and set active, the Q-SYS Reflect Aggregator will start communicating with the Reflect API to retrieve data about registered Q-SYS systems and devices based on the provided configuration.

By default, the unprovisioned devices will appear on Aggregated Devices -> Unprovisioned Devices tab.

To import a Q-SYS device for monitoring by the Q-SYS Reflect Aggregator:
1. Open Aggregated Devices
2. Click the (+) icon on the unprovisioned device, or select unprovisioned devices from the list and click Import
3. Click OK to confirm import

Once successfully imported, the device will display an up-arrow icon indicating it is actively monitored by the Q-SYS Reflect Aggregator.

Devices and available device data can be tuned by adapter configuration properties:

| Property | Description | Value |
|---|---|---|
| filterModel | List of models (separated by commas) to filter monitored devices | Blank by default - filter won't be applied |
| filterDeviceStatusMessage | List of device status messages (separated by commas) to filter monitored devices. Available statuses: Running, Idle: no System installed, Starting, Stopping, Offline, Unknown, OK, Initializing, Compromised, Missing, Fault, Not Present | Blank by default - filter won't be applied |
| filterSystemName | List of Q-SYS system names (separated by commas) to filter monitored devices | Blank by default - filter won't be applied |
| filterType | List of device types (separated by commas) to filter monitored devices | Blank by default - filter won't be applied |

Note: The AND condition is used when multiple filters are specified simultaneously.

For detailed information on the aggregator and its configuration, please refer to our knowledgebase -> https://symphony.knowledgeowl.com/help/q-sys-reflect-enterprise-manager-aggregator-technical-breakdown

## Available Monitored Data for Q-SYS Reflect Aggregator

Q-SYS Reflect Aggregator monitored data consists of 2 parts: Aggregator extended properties and Device extended properties.

Aggregator properties include service information - Adapter Metadata (AdapterBuildDate, AdapterUptime, AdapterUptime(min), AdapterVersion, LastMonitoringCycleDuration, MonitoredDevicesTotal, MonitoringCycleInterval).

Aggregated Devices (Q-SYS Cores and peripheral devices) provide the following monitoring capabilities:

| Property Type | Description | Properties |
|---|---|---|
| Device metadata (Q-SYS Core) | Identification and operational details for Q-SYS Core devices | Firmware Version, Device Id, Device Model, Device Name, Device Status Message, Device Uptime, Device Type, Serial Number, Site Id, Site Name |
| System-level properties (Q-SYS Core) | System-wide platform and runtime information | Core Name, Design Name, Design Platform, Model, System Code, System Id, SystemStatus, Uptime |
| Alerts (Q-SYS Core only) | Active alert and health status categories. Only displayed if at least one alert is present on the system | Alerts Fault, Alerts Normal, Alerts Unknown, Alerts Warning |
| Device metadata (non-Core devices) | Identity and status information for peripheral devices such as streaming I/O, AV Source, Camera, HDMI Display, Conferencing, Touch Screen, etc. | Device Id, Device Model, Device Name, Device Status Message, Device Type, Manufacturer, Location, Started At |

Note: Monitoring and Control Capabilities may depend on the device model.

### Device Status Messages

Available statuses when aggregated device model is Q-SYS Core:

| Status | Note |
|---|---|
| Running | The device is functioning normally |
| Idle: no System installed | The device is not installed |
| Starting | The device is in the process of a firmware or configuration update, or the design is starting |
| Stopping | The device is in the process of shutting down |
| Offline | The device cannot be reached |

Available statuses when aggregated device model is not Q-SYS Core:

| Status | Note |
|---|---|
| Unknown | Appears during a Core reboot (e.g. during a firmware update), or when a design is being pushed to the Core and before it has started running |
| OK | The device is functioning normally |
| Initializing | The device is in the process of a firmware or configuration update, or the design is starting |
| Compromised | The device is functioning, but a non-fatal problem exists |
| Missing | The device cannot be discovered |
| Fault | The device is malfunctioning or is mis-configured |
| Not Present | Appears when the device is not connected to the network and the Is Required component property is set to 'No' |

## Troubleshooting for Q-SYS Reflect Enterprise Manager Integration

**Troubleshooting guidance**
- If an error occurs, focus only on troubleshooting steps that are provided in the section below.
- Do not include prerequisite/setup information.
- Do not add unrelated configuration details from other sections.
- If the document does not provide a direct error troubleshooting step, state that the document does not contain enough guidance for that specific issue.

**Login Error**
- Ensure the Symphony device Password field contains the correct and valid API Token value
- Verify the Management Address is set to reflect.qsc.com and Port is 443

**API Error**
- Check the API error description.
- Verify the Management Address is set to reflect.qsc.com and Port is 443
- Ensure the Q-SYS Reflect account has the necessary permissions to access the organization's device data

**Link Error / Ping Timeout**
- Make sure your Cloud Connector can reach reflect.qsc.com on port 443
- Check the Ping Protocol setting in the Symphony Q-SYS Reflect Aggregator Device configuration
- Try switching between ICMP/TCP modes, as certain protocols may be unavailable or blocked by proxy settings

If none of the recommended steps help, please enter an SOS ticket at {https://avi-spl.atlassian.net/servicedesk/customer/portals}

## What AI Assistant can do with the Q-SYS Reflect Enterprise Manager Integration:
- Find Q-SYS Reflect Aggregated Devices (Q-SYS Reflect Aggregator as Monitoring Proxy)
- Verify Q-SYS Reflect Aggregator configuration

## What AI Assistant cannot do with the Q-SYS Reflect Enterprise Manager Integration:
- Provision the devices
