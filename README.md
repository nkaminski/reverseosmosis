# ReverseOsmosis 🚰

**ReverseOsmosis** is a reverse-engineered, alternative Android application designed to control Bluetooth-enabled water dispensers (specifically those from [Natural Choice Water](https://naturalchoicewater.com/)). 

Designed for those who prefer a minimal, terminal-inspired interface over the now unavailable proprietary app, this project provides direct control over hot, cold, and ambient temperature water dispensing via Bluetooth Low Energy (BLE).

![Terminal UI Aesthetic](https://img.shields.io/badge/UI-Terminal_Green-00FF41?style=flat-square)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF?style=flat-square&logo=kotlin)
![Compose](https://img.shields.io/badge/Jetpack-Compose-4285F4?style=flat-square&logo=android)

---

### ⚠️ THE "DON'T FLOOD YOUR CAFETERIA" DISCLAIMER

This is a reverse-engineered side project, not a polished corporate product.

- **The Unscheduled Water Feature:** 
  This app lets you control a practically unlimited water supply wirelessly and without any authentication. While the manufacturer's insecure design isn't my problem, please use this responsibly.
- **Hot Water:** It controls the hot button - enough said.
- **No Guarantees:** This is provided "as-is". If it works, neat! If it crashes and turns your cafeteria into a swamp, grab a towel.

---

## 🛠 Features

- **Low-Level BLE Control:** Communicates directly with the dispenser using the Nordic UART Service (NUS).
- **Safety Heartbeat:** Implements a 500ms safety stop ($R) protocol to ensure the connection remains alive and the dispenser still stops if a packet is dropped.
- **Direct MAC Entry:** Quickly link to a specific device without a complex discovery UI.

## 🔍 How It Works

The application targets the **Nordic UART Service (NUS)** common in many BLE-enabled IoT devices. Through reverse engineering the original manufacturer's protocol, the following command set was identified:

| Command | Action            |
| :------ | :---------------- |
| `$H`    | **Hot Water**     |
| `$C`    | **Cold Water**    |
| `$A`    | **Ambient Water** |
| `$R`    | **Stop/Release**  |

The app maintains a "heartbeat" while connected, periodically sending `$R` to ensure the solenoid valves do not stay open in the event of a dropped command packet.

## 📱 Installation & Requirements
* **Android 12+ (API 31+):** Required for modern Bluetooth permission handling.
* **Permissions:** The app requires `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT`.
* **MAC Address:** You will need the Bluetooth MAC address of your dispenser (usually found via a BLE scanner app or the original manufacturer's app).


```kotlin
// NUS Service Definitions
val NUS_SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
val NUS_RX_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
```

---
*Developed by Nash Kaminski.