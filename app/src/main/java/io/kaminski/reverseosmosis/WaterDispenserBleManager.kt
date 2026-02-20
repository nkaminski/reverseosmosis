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

/**
 * Manages Bluetooth Low Energy communication with the water dispenser.
 * Implements a periodic heartbeat to ensure the dispenser state remains synchronized.
 */
@SuppressLint("MissingPermission")
class WaterDispenserBleManager(
    private val context: Context,
    private val dataStoreManager: DataStoreManager
) {

    companion object {
        private const val TAG = "WaterDispenserBleManager"
        private val NUS_SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        private val NUS_RX_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        private const val HEARTBEAT_INTERVAL_MS = 400L
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null

    // State for UI
    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    // Safety & Concurrency
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val writeMutex = Mutex()
    private var heartbeatJob: Job? = null

    // SharedFlow to signal BLE write completions, driving the heartbeat loop.
    private val writeFinishedEvent = MutableSharedFlow<Int>(
        extraBufferCapacity = 1,
    )

    enum class DispenserCommand(val value: String) {
        HOT("\$H"),
        COLD("\$C"),
        AMBIENT("\$A"),
        RELEASE("\$R")
    }

    @Volatile
    private var currentCommand = DispenserCommand.RELEASE

    enum class ConnectionState { Disconnected, Connecting, Connected, Error }

    fun isDispenserSafe(): Boolean = currentCommand == DispenserCommand.RELEASE

    fun connect(address: String) {
        val adapter = bluetoothAdapter ?: return triggerError("Bluetooth adapter is null")

        try {
            _connectionState.value = ConnectionState.Connecting
            val device = adapter.getRemoteDevice(address)
            Log.i(TAG, "Connecting to $address...")
            bluetoothGatt = device.connectGatt(context,
                                            false,
                                            gattCallback,
                                            BluetoothDevice.TRANSPORT_LE)
        } catch (e: IllegalArgumentException) {
            triggerError("Invalid Bluetooth address: $address", e)
        }
    }

    fun disconnect() {
        Log.d(TAG, "User initiated disconnect")
        stopHeartbeat()
        bluetoothGatt?.disconnect()
    }

    fun pressButton(cmd: DispenserCommand) {
        currentCommand = cmd
    }

    fun releaseButton() {
        currentCommand = DispenserCommand.RELEASE
    }

    private suspend fun writeToCharacteristic(value: String) {
        val gatt = bluetoothGatt ?: return
        val char = writeCharacteristic ?: return

        writeMutex.withLock {
            try {
                Log.d(TAG, "Writing to char handle (${char.instanceId}): $value")

                @Suppress("DEPRECATION")
                char.value = value.toByteArray(Charsets.US_ASCII)
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                
                @Suppress("DEPRECATION")
                val success = gatt.writeCharacteristic(char)

                if (!success){
                    Log.e(TAG, "GATT write failed to initiate for $value")
                }
            } catch (e: Exception) {
                triggerError("Exception during write", e)
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            // Initial write to sync state upon connection
            writeToCharacteristic(currentCommand.value)

            // Heartbeat loop: wait for last write to complete, wait interval, then repeat.
            // This ensures we always have a command in flight or recently confirmed.
            // All writes are driven by this loop to avoid GATT congestion / dispenser hangs.
            writeFinishedEvent.collect { status ->
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    delay(HEARTBEAT_INTERVAL_MS)
                    if (isActive) writeToCharacteristic(currentCommand.value)
                } else {
                    triggerError("Write failed with status: $status")
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun triggerError(message: String, exception: Throwable? = null) {
        Log.e(TAG, message, exception)
        stopHeartbeat()
        bluetoothGatt?.disconnect()
        _connectionState.value = ConnectionState.Error
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> gatt.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (_connectionState.value != ConnectionState.Error) {
                        _connectionState.value = ConnectionState.Disconnected
                    }
                    stopHeartbeat()
                    gatt.close()
                    bluetoothGatt = null
                    writeCharacteristic = null
                    Log.i(TAG, "Disconnected from device")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(NUS_SERVICE_UUID)
                val rxChar = service?.getCharacteristic(NUS_RX_UUID)
                if (rxChar != null) {
                    writeCharacteristic = rxChar
                    _connectionState.value = ConnectionState.Connected
                    startHeartbeat()
                    scope.launch { dataStoreManager.saveMacAddress(gatt.device.address) }
                } else {
                    triggerError("NUS RX characteristic not found")
                }
            } else {
                triggerError("GATT service discovery failed with status $status")
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, status: Int) {
            writeFinishedEvent.tryEmit(status)
        }
    }
}
