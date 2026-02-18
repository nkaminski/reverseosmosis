package io.kaminski.reverseosmosis
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bleManager = WaterDispenserBleManager(this)

        setContent {
            ReverseOsmosisApp(bleManager)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bleManager.disconnect()
    }
}

@Composable
fun ReverseOsmosisApp(bleManager: WaterDispenserBleManager) {
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
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "> REVERSE_OSMOSIS_SYS_V1.0",
                fontFamily = FontFamily.Monospace,
                color = PhosphorGreen,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            if (!hasPermissions) {
                Text(
                    text = ">> ERROR: PERMISSIONS_DENIED\n>> SYSTEM_HALTED",
                    color = AlertRed,
                    fontFamily = FontFamily.Monospace
                )
            } else if (connectionState == WaterDispenserBleManager.ConnectionState.Connected) {
                ControlPanel(bleManager)
            } else {
                ScanPanel(bleManager, connectionState)
            }

            Text(
                text = "STATUS: ${connectionState.name.uppercase()}",
                color = if (connectionState == WaterDispenserBleManager.ConnectionState.Error) AlertRed else PhosphorGreen,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanPanel(bleManager: WaterDispenserBleManager, connectionState: WaterDispenserBleManager.ConnectionState) {
    var macInput by remember { mutableStateOf("") }

    // Regex for MAC Address (XX:XX:XX:XX:XX:XX)
    val macRegex = "^([0-9A-Fa-f]{2}[:]){5}([0-9A-Fa-f]{2})$".toRegex()
    val isValid = macRegex.matches(macInput)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp)
    ) {
        Text(
            text = ">> ENTER_DEVICE_ADDRESS:",
            color = PhosphorGreen,
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
                color = PhosphorGreen,
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            ),
            singleLine = true,
            placeholder = {
                Text(
                    "AA:BB:CC:DD:EE:FF",
                    color = TerminalDimGreen,
                    fontFamily = FontFamily.Monospace
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PhosphorGreen,
                unfocusedBorderColor = TerminalDimGreen,
                cursorColor = PhosphorGreen,
                focusedTextColor = PhosphorGreen,
                unfocusedTextColor = PhosphorGreen,
                focusedContainerColor = TerminalBlack,
                unfocusedContainerColor = TerminalBlack
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrect = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done
            )
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { if (isValid) bleManager.connect(macInput) },
            enabled = isValid && connectionState != WaterDispenserBleManager.ConnectionState.Connecting,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isValid) TerminalDimGreen else DisabledGray,
                disabledContainerColor = DisabledGray
            ),
            shape = RectangleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .border(1.dp, if (isValid) PhosphorGreen else DisabledGray, RectangleShape)
        ) {
            if (connectionState == WaterDispenserBleManager.ConnectionState.Connecting) {
                CircularProgressIndicator(color = PhosphorGreen, modifier = Modifier.size(24.dp))
            } else {
                Text(
                    text = if (isValid) ">> INITIALIZE_LINK" else ">> INVALID_ADDRESS",
                    fontFamily = FontFamily.Monospace,
                    color = if (isValid) PhosphorGreen else Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (!isValid && macInput.isNotEmpty()) {
            Text(
                text = "FORMAT: XX:XX:XX:XX:XX:XX",
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
            label = "[ H O T ]",
            textColor = AlertRed,
            onPress = { bleManager.pressHot() },
            onRelease = { bleManager.releaseButton() }
        )

        ConsoleButton(
            label = "[ A M B I E N T ]",
            textColor = PhosphorGreen,
            onPress = { bleManager.pressAmbient() },
            onRelease = { bleManager.releaseButton() }
        )

        ConsoleButton(
            label = "[ C O L D ]",
            textColor = CoolCyan,
            onPress = { bleManager.pressCold() },
            onRelease = { bleManager.releaseButton() }
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = { bleManager.disconnect() },
            colors = ButtonDefaults.buttonColors(containerColor = TerminalDimGreen),
            shape = RectangleShape,
            modifier = Modifier.border(1.dp, PhosphorGreen)
        ) {
            Text(">> TERMINATE_LINK <<", fontFamily = FontFamily.Monospace, color = PhosphorGreen)
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