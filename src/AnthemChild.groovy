definition (
  name: "Anthem MRX x40 Receiver Zone Driver", 
  namespace: "mitmit", 
  author: "Yonatan Striem Amit",
  description : "Child Zone Driver for Anthem MRX x40 receivers") {

  capability "Actuator"
  capability "Switch"
  capability "AudioVolume"
  capability "MediaInputSource"

    // Add any additional capabilities or custom commands specific to your receiver
}

preferences {
  // Add user-input preferences for the driver, such as IP address, port, authentication, etc.
  input name: "logLevel", type: "enum", title: "Log Level", description: "The level of logging to use", required: true, defaultValue: "DEBUG", options: ["DEBUG", "INFO", "WARN", "ERROR"], displayDuringSetup: false
  attribute "VolumeDB", "number"
}

import groovy.transform.Field
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

Map anComm(Closure query, Closure set, Closure parse, String description = "") {
  [ "query" : query, "set" : set, "parse" : parse, "description" : description ]
}
 
@Field Map handlers = [
  "POW" : anComm({ sendMsg("POW?")}, {msg -> sendMsg("POW${msg}")}, {msg -> handlePOW(msg)}, "Power for Zone"),
  "INP" : anComm({ sendMsg("INP?")}, {msg -> sendMsg("INP${msg}")}, {msg -> handleINP(msg)}, "Input for Zone"),
  "PVOL" : anComm({ sendMsg("PVOL?")}, {msg -> sendMsg("PVOL${msg}")}, {msg -> handlePVOL(msg)}, "Volume for Zone"),
  "VOL" : anComm({ sendMsg("VOL?")}, {msg -> sendMsg("VOL${msg}")}, {msg -> handleVOL(msg)}, "Volume for Zone"),
  "MUT" : anComm({ sendMsg("MUT?")}, {msg -> sendMsg("MUT${msg}")}, {msg -> handleMUT(msg)}, "Mute for Zone"),
  "VDN" : anComm( null, { sendMsg("VDN")}, null, "Volume Down for Zone"),
  "VUP" : anComm( null, { sendMsg("VUP")}, null, "Volume Up for Zone"),
]

def parse(String msg) {
    handlers.each { key, value ->
        if (msg.startsWith(key)) {
            if (value.parse) {
                value.parse(msg.substring(key.length()))
            }
            else {
                logWarning "No parser for ${key}"
            }
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

    channelNames = new JsonSlurper().parseText(getDataValue("inputNames") ?: "{}") as Map

    name = channelNames[inp] ?: "Input ${inp}"
    
    sendEvent(name : "mediaInputSource", value : name)
}

def handlePVOL(String pvol) {
    logDebug "handlePVOL: ${pvol}"
    sendEvent(name : "volume", value : pvol.toInteger())
}

def handleMUT(String mut) {
    logDebug "handleMUT: ${mut}"
    if (mut == "0") {
        sendEvent(name: "mute", value: "unmuted")
    } else if (mut == "1") {
        sendEvent(name: "mute", value: "muted")
    }
    else {
        logWarning "handleMUT: Unknown value: ${mut} in Zone ${state.zone}"
    }
}

def handleVOL(String vol) {
    logDebug "handleVOL: ${vol}"
    sendEvent(name : "VolumeDB", value : vol.toDouble())
}


def on() {
  logDebug "on() on zone ${state.zone}"
  handlers["POW"].set("1")
}

def off() {
  logDebug "off() on zone ${state.zone}"
  handlers["POW"].set("0")
}

def setInputSource(name) {
  logDebug "setInputSource(${name}) on zone ${state.zone}"
  reverseNameMap = new JsonSlurper().parseText(getDataValue("reverseInputNames") ?: "{}") as Map
  handlers["INP"].set(reverseNameMap[name])
}

def mute() {
    logDebug "mute() on zone ${state.zone}"
    handlers["MUT"].set("1")    
}

def unmute() {
    logDebug "unmute() on zone ${state.zone}"
    handlers["MUT"].set("0")    
}

def setVolume(volume) {
    logDebug "setVolume(${volume}) on zone ${state.zone}"
    handlers["PVOL"].set(volume)
}

def volumeUp() {
    logDebug "volumeUp() on zone ${state.zone}"
    handlers["VUP"].set()
    handlers["PVOL"].query()
    handlers["VOL"].query()
}

def volumeDown() {
    logDebug "volumeDown() on zone ${state.zone}"
    handlers["VDN"].set()
    handlers["PVOL"].query()
    handlers["VOL"].query()
}

def installed() {
  logInfo "Installed with settings: ${settings}"
}

def updated() {
  logInfo "Updated with settings: ${settings} v1.0"
}

def initialize() {
    logInfo "Initializing Zone ${state.zone}"
    handlers["POW"].query()
    handlers["INP"].query()
    handlers["PVOL"].query()
    handlers["MUT"].query()
    handlers["VOL"].query()

}

def inputNamesUpdated(Map inputNames) {
    logDebug "inputNamesUpdated: ${inputNames}"
    reverseNameMap = [:]

    inputNames.each { key, value ->
        reverseNameMap[value] = key
    }
    updateDataValue("inputNames", JsonOutput.toJson(inputNames))
    updateDataValue("reverseInputNames", JsonOutput.toJson(reverseNameMap))
    sendEvent(name : "supportedInputs", value : JsonOutput.toJson(reverseNameMap.keySet()))
    handlers["INP"].query()

}

def setZone(zone) {
  state.zone = zone
}
def setLogLevel(logLevel) {
  device.updateSetting("logLevel", logLevel)
}

def sendMsg(String msg){
    parent.sendMsg("Z${state.zone}${msg}")
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