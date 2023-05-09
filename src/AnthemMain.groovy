metadata {
  definition (name: "Anthem MRX Receiver Driver", namespace: "mitmit", author: "Yonatan Striem Amit") {
    capability "Actuator"
    capability "Switch"
    capability "Audio Volume"
    capability "Refresh"

    // Add any additional capabilities or custom commands specific to your receiver
  }

  preferences {
    // Add user-input preferences for the driver, such as IP address, port, authentication, etc.
  }
}

def installed() {
  log.debug "Installed with settings: ${settings}"
  initialize()
}

def updated() {
  log.debug "Updated with settings: ${settings}"
  initialize()
}

def initialize() {
  // Initialize the driver and set up any necessary connections or configurations
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
}