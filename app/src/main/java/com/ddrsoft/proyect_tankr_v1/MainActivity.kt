package com.ddrsoft.proyect_tankr_v1

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.UUID

// --- COLORES INTEGRADOS ---
val Blue40 = Color(0xFF2C5EA8)
val Blue90 = Color(0xFFD0E4FF)
val Blue95 = Color(0xFFE8F1FF)
val Cyan60 = Color(0xFF00A0B0)
val RedEmergency = Color(0xFFD32F2F)
val RedLight = Color(0xFFFFCDD2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TankrScreen(innerPadding)
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun TankrScreen(
    innerPadding : PaddingValues = PaddingValues(10.dp),
){
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Variables de estado
    var waterLevel by remember { mutableStateOf(0.0) }
    var connectionStatus by remember { mutableStateOf("Desconectado") }
    var isEmergencyStop by remember { mutableStateOf(false) }
    var btOutputStream by remember { mutableStateOf<OutputStream?>(null) }

    // --- CALIBRACIÓN DEL TANQUE ---
    // Distancia desde el sensor hasta el fondo del contenedor (0% de agua)
    val maxTankHeightCm = 13.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(Blue95, Color.White)))
            .padding(innerPadding)
            .padding(20.dp)
    ) {
        // Header integrado
        Header()

        // TankCard integrado
        TankCard(level = waterLevel)

        // Stats Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        )
        {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "Bluetooth", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = connectionStatus,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if(connectionStatus == "Conectado") Color(0xFF4CAF50) else Blue40
                    )
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "Lectura Cruda", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if(isEmergencyStop) "--- %" else "${(waterLevel * 100).toInt()} %",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if(isEmergencyStop) RedEmergency else Blue40
                    )
                }
            }
        }

        // Tarjeta de Paro de Emergencia
        Card(
            colors = CardDefaults.cardColors(containerColor = if(isEmergencyStop) RedLight else Color.White),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.padding(top = 20.dp)
        )
        {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Paro de Emergencia",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if(isEmergencyStop) RedEmergency else Blue40
                    )
                    Text(
                        text = if (isEmergencyStop) "SISTEMA DETENIDO" else "Sistema Operativo",
                        fontSize = 13.sp,
                        color = if(isEmergencyStop) RedEmergency else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = isEmergencyStop,
                    enabled = connectionStatus == "Conectado",
                    onCheckedChange = { state ->
                        isEmergencyStop = state
                        val comando = if (state) "1" else "0"
                        try {
                            btOutputStream?.write(comando.toByteArray())
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error enviando comando", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = RedEmergency,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Blue90,
                        disabledCheckedTrackColor = Color.Gray,
                        disabledUncheckedTrackColor = Color.LightGray
                    )
                )
            }
        }

        // Boton Conectar Bluetooth
        Button(
            onClick = {
                coroutineScope.launch {
                    connectionStatus = "Conectando..."
                    isEmergencyStop = false

                    val isConnected = connectToBluetoothDevice(
                        context = context,
                        deviceName = "VERDOSO",
                        onConnected = { outputStream ->
                            btOutputStream = outputStream
                        }
                    ) { distanciaLeida ->
                        if (!isEmergencyStop) {
                            try {
                                val distancia = distanciaLeida.trim().toDouble()
                                var porcentaje = 1.0 - (distancia / maxTankHeightCm)
                                if (porcentaje < 0.0) porcentaje = 0.0
                                if (porcentaje > 1.0) porcentaje = 1.0
                                waterLevel = porcentaje
                            } catch (e: Exception) {
                                // Ignorar datos corruptos
                            }
                        }
                    }

                    if (isConnected) {
                        connectionStatus = "Conectado"
                        Toast.makeText(context, "Bluetooth Conectado", Toast.LENGTH_SHORT).show()
                    } else {
                        connectionStatus = "Error"
                        Toast.makeText(context, "Fallo al conectar a VERDOSO", Toast.LENGTH_LONG).show()
                    }
                }
            },
            modifier = Modifier
                .padding(top = 20.dp)
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(20.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
        ) {
            Text(text = "Conectar a Bluetooth")
        }
    }
}

// --- LOGICA DE BLUETOOTH ---
@SuppressLint("MissingPermission")
suspend fun connectToBluetoothDevice(
    context: Context,
    deviceName: String,
    onConnected: (OutputStream) -> Unit,
    onDataReceived: (String) -> Unit
): Boolean = withContext(Dispatchers.IO) {
    try {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter: BluetoothAdapter? = bluetoothManager.adapter

        if (adapter == null || !adapter.isEnabled) return@withContext false

        val device = adapter.bondedDevices?.find { it.name == deviceName } ?: return@withContext false

        val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        val socket = device.createRfcommSocketToServiceRecord(uuid)

        socket.connect()
        onConnected(socket.outputStream)

        Thread {
            try {
                val inputStream = socket.inputStream
                val buffer = ByteArray(1024)
                var readMessage = ""

                while (true) {
                    val bytes = inputStream.read(buffer)
                    val incoming = String(buffer, 0, bytes)
                    readMessage += incoming

                    if (readMessage.contains("\n")) {
                        val cleanData = readMessage.replace("\n", "").replace("\r", "")
                        onDataReceived(cleanData)
                        readMessage = ""
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()

        return@withContext true
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext false
    }
}

// --- COMPONENTES VISUALES ---
@Composable
fun Header() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Tankr Bluetooth", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Blue40)
    }
}

@Composable
fun TankCard(level: Double) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "Nivel de Agua Actual", fontSize = 14.sp, color = Color.Gray)
            Text(
                text = "${(level * 100).toInt()}%",
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = Blue40
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun TankrScreenPreview(){
    MaterialTheme {
        TankrScreen()
    }
}