package io.kaminski.reverseosmosis
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*

// --- NORDIC UART SERVICE (NUS) DEFINITIONS ---
val NUS_SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
// RX Characteristic (Phone -> Dispenser) - This is where we WRITE commands
val NUS_RX_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")

@SuppressLint("MissingPermission") // Checked by MainActivity
class WaterDispenserBleManager(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null

    // State for UI
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    // Safety & Queueing
    private val writeMutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatJob: Job? = null

    // Tracks if a button is currently held down
    private var isUserInteracting = false

    enum class ConnectionState { Disconnected, Connecting, Connected, Error }

    fun connect(address: String) {
        // Validate adapter exists
        if (bluetoothAdapter == null) {
            _connectionState.value = ConnectionState.Error
            return
        }

        try {
            _connectionState.value = ConnectionState.Connecting
            val device = bluetoothAdapter.getRemoteDevice(address)
            // Auto-connect false for faster initial connection
            device.connectGatt(context, false, gattCallback)
        } catch (e: IllegalArgumentException) {
            _connectionState.value = ConnectionState.Error
        }
    }

    fun disconnect() {
        stopHeartbeat()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        _connectionState.value = ConnectionState.Disconnected
    }

    // --- Command Interface ---

    fun pressHot() = sendCommand("\$H", interacting = true)
    fun pressCold() = sendCommand("\$C", interacting = true)
    fun pressAmbient() = sendCommand("\$A", interacting = true)

    fun releaseButton() {
        isUserInteracting = false
        // Immediate safety cutoff
        sendCommand("\$R", interacting = false)
    }

    // --- Internal Logic ---

    private fun sendCommand(command: String, interacting: Boolean) {
        if (interacting) isUserInteracting = true

        scope.launch {
            writeToCharacteristic(command)
        }
    }

    private suspend fun writeToCharacteristic(value: String) {
        val gatt = bluetoothGatt ?: return
        val char = writeCharacteristic ?: return

        writeMutex.withLock {
            try {
                val writeType = if ((char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) > 0) {
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                } else {
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                }

                char.value = value.toByteArray(Charsets.UTF_8)
                char.writeType = writeType

                @Suppress("DEPRECATION")
                val success = gatt.writeCharacteristic(char)

                if (!success) {
                    _connectionState.value = ConnectionState.Error
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _connectionState.value = ConnectionState.Error
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(500L)
                // Only send safety reset if user is NOT pressing a button
                if (!isUserInteracting && _connectionState.value == ConnectionState.Connected) {
                    writeToCharacteristic("\$R")
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                bluetoothGatt = gatt
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = ConnectionState.Disconnected
                stopHeartbeat()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(NUS_SERVICE_UUID)
                if (service != null) {
                    val rxChar = service.getCharacteristic(NUS_RX_UUID)
                    if (rxChar != null) {
                        writeCharacteristic = rxChar
                        _connectionState.value = ConnectionState.Connected
                        startHeartbeat()
                    } else {
                        _connectionState.value = ConnectionState.Error
                    }
                } else {
                    _connectionState.value = ConnectionState.Error
                }
            }
        }
    }
}