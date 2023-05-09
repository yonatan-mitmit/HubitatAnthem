metadata {
  definition (
    name: "Anthem MRX x40 Receiver Driver", 
    namespace: "mitmit", 
    author: "Yonatan Striem Amit",
    description : "Multi Zone Driver for Anthem MRX x40 receivers") {

    capability "Actuator"
    capability "Initialize"
    capability "Switch"
    capability "Refresh"

    // Add any additional capabilities or custom commands specific to your receiver
  }

  preferences {
    // Add user-input preferences for the driver, such as IP address, port, authentication, etc.
    input name: "ip", type: "text", title: "IP Address", description: "The IP address of the receiver", required: true, displayDuringSetup: true, defaultValue: "192.168.1.72"
    input name: "port", type: "number", title: "Port", description: "The port of the receiver", required: true, defaultValue: "14999", displayDuringSetup: true, range: "1..65535"
    input name: "enabledReceiverZones", type: "enum", title: "Enabled Receiver Zones", description: "The zones that are enabled on the receiver", required: true, multiple: true, options: [[1:"Main"], [2:"Zone 2"]], displayDuringSetup: true, defaultValue: [1]
    input name: "logLevel", type: "enum", title: "Log Level", description: "The level of logging to use", required: true, defaultValue: "DEBUG", options: ["DEBUG", "INFO", "WARN", "ERROR"], displayDuringSetup: false

    attribute "Model", "string"
    attribute "Channels", "integer"
    attribute "Network Status", "string"

  }
}
import groovy.transform.Field

@Field static private int connectDelay = 1
@Field static private int reconnectDelay = 5
@Field static private int numberZones = 2
@Field static private String childZoneDriver = "Anthem MRX x40 Receiver Zone Driver"

@Field static private Map zoneNames = [
  1 : "Main",
  2 : "Zone 2"
]

Map anComm(Closure send, Closure parse, String description = "") {
  [ "send" : send, "parse" : parse, "description" : description ]
}

@Field Map handlers = [
  "IDM" : anComm({ sendMsg("IDM?")}, {msg -> handleIDM(msg)}, "Query the model number of the receiver"),
  "ICN" : anComm({ sendMsg("ICN?")}, {msg -> handleICN(msg)}, "Query number of input channels")
]

def installed() {
  logInfo "Installed with settings: ${settings}"
  initialize()
  updateChildren()
}

def updated() {
  logInfo "Updated with settings: ${settings} v1.0"
  telnetClose()
  sendEvent(name: "Network Status", value: "Disconnected")
  unschedule()
  updateChildren()
  //runIn(5, initialize)

  initialize()
}

def initialize() {
  // Initialize the driver and set up any necessary connections or configurations
  telnetClose()
  // The connect method must be run in a delay, otherwise the disconnect handler will get into a loop. We must give it a chance to run first.
  runIn(connectDelay, connect)
}

def connect() {
  String _ip = settings?.ip
  int _port = settings?.port
  try {
    telnetConnect([termChars:[(int)';']],_ip, _port, null, null)
    sendEvent(name: "Network Status", value: "Connected")

    onConnectInitialize()
    //runIn(2, connect)
  } catch (Exception e) {
    logWarning("Error connecting to receiver: ${e.getMessage()}")
    sendEvent(name: "Network Status", value: e.getMessage)
  }
}

def onConnectInitialize() {
    refresh()
    getChildDevices()?.each { child ->
      child.initialize()
    }
}

def on() {
  // Turn on the receiver
}

def off() {
  // Turn off the receiver
}

def setLevel(level) {
  // Set the volume level of the receiver
}

def refresh() {
  // Refresh the state of the receiver
  handlers["IDM"].send()
  handlers["ICN"].send()
}


def parse(String description) {
  // Parse incoming events to generate events for the device
  logDebug("Parsing receiver command: ${description}")

  // XXX: Is this still needed as we set ';' as the term character?
  def tokens = description.split(';')

  tokens.each { token ->
    if (token.size() >= 2) {
      def firstTwo = token.take(2)
      if (firstTwo.matches(/Z\d/)) {
        handleChildResponse(firstTwo[1], token[2..-1])
        return
      }
    }

    if (token.size() >= 3) {
      def firstThree = token.take(3)
      def command = handlers[firstThree]
      if (command) {
        logDebug("Calling command for ${firstThree} (${command.description})")
        command.parse(token[3..-1])
        return
      }
    }
  }
}

def telnetStatus(String status) {
  logInfo("Telnet status: ${status}.")

  sendEvent(name: "Network Status", value: "Disconnected")

  if(status.contains("receive error: Stream is closed")){
        logDebug("Reconnecting in: ${reconnectDelay}.")
        unschedule()
        runIn(reconnectDelay, connect)
    }
}

def handleIDM(String description) {
  // Handle the IDM command
  logDebug("Handling IDM command: ${description}")
  //sendEvent(name: "Model", value: description)
  updateDataValue("Model", description)
}

def handleICN(String msg) {
  // Handle the IDM command
  logDebug("Handling ICN command ${msg}")
  //sendEvent(name: "Channels", value: msg.toInteger())
  updateDataValue("Channels", msg)
}

def handleChildResponse(String zone, String s) {
    // Your logic for handling child response
    child = childDevice(getChildName(zone))
    if (!child) {
      log.debug("Received command for disabled zone ${zone} : ${s}")
    }
    child.parse(s)
}

def getChildName(child) {
  "${device.deviceNetworkId}-zone${zone}"
}

def updateChildren() {
  zoneEnabledMap = [:]
  (0..numberZones).each { zoneEnabledMap[it] = false }
  enabledReceiverZones.each { zoneEnabledMap[it] = true }
  
  logDebug("Zone enabled map: ${zoneEnabledMap}")

  zoneEnabledMap.each { zone, enabled ->
    def childDevice = getChildDevice(getChildName(zone))
    if (enabled) {
      if (!childDevice) {
        logDebug("Creating child device for zone ${zone}")
        childDevice = addChildDevice(childZoneDriver, getChildName(zone), [label: "Anthem ${device.getName()} - Zone ${zone}", isComponent: true, name : "${device.getName()} - Zone ${zone}" ])
      }
        childDevice.setZone(zone)
        childDevice.setLogLevel(settings.logLevel)
    } else {
      if (childDevice) {
        logDebug("Removing child device for zone ${zone}")
        deleteChildDevice(childDevice.deviceNetworkId)
      }
    }
  }
}

def sendMsg(message) {
  // Send a message to the receiver
  logDebug("Sending message to receiver: ${message}")
  sendHubCommand(new hubitat.device.HubAction(message, hubitat.device.Protocol.TELNET))
}

// Logger
def logDebug(message) {
  if (settings.logLevel == "DEBUG") {
    log.debug message
  }
}

def logInfo(message) {
  if (settings.logLevel == "DEBUG" || settings.logLevel == "INFO") {
    log.info message
  }
}

def logWarning(message) {
  if (settings.logLevel == "DEBUG" || settings.logLevel == "INFO" || settings.logLevel == "WARN") {
    log.warn message
  }
}

def logError(message) {
  if (settings.logLevel == "DEBUG" || settings.logLevel == "INFO" || settings.logLevel == "WARN" || settings.logLevel == "ERROR") {
    log.error message
  }
}


/*
start: Connected:

init 
-- disconnect
-- schedule (connect)
eventDisconnect
-- unschedule (cancel connect)
-- schedule (connect)

connect


*/