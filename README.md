# ReverseOsmosis 🚰

**ReverseOsmosis** is a reverse-engineered, alternative Android application designed to control Bluetooth-enabled water dispensers (specifically those from [Natural Choice Water](https://naturalchoicewater.com/)). 

Designed for those who prefer a minimal, terminal-inspired interface over the now unavailable proprietary app, this project provides direct control of hot, cold, and ambient temperature water dispensing via Bluetooth Low Energy (BLE).

![Terminal UI Aesthetic](https://img.shields.io/badge/UI-Terminal_Green-00FF41?style=flat-square)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF?style=flat-square&logo=kotlin)
![Compose](https://img.shields.io/badge/Jetpack-Compose-4285F4?style=flat-square&logo=android)

---

### ⚠️ THE "DON'T FLOOD YOUR CAFETERIA" DISCLAIMER

This is a reverse-engineered side project, not a polished corporate product.

- **The Unscheduled Water Feature:** 
  This app lets you control a practically unlimited water supply wirelessly and without any authentication. While the manufacturer's insecure design isn't my problem, please use this responsibly.
- **Hot Water:** It controls the hot button - enough said.
- **Emergency Stop:** In the rare event of a malfunction where water will not stop pouring, the fastest way to trigger the dispenser's failsafe is to **turn off your Bluetooth**.
- **No Guarantees:** This is provided "as-is". If it works, neat! If it crashes and turns your cafeteria into a swamp, grab a towel.

---

## 🛠 Features

- **Simple UI:** A distraction-free, high-contrast interface designed for efficiency.
- **Low-Level BLE Control:** Communicates directly with the dispenser using the Nordic UART Service (NUS). No IP connectivity needed.
- **Direct MAC Entry:** Quickly link to a specific device without a complex or slow discovery UI.

## 🔍 How It Works

Through reverse engineering the original manufacturer's protocol, the following command set was identified
with commands sent to the device using the Nordic UART Service (NUS):

| Command | Action            |
| :------ | :---------------- |
| `$H`    | **Hot Water**     |
| `$C`    | **Cold Water**    |
| `$A`    | **Ambient Water** |
| `$R`    | **Stop/Release**  |

## 📱 Installation & Requirements
* **Android 12+ (API 31+):**
* **Permissions:** The app requires `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT`.
* **MAC Address:** You will need the Bluetooth MAC address of your dispenser (this is encoded in the QR code shown on the screen).
