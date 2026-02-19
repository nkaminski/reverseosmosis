package io.kaminski.reverseosmosis
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*

// --- NORDIC UART SERVICE (NUS) DEFINITIONS ---
val NUS_SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
// RX Characteristic (Phone -> Dispenser) - This is where we WRITE commands
val NUS_RX_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")

@SuppressLint("MissingPermission") // Checked by MainActivity
class WaterDispenserBleManager(private val context: Context) {

    private val bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothDevice: BluetoothDevice? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null

    // State for UI
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    // Safety & Queueing
    private val writeMutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatJob: Job? = null

    // Persistent storage of the MAC address
    private val dataStoreManager = DataStoreManager(context)

    // Event used to trigger the next write after completion
    private val writeFinishedEvent = MutableSharedFlow<Int>(extraBufferCapacity = 1)

    enum class DispenserCommand(val value: String) {
        HOT("\$H"),
        COLD("\$C"),
        AMBIENT("\$A"),
        RELEASE("\$R")
    }
    
    private var currentDispenserCommand: DispenserCommand = DispenserCommand.RELEASE

    enum class ConnectionState { Disconnected, Connecting, Connected, Error }

    fun isDispenserSafe(): Boolean {
        return currentDispenserCommand == DispenserCommand.RELEASE
    }
    fun connect(address: String) {
        // Validate adapter exists
        if (bluetoothAdapter == null) {
            triggerError("Bluetooth adapter is null")
            return
        }

        try {
            _connectionState.value = ConnectionState.Connecting
            bluetoothDevice = bluetoothAdapter.getRemoteDevice(address)
            // Auto-connect false for faster initial connection
            bluetoothDevice?.connectGatt(context, false, gattCallback)
        } catch (e: IllegalArgumentException) {
            triggerError("Invalid Bluetooth address", e)
        }
    }

    fun disconnect() {
        stopHeartbeat()
        bluetoothGatt?.disconnect()
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun triggerError(message: String, exception: Throwable? = null) {
        if (exception != null) {
            Log.e("WaterDispenserBleManager", message, exception)
        } else {
            Log.e("WaterDispenserBleManager", message)
        }

        stopHeartbeat()
        bluetoothGatt?.disconnect()
        _connectionState.value = ConnectionState.Error
    }

    // --- Command Interface ---
    fun pressButton(cmd: DispenserCommand) {
        currentDispenserCommand = cmd
    }
    fun releaseButton() {
        currentDispenserCommand = DispenserCommand.RELEASE
    }

    // --- Internal Logic --
    private suspend fun writeToCharacteristic(value: String) {
        val gatt = bluetoothGatt ?: return
        val char = writeCharacteristic ?: return

        writeMutex.withLock {
            try {
                @Suppress("DEPRECATION")
                char.value = value.toByteArray(Charsets.US_ASCII)
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

                @Suppress("DEPRECATION")
                val success = gatt.writeCharacteristic(char)

                Log.i("WaterDispenserBleManager", "Characteristic write triggered: val: $value, success: $success")
                if (!success) {
                    triggerError("Failed to initiate characteristic write")
                }
            } catch (e: Exception) {
                triggerError("Exception during write", e)
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            // Initial write upon connection
            writeToCharacteristic(currentDispenserCommand.value)

            // Listen for completion events to schedule the next write
            writeFinishedEvent.collect { status ->
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    delay(500L) // Wait 500ms after completion to issue another write
                    if (isActive) {
                        writeToCharacteristic(currentDispenserCommand.value)
                    }
                } else {
                    triggerError("Characteristic write failed with status $status")
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
    }

    internal fun Int.toConnectionStateString() = when (this) {
        BluetoothProfile.STATE_CONNECTED -> "Connected"
        BluetoothProfile.STATE_CONNECTING -> "Connecting"
        BluetoothProfile.STATE_DISCONNECTED -> "Disconnected"
        BluetoothProfile.STATE_DISCONNECTING -> "Disconnecting"
        else -> "N/A"
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d("WaterDispenserBleManager", "onConnectionStateChange: status=${status.toConnectionStateString()}, newState=${newState.toConnectionStateString()}")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                bluetoothGatt = gatt
                gatt.discoverServices()
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (_connectionState.value != ConnectionState.Error) {
                    _connectionState.value = ConnectionState.Disconnected
                }
                stopHeartbeat()
                gatt.close()
                bluetoothGatt = null
                writeCharacteristic = null
                Log.d("WaterDispenserBleManager", "Disconnected from device")
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
                        // Save successfully connected MAC address
                        scope.launch {
                            dataStoreManager.saveMacAddress(gatt.device.address)
                        }

                    } else {
                        triggerError("NUS RX characteristic not found")
                    }
                } else {
                    triggerError("NUS service not found")
                }
            } else {
                triggerError("GATT service discovery failed with status $status")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            Log.d("WaterDispenserBleManager", "onCharacteristicWrite: status=$status, handle=${characteristic.instanceId}")
            // Signal that write has completed
            writeFinishedEvent.tryEmit(status)
        }
    }
}
