# Q-SYS Reflect Enterprise Manager Integration - Capabilities & Configuration

This document covers Q-SYS Reflect Enterprise Manager Aggregator Capabilities and Configuration. 

Note: Not to be confused with Q-SYS Core Aggregator.

Symphony integrates with Q-SYS Reflect Enterprise Manager to provide comprehensive monitoring of the Q-SYS audio, video, and control (AV&C) ecosystem.
Main features are: real-time Q-SYS system health monitoring, Core and peripheral device tracking, alert and status management, and remote management capabilities across the entire Q-SYS Reflect organization.

## Main use cases
- **Monitor** Q-SYS Core system health, device status, and alert conditions across the organization
- **Track** individual device details - firmware version, uptime, serial number, site information, and device status messages
- **Filter** monitored devices by model, type, system name, or device status message to focus on relevant assets
- **Inventory** keep Q-SYS Cores and associated AV devices (streaming I/O, cameras, conferencing endpoints, touch screens, etc.) in check

## Prerequisites and where to start
Q-SYS Reflect Aggregator communicates with the Q-SYS Reflect Enterprise Manager API on behalf of an authenticated account using an API Token.

To get started:
- Log in to Q-SYS Reflect Enterprise Manager at https://reflect.qsc.com
- Generate a Q-SYS Reflect API Token from your account settings
- Use the API Token as the Password field during Symphony device configuration (Username is not required)

For detailed information on obtaining the API token, refer to the Q-SYS Reflect documentation -> https://reflect.qsc.com/help/Content/QREM_API_Example.htm

## Q-SYS Reflect Device Configuration and Provisioning
Once the API Token is obtained, use it as the Password for the Symphony device configuration.

Create the Reflect device with Monitoring Service -> Advanced Monitoring, the HTTP management protocol must be selected:
- Management Address: reflect.qsc.com
- Protocol: HTTP
- Username: (not required - leave blank)
- Password: Q-SYS Reflect API Token
- Port: 443 by default

When the device is configured, saved, and set active, the Q-SYS Reflect Aggregator will start communicating with the Reflect API to retrieve data about registered Q-SYS systems and devices based on the provided configuration.

By default, the unprovisioned devices will appear on Aggregated Devices -> Unprovisioned Devices tab.

To import a Q-SYS device for monitoring by the Q-SYS Core Aggregator:
1. Open Aggregated Devices
2. Select unprovisioned devices
3. Fill required provisioning fields
4. Import devices into Symphony

Devices and available device data can be tuned by adapter configuration properties:

| Property | Description |
|---|---|---|
| filterModel | List of models to filter monitored devices |
| filterDeviceStatusMessage | List of device status messages to filter monitored devices. Available statuses: Running, Idle: no System installed, Starting, Stopping, Offline, Unknown, OK, Initializing, Compromised, Missing, Fault, Not Present |
| filterSystemName | List of Q-SYS system names to filter monitored devices | 
| filterType | List of device types to filter monitored devices | 

For detailed information on the aggregator and its configuration, please refer to our knowledgebase -> https://symphony.knowledgeowl.com/help/q-sys-reflect-enterprise-manager-aggregator-technical-breakdown

## Available Monitored Data

Q-SYS Reflect Aggregator monitored data consists of 2 parts: Aggregator extended properties and Device extended properties.

Aggregator properties include service information - Adapter Metadata (AdapterBuildDate, AdapterUptime, AdapterVersion, LastMonitoringCycleDuration, MonitoredDevicesTotal, MonitoringCycleInterval).

Aggregated Devices (Q-SYS Cores and peripheral devices) properties include the following monitorable information:

| Property Type | Description | Examples |
| --- | --- | --- |
| Device metadata (Q-SYS Core) | Contains identification and operational details for Q-SYS Core devices. | Firmware Version, Device Id, Device Model, Device Name, Device Status Message, Device Uptime, Serial Number |
| Device metadata (non-Core devices) | Contains identity and status information for non-Core devices. | Device Id, Device Model, Manufacturer, Location, Started At |
| System-level properties | Contains system-wide platform and runtime information. | Core Name, Design Name, SystemStatus, Uptime |
| Alerts (Q-SYS Core only) | Contains active alert and health status categories for the Q-SYS Core. | Alerts Fault, Alerts Warning, Alerts Normal, Alerts Unknown |

Note: Monitoring and Control Capabilities may depend on the device model. 

### Device Status Messages

| Device Type | Description | Common Statuses |
| --- | --- | --- |
| Q-SYS Core | Primarily reflects system lifecycle and availability states such as startup, shutdown, normal operation, or loss of connectivity. | Running, Starting, Stopping, Offline, Idle: no System installed |
| Non-Q-SYS Core Devices | Focuses on operational health, discovery state, configuration issues, and device availability within the Q-SYS environment. | OK, Initializing, Compromised, Missing, Fault, Not Present, Unknown |

## Troubleshooting

** Login Error **
- Check that the Q-SYS Reflect API Token is valid, active, and has not expired
- Ensure the Symphony device Password field contains the correct API Token value; Username should be left blank
- Verify the Management Address is set to reflect.qsc.com and Port is 443

** API Error **
- Check the API error description in the device status section
- If it references configuration mismatches, verify the Management Address, Protocol, and API Token values are correct
- Ensure the Q-SYS Reflect account has the necessary permissions to access the organization's device data

** Link Error / Ping Timeout **
- Make sure your Cloud Connector can reach reflect.qsc.com on port 443
- Check the Ping Protocol setting in the Symphony Q-SYS Reflect Aggregator Device configuration
- Try switching between ICMP/TCP modes, as certain protocols may be unavailable or blocked by proxy settings

If none of the recommended steps help, please enter an SOS ticket at {https://avi-spl.atlassian.net/servicedesk/customer/portals}

## What AI Assistant can do with it:
- Find Q-SYS Reflect Aggregated Devices (Q-SYS Reflect Aggregator as Monitoring Proxy)
- Verify Q-SYS Reflect Aggregator configuration

## What AI Assistant cannot do with it:
- Provision the devices
