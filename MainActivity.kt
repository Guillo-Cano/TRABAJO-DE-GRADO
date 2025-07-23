package com.example.balizafinal

//Importaciones de librerias
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.text.style.TextAlign
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.edit
import android.content.res.Configuration

class MainActivity : ComponentActivity() {

    //Variables
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var tts: TextToSpeech
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var speechIntent: Intent
    private var scanning = false
    private var opcionSeleccionada = false
    private var edificioOrigen: String = ""
    private var estadoMenu: String = ""
    private var porteriaOrigen: String = ""
    private var balizaDestinoEsperada: String? = null
    private var balizaOrigen: String? = null
    private var navegandoAHaciaDestino = false
    private val rssiUmbralLlegada = -65
    private var ultimaAdvertenciaTime: Long = 0
    private val retardoAdvertenciaMs = 5000L
    private var finalizandoNavegacion = false
    private var enunciandoAdvertencia = false
    private var mensajeEnProgreso = false
    private var flujoEnCurso = false
    private val dispositivosDetectados = mutableMapOf<String, Int>()
    private var handlerSeleccionDispositivo: Handler? = null
    private val prefsName = "AppPrefs"
    private val keyFlujoIniciado = "flujoIniciado"

    //Mapas
    private val mapaDestinoPorVoz = mapOf(
        "edificio a" to "ESP32_A",
        "edificio b" to "ESP32_B",
        "cafetería" to "ESP32_CAFETERIA",
        "cafeteria" to "ESP32_CAFETERIA",
        "salida calle 72" to "ESP32_CALLE72",
        "salida calle 73" to "ESP32_CALLE73",
        "museo" to "ESP32_MUSEO"
    )
    private val nombresAmigables = mapOf(
        "ESP32_A" to "Edificio A",
        "ESP32_B" to "Edificio B",
        "ESP32_CAFETERIA" to "la cafetería",
        "ESP32_CALLE72" to "la salida por la calle 72",
        "ESP32_CALLE73" to "la salida por la calle 73",
        "ESP32_MUSEO" to "el museo",
        "ESP32_PUNTOMUSEO" to "la sala Santiago Ayala"
    )
    private val informacionRelevante = mapOf(
        "ESP32_A" to "En el primer piso del Edificio A se encuentra la Biblioteca y el Centro Tiflotecnológico. En el segundo piso hay salones de clase. Por otro lado, en el tercer piso de este edificio están ubicados el Departamento de Lenguas y el Departamento de Sociales.",
        "ESP32_B" to "En el primer piso del Edificio B se encuentra el Departamento de Tecnología y la Enfermería, en el segundo piso está el Departamento de Matemáticas. En el tercer piso se encuentran los Departamentos de Biología, Química y Física.",
        "ESP32_CAFETERIA" to "En la Cafetería se ofrecen diferentes horarios de comida: el desayuno está disponible desde las 8 de la mañana hasta las 10 de la mañana, y el almuerzo se puede adquirir de 11:30 a 2 de la tarde.",
        "ESP32_CALLE72" to "Estás en la portería de la Calle 72, la Universidad opera de 6 de la mañana a 6 de la tarde. No olvides el carnet para ingresar.",
        "ESP32_CALLE73" to "Estás en la portería de la Calle 73, la Universidad opera de 6 de la mañana a 6 de la tarde. No olvides el carnet para ingresar.",
        "ESP32_MUSEO" to "En el Museo, también llamado la casita de la vida, se exhibe la historia natural y diversas colecciones, tales como: entomológica, alcohólica, taxidérmica, malacológica, colección viva e invernaderos. Está abierto a todo tipo de público de 8 a 12 de la mañana y de 2 a 5 de la tarde. La visita al museo siempre es guiada."
    )
    private val instruccionesInternas = mapOf(
        "centro tiflotecnológico" to "Desde la entrada del edificio A, Siga derecho para entrar a la biblioteca. Luego, gire a la derecha y avance aproximadamente 35 pasos. Después, gire a la izquierda y habrá llegado al centro tiflotecnológico.",
        "departamento de tecnología" to "Desde la entrada del edificio B, Siga derecho aproximadamente 60 pasos. Después, gire a la izquierda, avance 5 pasos más y habrá llegado al departamento de tecnología.",
        "museo" to "Desde la entrada principal del museo, avance derecho unos 20 pasos y encontrará la primera sala de exposición."
    )
    private val deviceNames =
        listOf(
            "ESP32_A",
            "ESP32_B",
            "ESP32_CAFETERIA",
            "ESP32_CALLE72",
            "ESP32_CALLE73",
            "ESP32_MUSEO",
            "ESP32_PUNTOMUSEO"
        )

    //Creación e iniciación de instancias
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // Verifica si el Bluetooth está desactivado
        if (!bluetoothAdapter.isEnabled) {
            solicitarReinicioManualBluetooth()
        }

        // Verifica si el escáner BLE está disponible
        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            Toast.makeText(this, "El escáner BLE no está disponible. Reinicia el Bluetooth desde ajustes.", Toast.LENGTH_LONG).show()
            solicitarReinicioManualBluetooth()
            return
        }

        // Inicia TTS solo si el flujo aún no ha comenzado
        if (!flujoEnCurso) {
            tts = TextToSpeech(this) { status ->
                if (status != TextToSpeech.ERROR) {
                    tts.language = Locale.getDefault()
                    flujoEnCurso = true
                    getSharedPreferences(prefsName, MODE_PRIVATE).edit {
                        putBoolean(keyFlujoIniciado, true)
                    }
                    hablar("Bienvenido al sistema de orientación") {
                        preguntarPorEscaneo()
                    }
                }
            }
        } else {
            if (!::tts.isInitialized) {
                tts = TextToSpeech(this) { status ->
                    if (status != TextToSpeech.ERROR) {
                        tts.language = Locale.getDefault()
                    }
                }
            }
        }

        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        solicitarPermisos()

        setContent {
            AppScreen()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d("ConfigChange", "Cambio de orientación detectado sin reinicio")
    }

    //Función solicitar permisos
    private fun solicitarPermisos() {
        val permisosNecesarios = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permisosNecesarios.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permisosNecesarios.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permisosNecesarios.add(Manifest.permission.RECORD_AUDIO)
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permisosNecesarios.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permisosNecesarios.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permisosNecesarios.toTypedArray(), 1)
        }
    }

    //Verificar permisos
    private fun dispositivoTieneBLE(): Boolean {
        return packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    private fun bluetoothEstaActivado(): Boolean {
        val bluetoothAdapter = (getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled
    }

    private fun gpsEstaActivado(): Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun tieneConexionInternet(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities != null && (
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                )
    }

    //Verificar condiciones
    private fun verificarCondicionesIniciales(): Boolean {
        if (!dispositivoTieneBLE()) {
            hablar("Este dispositivo no es compatible con Bluetooth de baja energía. No se puede usar esta aplicación.") {
                preguntarPorEscaneo()
            }
            return false
        }

        if (!bluetoothEstaActivado()) {
            hablar("El Bluetooth está apagado. Por favor, actívelo para continuar.") {
                preguntarPorEscaneo()
            }
            return false
        }

        if (!gpsEstaActivado()) {
            hablar("La ubicación está desactivada. Por favor, active el GPS para detectar las balizas.") {
                preguntarPorEscaneo()
            }
            return false
        }

        if (!tieneConexionInternet()) {
            hablar("No hay conexión a internet. Por favor, conéctese a una red Wi-Fi o datos móviles para continuar.") {
                preguntarPorEscaneo()
            }
            return false
        }

        return true
    }

    //Función de vibrar
    private fun vibrar() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Patrón: vibra 200ms, pausa 100ms, vibra 300ms
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 300), -1)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 200, 100, 300), -1)
        }
    }

    //Escaner actualizado
    private fun obtenerScannerBLE() = bluetoothAdapter.bluetoothLeScanner

    //Función de iniar escaneo
    private fun startScan() {
        if (scanning) return

        // Verificar permisos
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED))
        ) {
            solicitarPermisos()
            Toast.makeText(this, "Faltan permisos para iniciar el escaneo.", Toast.LENGTH_SHORT).show()
            return
        }

        // Verificar condiciones iniciales (BLE, GPS, red, etc.)
        if (!verificarCondicionesIniciales()) return

        try {
            // Revalidar adaptador y escáner
            val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothAdapter = bluetoothManager.adapter

            if (!bluetoothAdapter.isEnabled) {
                solicitarReinicioManualBluetooth()
                return
            }

            val scanner = bluetoothAdapter.bluetoothLeScanner
            if (scanner == null) {
                Toast.makeText(this, "El escáner BLE no está disponible. Reinicia el Bluetooth desde ajustes.", Toast.LENGTH_LONG).show()
                solicitarReinicioManualBluetooth()
                return
            }

            // Inicializar estado de escaneo
            opcionSeleccionada = false
            estadoMenu = ""
            dispositivosDetectados.clear()
            handlerSeleccionDispositivo?.removeCallbacksAndMessages(null)
            handlerSeleccionDispositivo = null

            // Limpiar resultados pendientes y comenzar escaneo
            try {
                scanner.flushPendingScanResults(scanCallback)
            } catch (e: Exception) {
                Log.w("BLE", "No se pudieron limpiar resultados previos", e)
            }

            scanner.startScan(scanCallback)
            scanning = true
            Toast.makeText(this, "Escaneando dispositivos...", Toast.LENGTH_SHORT).show()

        } catch (e: SecurityException) {
            Toast.makeText(this, "No se pudo iniciar el escaneo: falta permiso", Toast.LENGTH_SHORT).show()
            Log.e("BLE", "Error al iniciar escaneo", e)
        } catch (e: Exception) {
            Toast.makeText(this, "Error inesperado al iniciar escaneo", Toast.LENGTH_SHORT).show()
            Log.e("BLE", "Error inesperado al iniciar escaneo", e)
        }
    }

    //Función de detener escaneo
    private fun stopScan() {
        if (scanning) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val permisoScan = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                val permisoConnect = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                if (permisoScan != PackageManager.PERMISSION_GRANTED || permisoConnect != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permisos de Bluetooth no concedidos", Toast.LENGTH_SHORT).show()
                    return
                }
            }

            try {
                obtenerScannerBLE()?.stopScan(scanCallback)
                scanning = false
                Toast.makeText(this, "Escaneo detenido.", Toast.LENGTH_SHORT).show()
            } catch (e: SecurityException) {
                Toast.makeText(this, "No se pudo detener el escaneo: falta permiso", Toast.LENGTH_SHORT).show()
                Log.e("BLE", "Error al detener escaneo", e)
            }
        }
    }

    //Función para obtener recomendacion de ubicaciónn
    private fun obtenerRecomendacionSegunDestino(destino: String): String {
        return when (destino) {
            "ESP32_B" -> "Recomendación: La cafetería queda muy cerca del edificio B. Además, la salida por la calle 73 también está cerca del edificio B y la cafetería."
            "ESP32_CAFETERIA" -> "Recomendación: La cafetería está muy cerca del edificio B. Además, la salida por la calle 73 también está cerca del edificio B y la cafetería."
            "ESP32_CALLE73" -> "Recomendación: La salida por la calle 73 queda cerca del edificio B y de la cafetería."
            "ESP32_A" -> "Recomendación: El edificio A queda cerca de la salida por la calle 72."
            "ESP32_CALLE72" -> "Recomendación: La salida por la calle 72 queda cerca del edificio A."
            "ESP32_MUSEO" -> "Recomendación: El museo está ubicado cerca a la salida por calle 73 y el Edificio B."
            else -> ""
        }
    }

    //Función de inicar navegación
    private fun iniciarNavegacion(origen: String, destino: String) {
        balizaOrigen = origen
        balizaDestinoEsperada = destino
        navegandoAHaciaDestino = true
        stopScan()

        val destinoAmigable = nombresAmigables[destino] ?: destino
        val recomendacion = obtenerRecomendacionSegunDestino(destino)

        hablar(recomendacion) {
            hablar("Iniciando navegación hacia $destinoAmigable. Por favor avance.") {
                Handler(Looper.getMainLooper()).postDelayed({
                    startScan()
                }, 500)
            }
        }
    }

    //Función escanear esp32
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val deviceName: String = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val permisoConnect = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.BLUETOOTH_CONNECT
                    )
                    if (permisoConnect != PackageManager.PERMISSION_GRANTED) return
                }
                result.device.name ?: return
            } catch (e: SecurityException) {
                Log.e("BLE", "Error al obtener el nombre del dispositivo BLE", e)
                return
            }

            if (!deviceNames.contains(deviceName)) return
            if (finalizandoNavegacion) return
            if (deviceName == balizaOrigen) return

            val rssi = result.rssi
            dispositivosDetectados[deviceName] = rssi

            if (navegandoAHaciaDestino) {
                if (deviceName == balizaDestinoEsperada && rssi > rssiUmbralLlegada && !mensajeEnProgreso) {
                    navegandoAHaciaDestino = false
                    finalizandoNavegacion = true
                    balizaDestinoEsperada = null
                    balizaOrigen = null
                    stopScan()

                    val mensajeFinal = if (deviceName == "ESP32_PUNTOMUSEO") {
                        "Has llegado a la sala Santiago Ayala, en esta sala encontraras una colección disecada de varias espacies de animales."
                    } else {
                        "Has llegado a tu destino: ${nombresAmigables[deviceName] ?: deviceName}."
                    }

                    hablar(mensajeFinal) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            finalizandoNavegacion = false
                            reiniciarFlujoLogico()
                        }, 5000)
                    }
                    return
                }

                if (deviceName != balizaDestinoEsperada && !enunciandoAdvertencia && !mensajeEnProgreso) {
                    val tiempoActual = System.currentTimeMillis()
                    if (tiempoActual - ultimaAdvertenciaTime > retardoAdvertenciaMs) {
                        ultimaAdvertenciaTime = tiempoActual
                        enunciandoAdvertencia = true
                        val nombreDetectado = nombresAmigables[deviceName] ?: deviceName

                        hablar("Este no es tu destino. Estás en $nombreDetectado. Sigue avanzando.") {
                            Handler(Looper.getMainLooper()).postDelayed({
                                enunciandoAdvertencia = false
                            }, 1000)
                        }
                    }
                    return
                }

                return
            }

            // Si no estamos navegando y aún no se ha iniciado el temporizador
            if (handlerSeleccionDispositivo == null) {
                handlerSeleccionDispositivo = Handler(Looper.getMainLooper())
                handlerSeleccionDispositivo?.postDelayed({
                    seleccionarDispositivoMasCercano()
                }, 1000)
            }
        }
    }

    //Seleccionar dispositivo mas cercano
    private fun seleccionarDispositivoMasCercano() {
        stopScan()
        handlerSeleccionDispositivo = null

        if (dispositivosDetectados.isEmpty()) {
            hablar("No se detectaron dispositivos cercanos. ¿Desea intentar escanear de nuevo?") {
                preguntarPorEscaneo()
            }
            return
        }

        val dispositivoMasCercano = dispositivosDetectados.maxByOrNull { it.value }
        dispositivosDetectados.clear()

        val deviceName = dispositivoMasCercano?.key ?: return
        val mensaje = "Bienvenido"
        val menuOpciones = when (deviceName) {
            "ESP32_A" -> listOf("1 para centro tiflotecnológico", "2 para otro edificio", "3 para salir")
            "ESP32_B" -> listOf("1 para departamento de tecnología", "2 para otro edificio", "3 para salir")
            "ESP32_CAFETERIA" -> listOf("1 para quedarse en cafetería", "2 para otro edificio", "3 para salir")
            "ESP32_CALLE72" -> listOf("1 para ir a edificio", "2 para salida calle 73")
            "ESP32_CALLE73" -> listOf("1 para ir a edificio", "2 para salida calle 72")
            "ESP32_MUSEO" -> listOf("1 para entrar al museo", "2 para otro edificio", "3 para salir")
            else -> emptyList()
        }

        val textoMenu = menuOpciones.joinToString(". ", prefix = "Opciones: ")
        vibrar()
        Toast.makeText(this, "Dispositivo más cercano: $deviceName", Toast.LENGTH_SHORT).show()
        hablar("Llegando a ${nombresAmigables[deviceName] ?: deviceName}.") {
            preguntarInformacionRelevante(mensaje, textoMenu, deviceName)
        }
    }

    //Función preguntar por escaneo
    private fun preguntarPorEscaneo() {
        stopScan()
        hablar("¿Desea comenzar el escaneo? Diga sí para iniciar o no para esperar.") {
            Handler(Looper.getMainLooper()).postDelayed({
                escucharRespuestaEscaneo()
            }, 500)
        }
    }

    //Función preguntar de información relevante
    private fun preguntarInformacionRelevante(
        mensaje: String,
        textoMenu: String,
        deviceName: String
    ) {
        hablar("Hay información relevante en el punto. ¿Desea escucharla? Diga sí o no.") {
            Handler(Looper.getMainLooper()).postDelayed({
                escucharRespuestaInformacion(mensaje, textoMenu, deviceName)
            }, 500) 
        }
    }

    //Función de escuchar respuerta a información relevante
    private fun escucharRespuestaInformacion(
        mensaje: String,
        textoMenu: String,
        deviceName: String
    ) {
        speechRecognizer?.apply {
            cancel()
            destroy()
        }
        speechRecognizer = null

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "El reconocimiento de voz no está disponible en este momento.", Toast.LENGTH_LONG).show()
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    hablar("No entendí. ¿Desea escuchar la información relevante? Diga sí o no.") {
                        escucharRespuestaInformacion(mensaje, textoMenu, deviceName)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val respuesta = matches?.firstOrNull()?.lowercase()?.trim()

                    Log.d("Voz", "Respuesta a información relevante: $respuesta")

                    if (respuesta?.contains("sí") == true || respuesta?.contains("si") == true) {
                        val info = informacionRelevante[deviceName] ?: "Información no disponible."
                        hablar(info) {
                            vibrar()
                            hablar(textoMenu) {
                                if (!opcionSeleccionada) {
                                    escucharOpcion(deviceName)
                                }
                            }
                        }
                    } else if (respuesta?.contains("no") == true) {
                        hablar(textoMenu) {
                            if (!opcionSeleccionada) {
                                escucharOpcion(deviceName)
                            }
                        }
                    } else {
                        hablar("No entendí. ¿Desea escuchar la información relevante? Diga sí o no.") {
                            escucharRespuestaInformacion(mensaje, textoMenu, deviceName)
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            startListening(speechIntent)
        }
    }

    //Función esuchar respuesta escaneo
    private fun escucharRespuestaEscaneo() {
        speechRecognizer?.apply {
            cancel()
            destroy()
        }
        speechRecognizer = null

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "El reconocimiento de voz no está disponible en este momento.", Toast.LENGTH_LONG).show()
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    hablar("No entendí. ¿Desea comenzar el escaneo? Diga sí o no.") {
                        escucharRespuestaEscaneo()
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val respuesta = matches?.firstOrNull()?.lowercase()?.trim()

                    Log.d("Voz", "Respuesta a escanear: $respuesta")

                    if (respuesta?.contains("sí") == true || respuesta?.contains("si") == true) {
                        startScan()
                    } else if (respuesta?.contains("no") == true) {
                        hablar("Esperando para volver a preguntar.") {
                            Handler(mainLooper).postDelayed({
                                preguntarPorEscaneo()
                            }, 10000)
                        }
                    } else {
                        hablar("No entendí. Diga sí para iniciar o no para esperar.") {
                            escucharRespuestaEscaneo()
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            startListening(speechIntent)
        }
    }

    //Función de hablar
    private fun hablar(texto: String, onFinish: (() -> Unit)? = null) {
        if (tts.isSpeaking) {
            Handler(Looper.getMainLooper()).postDelayed({
                hablar(texto, onFinish)
            }, 500)
            return
        }

        if (mensajeEnProgreso) {
            // Si ya se está hablando, encola el nuevo mensaje sin interrumpir
            tts.speak(texto, TextToSpeech.QUEUE_ADD, null, "TTS_ID_${System.currentTimeMillis()}")
            return
        }

        mensajeEnProgreso = true

        val utteranceId = "TTS_ID_${System.currentTimeMillis()}"

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                runOnUiThread {
                    Handler(Looper.getMainLooper()).postDelayed({
                        mensajeEnProgreso = false
                        onFinish?.invoke()
                    }, 500)
                }
            }

            override fun onError(utteranceId: String?) {
                runOnUiThread {
                    mensajeEnProgreso = false
                    onFinish?.invoke()
                }
            }
        })

        tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    //Función de obtener opciones
    private fun getOpcionesOtroEdificio(actual: String): List<String> {
        return when (actual) {
            "A" -> listOf("1 Edificio B", "2 Cafetería", "3 Museo")
            "B" -> listOf("1 Edificio A", "2 Cafetería", "3 Museo")
            "CAFETERIA" -> listOf("1 Edificio A", "2 Edificio B", "3 Museo")
            "MUSEO" -> listOf("1 Edificio A", "2 Edificio B", "3 Cafetería")
            "PORTERIA" -> listOf("1 Edificio A", "2 Edificio B", "3 Cafetería", "4 Museo")
            else -> listOf("1 Edificio A", "2 Cafetería", "3 Museo")
        }
    }

    //Función de mensajes error submenús edificios
    private fun getMensajeSubmenuEdificioDesdeOrigen(): String {
        return if (edificioOrigen == "PORTERIA") {
            getMensajeEdificiosDesdePorteria()
        } else if (edificioOrigen == "ESP32_MUSEO") {
            "Diga 1 para ir al Edificio A, 2 para ir al Edificio B, o 3 para ir a la Cafetería"
        } else {
            val opciones = getOpcionesOtroEdificio(edificioOrigen)
            val mensaje = opciones.joinToString(", ") {
                val num = it.substringBefore(" ")
                val nombre = it.substringAfter(" ")
                "$num para ir a $nombre"
            }
            "Diga $mensaje"
        }
    }

    //Función mensaje error submenú salida
    private fun getMensajeSubmenuSalida(): String {
        return "Diga 1 para salida por calle 72 y 2 para salida por calle 73"
    }

    //Función mensaje error submenú edificio
    private fun getMensajeOpcionesA(): String {
        return "Las opciones disponibles son: diga 1 para ir al centro tiflotecnológico, 2 para otro edificio, 3 para salir"
    }

    //Función mensaje error submenú edificio
    private fun getMensajeOpcionesB(): String {
        return "Las opciones disponibles son: diga 1 para ir al departamento de tecnología, 2 para otro edificio, 3 para salir"
    }

    //Función mensaje error submenú edificio
    private fun getMensajeOpcionesCafeteria(): String {
        return "Las opciones disponibles son: diga 1 para quedarse en cafetería, 2 para otro edificio, 3 para salir"
    }

    //Función mensaje error submenú salida
    private fun getMensajeEdificiosDesdePorteria(): String {
        return "Diga 1 para ir al Edificio A, 2 para ir al Edificio B, 3 para ir a la Cafetería, o 4 para ir al Museo"
    }

    //Función escuchar opción
    private fun escucharOpcion(estado: String) {
        estadoMenu = estado

        speechRecognizer?.apply {
            cancel()
            destroy()
        }
        speechRecognizer = null

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "El reconocimiento de voz no está disponible en este momento.", Toast.LENGTH_LONG).show()
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    runOnUiThread {
                        val mensajeError = when (estadoMenu) {
                            "SUBMENU_EDIFICIO" -> {
                                if (edificioOrigen == "PORTERIA") {
                                    "No entendí. ${getMensajeEdificiosDesdePorteria()}"
                                } else {
                                    "No entendí. ${getMensajeSubmenuEdificioDesdeOrigen()}"
                                }
                            }
                            "SUBMENU_SALIDA" -> "No entendí. ${getMensajeSubmenuSalida()}"
                            else -> "No entendí. Por favor repita su elección."
                        }
                        hablar(mensajeError) { escucharOpcion(estadoMenu) }
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val recognizedText = matches?.firstOrNull()?.lowercase(Locale.getDefault())?.trim()

                    Log.d("Reconocimiento", "Texto reconocido: $recognizedText en estado: $estadoMenu")

                    val opcionValida = when {
                        recognizedText?.matches(Regex(".*\\b(1|uno)\\b.*")) == true -> "1"
                        recognizedText?.matches(Regex(".*\\b(2|dos)\\b.*")) == true -> "2"
                        recognizedText?.matches(Regex(".*\\b(3|tres)\\b.*")) == true -> "3"
                        recognizedText?.matches(Regex(".*\\b(4|cuatro)\\b.*")) == true -> "4"
                        else -> null
                    }

                    runOnUiThread {
                        when (estadoMenu) {
                            "ESP32_A" -> procesarOpcionA(recognizedText)
                            "ESP32_B" -> procesarOpcionB(recognizedText)
                            "ESP32_CAFETERIA" -> procesarOpcionCafeteria(recognizedText)
                            "ESP32_CALLE72" -> procesarOpcionCalle72(recognizedText)
                            "ESP32_CALLE73" -> procesarOpcionCalle73(recognizedText)
                            "ESP32_MUSEO" -> procesarOpcionMuseo(recognizedText)

                            "SUBMENU_EDIFICIO", "SUBMENU_SALIDA" -> {
                                val opcionEsValida = when (estadoMenu) {
                                    "SUBMENU_EDIFICIO" -> edificioOrigen != "PORTERIA" || opcionValida in listOf("1", "2", "3", "4")
                                    "SUBMENU_SALIDA" -> opcionValida in listOf("1", "2")
                                    else -> false
                                }

                                if (opcionValida != null && opcionEsValida) {
                                    if (estadoMenu == "SUBMENU_EDIFICIO") {
                                        procesarSubmenuEdificio(recognizedText)
                                    } else if (estadoMenu == "SUBMENU_SALIDA") {
                                        procesarSubmenuSalida(recognizedText)
                                    }
                                } else {
                                    val mensajeError = when (estadoMenu) {
                                        "SUBMENU_EDIFICIO" -> {
                                            if (edificioOrigen == "PORTERIA") {
                                                "Opción no válida. ${getMensajeEdificiosDesdePorteria()}"
                                            } else {
                                                "Opción no válida. ${getMensajeSubmenuEdificioDesdeOrigen()}"
                                            }
                                        }
                                        "SUBMENU_SALIDA" -> "Opción no válida. ${getMensajeSubmenuSalida()}"
                                        else -> "Opción no válida."
                                    }
                                    hablar(mensajeError) { escucharOpcion(estadoMenu) }
                                }
                            }

                            else -> {
                                hablar("Opción no válida. Inténtelo de nuevo.") {
                                    escucharOpcion(estadoMenu)
                                }
                            }
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            startListening(speechIntent)
        }
    }

    //Función procesar menú A
    private fun procesarOpcionA(recognizedText: String?) {
        when {
            recognizedText?.contains("1") == true || recognizedText?.contains("uno") == true -> {
                opcionSeleccionada = true
                val instrucciones = instruccionesInternas["centro tiflotecnológico"]
                hablar("Usted eligió centro tiflotecnológico.") {
                    hablar(instrucciones ?: "Instrucciones no disponibles.") {
                        preguntarPorEscaneo()
                    }
                }
            }

            recognizedText?.contains("2") == true || recognizedText?.contains("dos") == true -> {
                val opciones = getOpcionesOtroEdificio("A")
                val textoOpciones =
                    "Diga 1 para ir a ${opciones[0].substringAfter(" ")}, 2 para ir a ${
                        opciones[1].substringAfter(" ")
                    }, o 3 para ir a ${opciones[2].substringAfter(" ")}"
                edificioOrigen = "ESP32_A"
                estadoMenu = "SUBMENU_EDIFICIO"
                vibrar()
                hablar(textoOpciones) {
                    escucharOpcion("SUBMENU_EDIFICIO")
                }
            }

            recognizedText?.contains("3") == true || recognizedText?.contains("tres") == true -> {
                edificioOrigen = "ESP32_A"
                estadoMenu = "SUBMENU_SALIDA"
                hablar("Opciones de salida: 1 para calle 72, 2 para calle 73") {
                    escucharOpcion("SUBMENU_SALIDA")
                }
            }

            else -> {
                hablar("Opción no válida. ${getMensajeOpcionesA()}") {
                    escucharOpcion("ESP32_A")
                }
            }
        }
    }

    //Función procesar menú B
    private fun procesarOpcionB(recognizedText: String?) {
        when {
            recognizedText?.contains("1") == true || recognizedText?.contains("uno") == true -> {
                opcionSeleccionada = true
                val instrucciones = instruccionesInternas["departamento de tecnología"]
                hablar("Usted eligió departamento de tecnología.") {
                    hablar(instrucciones ?: "Instrucciones no disponibles.") {
                        preguntarPorEscaneo()
                    }
                }
            }

            recognizedText?.contains("2") == true || recognizedText?.contains("dos") == true -> {
                val opciones = getOpcionesOtroEdificio("B")
                val textoOpciones =
                    "Diga 1 para ir a ${opciones[0].substringAfter(" ")}, 2 para ir a ${
                        opciones[1].substringAfter(" ")
                    }, o 3 para ir a ${opciones[2].substringAfter(" ")}"
                edificioOrigen = "ESP32_B"
                estadoMenu = "SUBMENU_EDIFICIO"
                vibrar()
                hablar(textoOpciones) {
                    escucharOpcion("SUBMENU_EDIFICIO")
                }
            }

            recognizedText?.contains("3") == true || recognizedText?.contains("tres") == true -> {
                edificioOrigen = "ESP32_B"
                estadoMenu = "SUBMENU_SALIDA"
                hablar("Opciones de salida: 1 para calle 72, 2 para calle 73") {
                    escucharOpcion("SUBMENU_SALIDA")
                }
            }

            else -> {
                hablar("Opción no válida. ${getMensajeOpcionesB()}") {
                    escucharOpcion("ESP32_B")
                }
            }
        }
    }

    //Función procesar menú cafeteria
    private fun procesarOpcionCafeteria(recognizedText: String?) {
        when {
            recognizedText?.contains("1") == true || recognizedText?.contains("uno") == true -> {
                opcionSeleccionada = true
                hablar("Usted eligió quedarse en cafetería.") {
                    preguntarPorEscaneo()
                }
            }

            recognizedText?.contains("2") == true || recognizedText?.contains("dos") == true -> {
                val opciones = getOpcionesOtroEdificio("CAFETERIA")
                val textoOpciones =
                    "Diga 1 para ir a ${opciones[0].substringAfter(" ")}, 2 para ir a ${
                        opciones[1].substringAfter(" ")
                    }, o 3 para ir a ${opciones[2].substringAfter(" ")}"
                edificioOrigen = "ESP32_CAFETERIA"
                estadoMenu = "SUBMENU_EDIFICIO"
                vibrar()
                hablar(textoOpciones) {
                    escucharOpcion("SUBMENU_EDIFICIO")
                }
            }

            recognizedText?.contains("3") == true || recognizedText?.contains("tres") == true -> {
                edificioOrigen = "ESP32_CAFETERIA"
                estadoMenu = "SUBMENU_SALIDA"
                hablar("Opciones de salida: 1 para calle 72, 2 para calle 73") {
                    escucharOpcion("SUBMENU_SALIDA")
                }
            }

            else -> {
                hablar("Opción no válida. ${getMensajeOpcionesCafeteria()}") {
                    escucharOpcion("ESP32_CAFETERIA")
                }
            }
        }
    }

    //Función procesar menú calle 72
    private fun procesarOpcionCalle72(recognizedText: String?) {
        when {
            recognizedText?.contains("1") == true || recognizedText?.contains("uno") == true -> {
                opcionSeleccionada = false
                edificioOrigen = "PORTERIA"
                porteriaOrigen = "ESP32_CALLE72"
                estadoMenu = "SUBMENU_EDIFICIO"
                vibrar()
                hablar(getMensajeEdificiosDesdePorteria()) {
                    escucharOpcion("SUBMENU_EDIFICIO")
                }
            }

            recognizedText?.contains("2") == true || recognizedText?.contains("dos") == true -> {
                opcionSeleccionada = true
                val destinoTexto = "salida calle 73"
                val balizaDestino = mapaDestinoPorVoz[destinoTexto]
                if (balizaDestino != null) {
                    val destinoAmigable = nombresAmigables[balizaDestino] ?: destinoTexto
                    hablar("Usted eligió ir a $destinoAmigable.") {
                        iniciarNavegacion("ESP32_CALLE72", balizaDestino)
                    }
                } else {
                    hablar("Destino no reconocido. Intente de nuevo.") {
                        escucharOpcion("ESP32_CALLE72")
                    }
                }
            }

            else -> {
                estadoMenu = "ESP32_CALLE72"
                hablar("Opción no válida. Diga 1 para ir a un edificio, o 2 para salida por calle 73.") {
                    escucharOpcion("ESP32_CALLE72")
                }
            }
        }
    }

    //Función procesar menú calle 73
    private fun procesarOpcionCalle73(recognizedText: String?) {
        when {
            recognizedText?.contains("1") == true || recognizedText?.contains("uno") == true -> {
                opcionSeleccionada = false
                edificioOrigen = "PORTERIA"
                porteriaOrigen = "ESP32_CALLE73"
                estadoMenu = "SUBMENU_EDIFICIO"
                vibrar()
                hablar(getMensajeEdificiosDesdePorteria()) {
                    escucharOpcion("SUBMENU_EDIFICIO")
                }
            }

            recognizedText?.contains("2") == true || recognizedText?.contains("dos") == true -> {
                opcionSeleccionada = true
                val destinoTexto = "salida calle 72"
                val balizaDestino = mapaDestinoPorVoz[destinoTexto]
                if (balizaDestino != null) {
                    val destinoAmigable = nombresAmigables[balizaDestino] ?: destinoTexto
                    hablar("Usted eligió ir a $destinoAmigable.") {
                        iniciarNavegacion("ESP32_CALLE73", balizaDestino)
                    }
                } else {
                    hablar("Destino no reconocido. Intente de nuevo.") {
                        escucharOpcion("ESP32_CALLE73")
                    }
                }
            }

            else -> {
                estadoMenu = "ESP32_CALLE73"
                hablar("Opción no válida. Diga 1 para ir a un edificio, o 2 para salida por calle 72.") {
                    escucharOpcion("ESP32_CALLE73")
                }
            }
        }
    }

    //Función procesar museo
    private fun procesarOpcionMuseo(recognizedText: String?) {
        when {
            recognizedText?.contains("1") == true || recognizedText?.contains("uno") == true -> {
                opcionSeleccionada = true
                hablar("Desde la entrada del museo, siga derecho 10 pasos y gire a la izquierda.") {
                    iniciarNavegacion("ESP32_MUSEO", "ESP32_PUNTOMUSEO")
                }
            }

            recognizedText?.contains("2") == true || recognizedText?.contains("dos") == true -> {
                val opciones = getOpcionesOtroEdificio("MUSEO")
                val textoOpciones =
                    "Diga 1 para ir a ${opciones[0].substringAfter(" ")}, 2 para ir a ${
                        opciones[1].substringAfter(" ")
                    }, o 3 para ir a ${opciones[2].substringAfter(" ")}"
                edificioOrigen = "ESP32_MUSEO"
                estadoMenu = "SUBMENU_EDIFICIO"
                vibrar()
                hablar(textoOpciones) {
                    escucharOpcion("SUBMENU_EDIFICIO")
                }
            }

            recognizedText?.contains("3") == true || recognizedText?.contains("tres") == true -> {
                edificioOrigen = "ESP32_MUSEO"
                estadoMenu = "SUBMENU_SALIDA"
                hablar("Opciones de salida: 1 para calle 72, 2 para calle 73") {
                    escucharOpcion("SUBMENU_SALIDA")
                }
            }

            else -> {
                hablar("Opción no válida. Diga 1 para entrar al museo, 2 para otro edificio, o 3 para salir.") {
                    escucharOpcion("ESP32_MUSEO")
                }
            }
        }
    }

    //Función submenú edificios
    private fun procesarSubmenuEdificio(recognizedText: String?) {
        val textoLimpio = recognizedText?.lowercase()?.trim() ?: ""
        Log.d("SubmenuEdificio", "Texto reconocido: $textoLimpio")

        val opcionIndex = when {
            textoLimpio.contains("uno") || textoLimpio == "1" -> 0
            textoLimpio.contains("dos") || textoLimpio == "2" -> 1
            textoLimpio.contains("tres") || textoLimpio == "3" -> 2
            textoLimpio.contains("cuatro") || textoLimpio == "4" -> 3
            else -> -1
        }

        Log.d("SubmenuEdificio", "Índice detectado: $opcionIndex")

        val balizaDestino = when (edificioOrigen) {
            "PORTERIA" -> when (opcionIndex) {
                0 -> "ESP32_A"
                1 -> "ESP32_B"
                2 -> "ESP32_CAFETERIA"
                3 -> "ESP32_MUSEO"
                else -> null
            }

            "ESP32_A" -> when (opcionIndex) {
                0 -> "ESP32_B"
                1 -> "ESP32_CAFETERIA"
                2 -> "ESP32_MUSEO"
                else -> null
            }

            "ESP32_B" -> when (opcionIndex) {
                0 -> "ESP32_A"
                1 -> "ESP32_CAFETERIA"
                2 -> "ESP32_MUSEO"
                else -> null
            }

            "ESP32_CAFETERIA" -> when (opcionIndex) {
                0 -> "ESP32_A"
                1 -> "ESP32_B"
                2 -> "ESP32_MUSEO"
                else -> null
            }

            "ESP32_MUSEO" -> when (opcionIndex) {
                0 -> "ESP32_A"
                1 -> "ESP32_B"
                2 -> "ESP32_CAFETERIA"
                else -> null
            }

            else -> null
        }

        if (balizaDestino == null) {
            val mensajeError =
                if (edificioOrigen == "PORTERIA") getMensajeEdificiosDesdePorteria() else getMensajeSubmenuEdificioDesdeOrigen()
            hablar("Opción no válida. $mensajeError") {
                escucharOpcion("SUBMENU_EDIFICIO")
            }
            return
        }

        val destinoAmigable = nombresAmigables[balizaDestino] ?: "el destino"
        val origenReal = if (edificioOrigen == "PORTERIA") porteriaOrigen else edificioOrigen

        opcionSeleccionada = true
        vibrar()
        hablar("Usted eligió ir a $destinoAmigable.") {
            iniciarNavegacion(origenReal, balizaDestino)
        }
    }

    //Función procesar submenú salida
    private fun procesarSubmenuSalida(recognizedText: String?) {
        val textoLimpio = recognizedText?.lowercase()?.trim()

        val destinoTexto = when (textoLimpio) {
            "1", "uno" -> "salida calle 72"
            "2", "dos" -> "salida calle 73"
            else -> null
        }

        if (destinoTexto == null || !mapaDestinoPorVoz.containsKey(destinoTexto)) {
            hablar("Opción no válida. ${getMensajeSubmenuSalida()}") {
                escucharOpcion("SUBMENU_SALIDA")
            }
            return
        }

        val balizaDestino = mapaDestinoPorVoz[destinoTexto]!!
        val destinoAmigable = nombresAmigables[balizaDestino] ?: destinoTexto
        val origenReal = if (edificioOrigen == "PORTERIA") porteriaOrigen else edificioOrigen

        opcionSeleccionada = true
        vibrar()
        hablar("Usted eligió ir a $destinoAmigable.") {
            iniciarNavegacion(origenReal, balizaDestino)
        }
    }

    //Reiniciar instacias
    private fun solicitarReinicioManualBluetooth() {
        Toast.makeText(this, "Se requiere reiniciar el Bluetooth para continuar.", Toast.LENGTH_LONG).show()
        val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
        startActivity(intent)
    }

    //Cerrar instancias
    override fun onDestroy() {
        // Detener escaneo si está activo
        stopScan()

        // Destruir reconocimiento de voz
        try {
            speechRecognizer?.apply {
                stopListening()
                destroy()
            }
            speechRecognizer = null
        } catch (e: Exception) {
            Log.e("onDestroy", "Error al destruir SpeechRecognizer", e)
        }

        // Detener TextToSpeech
        try {
            if (::tts.isInitialized) {
                tts.stop()
                tts.shutdown()
            }
        } catch (e: Exception) {
            Log.e("onDestroy", "Error al cerrar TTS", e)
        }

        // Cancelar cualquier handler activo
        handlerSeleccionDispositivo?.removeCallbacksAndMessages(null)
        handlerSeleccionDispositivo = null

        // Reiniciar variables
        opcionSeleccionada = false
        estadoMenu = ""
        edificioOrigen = ""
        porteriaOrigen = ""
        balizaDestinoEsperada = null
        balizaOrigen = null
        navegandoAHaciaDestino = false
        finalizandoNavegacion = false
        enunciandoAdvertencia = false
        mensajeEnProgreso = false
        dispositivosDetectados.clear()

        // Eliminar bandera de flujo iniciado
        getSharedPreferences(prefsName, MODE_PRIVATE).edit {
            remove(keyFlujoIniciado)
        }

        super.onDestroy()
    }

    override fun onRestart() {
        super.onRestart()

        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val flujoIniciado = prefs.getBoolean(keyFlujoIniciado, false)

        if (!flujoIniciado) {
            reiniciarFlujoLogico()
        }

        bluetoothAdapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter

        if (!bluetoothAdapter.isEnabled) {
            solicitarReinicioManualBluetooth()
            return
        }

        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            Toast.makeText(this, "Escáner BLE no disponible tras reactivación. Reinicia Bluetooth.", Toast.LENGTH_LONG).show()
            solicitarReinicioManualBluetooth()
            return
        }

        if (speechRecognizer == null) {
            inicializarSpeechRecognizer()
        }

        if (!::tts.isInitialized) {
            inicializarTextToSpeech()
        }
    }

    //Reiniciar flujo
    private fun reiniciarFlujoLogico() {
        opcionSeleccionada = false
        estadoMenu = ""
        edificioOrigen = ""
        porteriaOrigen = ""
        balizaDestinoEsperada = null
        balizaOrigen = null
        navegandoAHaciaDestino = false
        finalizandoNavegacion = false
        enunciandoAdvertencia = false
        preguntarPorEscaneo()
    }

    override fun onResume() {
        super.onResume()

        val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = manager.adapter

        if (!bluetoothAdapter.isEnabled) {
            solicitarReinicioManualBluetooth()
            return
        }

        // Verificar y limpiar resultados previos del escáner solo si API >= 31
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permisoScan = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
            if (permisoScan == PackageManager.PERMISSION_GRANTED) {
                try {
                    obtenerScannerBLE()?.flushPendingScanResults(scanCallback)
                } catch (e: SecurityException) {
                    Log.w("BLE", "flushPendingScanResults falló por falta de permisos", e)
                } catch (e: Exception) {
                    Log.w("BLE", "flushPendingScanResults falló inesperadamente", e)
                }
            } else {
                Log.w("BLE", "Permiso BLUETOOTH_SCAN no concedido. No se puede limpiar el escáner.")
            }
        }

        // Revalidar SpeechRecognizer
        if (speechRecognizer == null) {
            inicializarSpeechRecognizer()
        }

        // Revalidar TextToSpeech
        if (!::tts.isInitialized) {
            inicializarTextToSpeech()
        }
    }

    //Inicar instancias nuevamente
    private fun inicializarTextToSpeech() {
        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.ERROR) {
                tts.language = Locale.getDefault()
            }
        }
    }

    private fun inicializarSpeechRecognizer() {
        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
    }

    //Interfaz
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AppScreen() {
        Scaffold { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Sistema de orientación",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
