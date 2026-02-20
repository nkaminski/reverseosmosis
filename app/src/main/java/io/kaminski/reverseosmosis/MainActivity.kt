package io.kaminski.reverseosmosis

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.kaminski.reverseosmosis.ui.theme.*

class MainActivity : ComponentActivity() {

    private lateinit var bleManager: WaterDispenserBleManager
    private val dataStoreManager by lazy { DataStoreManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        bleManager = WaterDispenserBleManager(this, dataStoreManager)

        setContent {
            ReverseOsmosisApp(bleManager, dataStoreManager)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bleManager.disconnect()
    }
}

@Composable
fun ReverseOsmosisApp(bleManager: WaterDispenserBleManager, dataStoreManager: DataStoreManager) {
    val connectionState by bleManager.connectionState.collectAsState()
    val context = LocalContext.current

    // Permission Logic
    var hasPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasPermissions = perms[Manifest.permission.BLUETOOTH_SCAN] == true &&
                perms[Manifest.permission.BLUETOOTH_CONNECT] == true
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = TerminalBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Header(connectionState)

            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                when {
                    !hasPermissions -> PermissionError()
                    connectionState == WaterDispenserBleManager.ConnectionState.Connected -> ControlPanel(bleManager)
                    else -> ScanPanel(bleManager, dataStoreManager, connectionState)
                }
            }

            Footer(connectionState)
        }
    }
}

@Composable
private fun Header(connectionState: WaterDispenserBleManager.ConnectionState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.app_title_version_format, BuildConfig.VERSION_NAME),
            fontFamily = FontFamily.Monospace,
            color = RetroAquaBlue,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        if (connectionState != WaterDispenserBleManager.ConnectionState.Connected) {
            Text(
                text = stringResource(R.string.app_description),
                color = TerminalDimAqua,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun PermissionError() {
    Text(
        text = stringResource(R.string.error_permissions_denied),
        color = AlertRed,
        fontFamily = FontFamily.Monospace
    )
}

@Composable
private fun Footer(connectionState: WaterDispenserBleManager.ConnectionState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.status_format, connectionState.name.uppercase()),
            color = if (connectionState == WaterDispenserBleManager.ConnectionState.Error) AlertRed else RetroAquaBlue,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = stringResource(R.string.credits),
            color = TerminalDimAqua,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp, bottom = 8.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanPanel(
    bleManager: WaterDispenserBleManager,
    dataStoreManager: DataStoreManager,
    connectionState: WaterDispenserBleManager.ConnectionState
) {
    val lastMacAddress by dataStoreManager.lastMacAddress.collectAsState(initial = null)
    var macInput by remember { mutableStateOf("") }

    LaunchedEffect(lastMacAddress) {
        if (macInput.isEmpty() && lastMacAddress != null) {
            macInput = lastMacAddress!!
        }
    }

    val macRegex = "^([0-9A-Fa-f]{2}[:]){5}([0-9A-Fa-f]{2})$".toRegex()
    val isValid = macRegex.matches(macInput)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.enter_device_address),
            color = RetroAquaBlue,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = macInput,
            onValueChange = { if (it.length <= 17) macInput = it.uppercase() },
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(
                color = RetroAquaBlue,
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            ),
            singleLine = true,
            placeholder = {
                Text(
                    stringResource(R.string.mac_address_placeholder),
                    color = TerminalDimAqua,
                    fontFamily = FontFamily.Monospace
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RetroAquaBlue,
                unfocusedBorderColor = TerminalDimAqua,
                cursorColor = RetroAquaBlue,
                focusedTextColor = RetroAquaBlue,
                unfocusedTextColor = RetroAquaBlue,
                focusedContainerColor = TerminalBlack,
                unfocusedContainerColor = TerminalBlack
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done
            )
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { if (isValid) bleManager.connect(macInput) },
            enabled = isValid && connectionState != WaterDispenserBleManager.ConnectionState.Connecting,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isValid) TerminalDimAqua else DisabledGray,
                disabledContainerColor = DisabledGray
            ),
            shape = RectangleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .border(1.dp, if (isValid) RetroAquaBlue else DisabledGray, RectangleShape)
        ) {
            if (connectionState == WaterDispenserBleManager.ConnectionState.Connecting) {
                CircularProgressIndicator(color = RetroAquaBlue, modifier = Modifier.size(24.dp))
            } else {
                Text(
                    text = if (isValid) stringResource(R.string.connect_button) else stringResource(R.string.invalid_address_button),
                    fontFamily = FontFamily.Monospace,
                    color = if (isValid) RetroAquaBlue else Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (!isValid && macInput.isNotEmpty()) {
            Text(
                text = stringResource(R.string.mac_address_format_error),
                color = AlertRed,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun ControlPanel(bleManager: WaterDispenserBleManager) {
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        ConsoleButton(
            label = stringResource(R.string.label_hot),
            textColor = AlertRed,
            onPress = { bleManager.pressButton(WaterDispenserBleManager.DispenserCommand.HOT) },
            onRelease = { bleManager.releaseButton() }
        )

        ConsoleButton(
            label = stringResource(R.string.label_ambient),
            textColor = RetroAquaBlue,
            onPress = { bleManager.pressButton(WaterDispenserBleManager.DispenserCommand.AMBIENT) },
            onRelease = { bleManager.releaseButton() }
        )

        ConsoleButton(
            label = stringResource(R.string.label_cold),
            textColor = CoolCyan,
            onPress = { bleManager.pressButton(WaterDispenserBleManager.DispenserCommand.COLD) },
            onRelease = { bleManager.releaseButton() }
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = { bleManager.disconnect() },
            enabled = bleManager.isDispenserSafe(),
            colors = ButtonDefaults.buttonColors(containerColor = TerminalDimAqua),
            shape = RectangleShape,
            modifier = Modifier.border(1.dp, RetroAquaBlue)
        ) {
            Text(stringResource(R.string.disconnect_button), fontFamily = FontFamily.Monospace, color = RetroAquaBlue)
        }
    }
}

@Composable
fun ConsoleButton(
    label: String,
    textColor: Color,
    onPress: () -> Unit,
    onRelease: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        if (isPressed) onPress() else onRelease()
    }

    Button(
        onClick = {},
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .border(2.dp, textColor, RectangleShape),
        colors = ButtonDefaults.buttonColors(containerColor = TerminalBlack),
        shape = RectangleShape
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 24.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}
