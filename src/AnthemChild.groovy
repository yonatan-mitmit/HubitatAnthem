definition (
  name: "Anthem MRX x40 Receiver Zone Driver", 
  namespace: "mitmit", 
  author: "Yonatan Striem Amit",
  description : "Child Zone Driver for Anthem MRX x40 receivers") {

  capability "Actuator"
  capability "Initialize"
  capability "Switch"
  capability "AudioVolume"
  capability "MediaInputSource"

    // Add any additional capabilities or custom commands specific to your receiver
}

preferences {
  // Add user-input preferences for the driver, such as IP address, port, authentication, etc.
  input name: "logLevel", type: "enum", title: "Log Level", description: "The level of logging to use", required: true, defaultValue: "DEBUG", options: ["DEBUG", "INFO", "WARN", "ERROR"], displayDuringSetup: false
}

import groovy.transform.Field

Map anComm(Closure query, Closure set, Closure parse, String description = "") {
  [ "query" : query, "set" : set, "parse" : parse, "description" : description ]
}
 
@Field Map handlers = [
  "POW" : anComm({ sendMsg("POW?")}, {msg -> sendMsg("POW${msg}")}, {msg -> handlePOW(msg)}, "Power for Zone"),
  "INP" : anComm({ sendMsg("INP?")}, {msg -> sendMsg("INP${msg}")}, {msg -> handleINP(msg)}, "Input for Zone")
]

def parse(String msg) {
    handlers.each { key, value ->
        if (msg.startsWith(key)) {
            value.parse(msg.substring(key.length()))
        }
    }
}

def handlePOW(String pow) {
    logDebug "handlePOW: ${pow}"
    if (pow == "0") {
        sendEvent(name: "switch", value: "off")
    } else if (pow == "1") {
        sendEvent(name: "switch", value: "on")
    }
    else {
        logWarning "handlePOW: Unknown value: ${pow} in Zone ${state.zone}"
    }
}

def handleINP(String inp) {
    logDebug "handleINP: ${inp}"
    sendEvent(name : "inputSource", value : inp)
}

def installed() {
  logInfo "Installed with settings: ${settings}"
  initialize()
}

def updated() {
  logInfo "Updated with settings: ${settings} v1.0"
  initialize()
}

def initialize() {
    logInfo "Initializing Zone ${state.zone}"
    handlers["POW"].query()
    handlers["INP"].query()
}


def setZone(zone) {
  state.zone = zone
}
def setLogLevel(logLevel) {
  device.updateSetting("logLevel", logLevel)
}

def sendMsg(GString msg){
    parent.sendMsg("Z{state.zone}${msg}")
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