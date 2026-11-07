package com.example.realmebuds

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import java.util.concurrent.Executors

@SuppressLint("MissingPermission", "UseSwitchCompatOrMaterialCode")
class MainActivity : Activity() {

    private val io = Executors.newSingleThreadExecutor()
    private var conn: BudsConnection? = null

    private lateinit var statusView: TextView
    private lateinit var logView: TextView
    private lateinit var controls: LinearLayout

    // widgets que reflejan el estado de los auriculares
    private lateinit var noiseLabel: TextView
    private lateinit var levelDropdown: Dropdown
    private lateinit var eqLabel: TextView
    private lateinit var eqDropdown: Dropdown
    private lateinit var volumeToggle: Toggle
    private val featureToggles = HashMap<Int, Toggle>()
    private val keyDropdowns = HashMap<Int, Dropdown>()
    private val cycleChecks = HashMap<Int, CheckBox>()
    private var updatingCycle = false
    private lateinit var devicesBox: LinearLayout
    private val dynBars = HashMap<Int, SeekBar>()
    private val eqGains = IntArray(Protocol.EQ_FREQS.size)
    private val eqBars = ArrayList<SeekBar>()
    private var eqFreqs: IntArray = Protocol.EQ_FREQS.copyOf()

    @Volatile
    private var customExists = false
    private var slidersActive = 0
    private var lastSliderStop = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val eqRunnable = Runnable { applyCustomEq() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        if (hasBtPermission()) start() else requestBtPermission()
    }

    override fun onDestroy() {
        super.onDestroy()
        conn?.close()
        io.shutdownNow()
    }

    // ---- permisos ----

    private fun hasBtPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun requestBtPermission() {
        requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED) start()
        else log("Hace falta el permiso de dispositivos Bluetooth cercanos.")
    }

    // ---- conexión ----

    private fun start() {
        val adapter: BluetoothAdapter? =
            (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            log("Bluetooth apagado o no disponible.")
            return
        }
        val buds = adapter.bondedDevices.filter { it.name?.contains("buds", ignoreCase = true) == true }
        when (buds.size) {
            0 -> log("No hay auriculares realme Buds emparejados.")
            1 -> connectTo(buds[0])
            else -> {
                log("Varios dispositivos Buds; elige uno:")
                buds.forEach { d ->
                    controls.addView(button("${d.name} (${d.address})") { connectTo(d) })
                }
            }
        }
    }

    private fun connectTo(device: BluetoothDevice) {
        controls.removeAllViews()
        statusView.text = "Conectando a ${device.name}..."
        val c = BudsConnection(device) { log(it) }
        conn = c
        io.execute {
            try {
                c.connect()
                runOnUiThread {
                    buildControls()
                    refresh()
                }
            } catch (e: Exception) {
                runOnUiThread { statusView.text = "No se pudo conectar: ${e.message}" }
            }
        }
    }

    // ---- lectura y envío ----

    private fun refresh() {
        val c = conn ?: return
        io.execute { readAndShow(c) }
    }

    private fun readAndShow(c: BudsConnection) {
        val st = BudsApi.readState(c)
        runOnUiThread { showState(st) }
    }

    private fun sendData(cmd: Int, data: ByteArray, refreshAfter: Boolean = true) {
        val c = conn ?: return
        io.execute {
            val r = c.request(cmd, data)
            log(
                when {
                    r == null -> "0x%04x %s -> sin respuesta".format(cmd, data.toHex())
                    r.isOk -> "0x%04x %s -> OK".format(cmd, data.toHex())
                    else -> "0x%04x %s -> error (%s)".format(cmd, data.toHex(), r.data.toHex())
                }
            )
            if (refreshAfter) readAndShow(c)
        }
    }

    private fun send(cmd: Int, vararg bytes: Int) =
        sendData(cmd, ByteArray(bytes.size) { bytes[it].toByte() })

    private fun noiseName(v: Int?): String = when (v) {
        Noise.NORMAL -> "Off (normal)"
        Noise.TRANSPARENT -> "Transparencia"
        Noise.MILD -> "ANC Mild"
        Noise.MAX -> "ANC Max"
        Noise.MODERATE -> "ANC Moderate"
        Noise.SMART -> "ANC Smart"
        null -> "?"
        else -> "desconocido (0x%02x)".format(v)
    }

    private fun showState(st: BudsState) {
        statusView.text = "Batería: ${st.battery?.let { "$it %" } ?: "?"}   Firmware: ${st.firmware ?: "?"}"
        noiseLabel.text = "Actual: ${noiseName(st.noise)}"
        levelDropdown.show(st.noise)
        for ((id, t) in featureToggles) t.show(st.features[id])
        volumeToggle.show(st.volumeEnhancer)
        showEqMode(st.eq)
        for ((k, d) in keyDropdowns) d.show(st.keys[k])
        st.noiseCycle?.let { mask ->
            updatingCycle = true
            for ((bit, box) in cycleChecks) box.isChecked = (mask and bit) != 0
            updatingCycle = false
        }
        customExists = st.customEq != null
        // No se tocan los deslizadores mientras el usuario los mueve o acaba de soltarlos.
        if (!slidersBusy()) {
            for ((type, v) in st.dynamic) dynBars[type]?.progress = v + 5
            st.customEq?.let { g ->
                val f = st.customEqFreqs
                if (f != null && f.size == eqFreqs.size) eqFreqs = f.copyOf()
                for (i in eqBars.indices) {
                    if (i < g.size) {
                        eqGains[i] = g[i]
                        eqBars[i].progress = g[i] + 6
                    }
                }
            }
        }
        showDevices(st.devices)
    }

    private fun showDevices(list: List<PairedDevice>) {
        devicesBox.removeAllViews()
        for (d in list) {
            val state = when {
                d.isThisDevice -> "This device"
                d.state == 2 -> "Connected"
                d.state == 1 -> "Connecting"
                else -> "Not connected"
            }
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val tv = TextView(this)
            tv.text = "${d.name}\n$state"
            line.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (!d.isThisDevice) {
                line.addView(button("Acciones") { deviceActions(d) })
            }
            devicesBox.addView(line)
        }
    }

    private fun deviceActions(d: PairedDevice) {
        val options = arrayOf("Conectar", "Desconectar", "Eliminar")
        AlertDialog.Builder(this)
            .setTitle("${d.name} (${d.macText})")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> multiConnect(d, 1)
                    1 -> confirm("¿Desconectar ${d.name}?") { multiConnect(d, 0) }
                    else -> confirm("Si lo eliminas, tendrás que volver a emparejarlo para conectarlo.") {
                        multiConnect(d, 2)
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun multiConnect(d: PairedDevice, action: Int) {
        log("Dual-device: ${d.name} acción $action")
        sendData(Cmd.SET_MULTI_CONNECT, BudsApi.multiConnectData(d.mac, action))
    }

    private fun confirm(message: String, onOk: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> onOk() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private val eqNames = mapOf(
        Eq.CLEAR_VOCALS to "Clear Vocals",
        Eq.NATURE_BALANCE to "Nature Balance",
        Eq.CLEAR_BASS to "Clear Bass",
        Eq.BASS_BOOST to "Bass Boost",
    )

    // ---- interfaz (sin XML) ----

    private fun buildUi() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        scroll.addView(root)
        setContentView(scroll)

        statusView = TextView(this).apply { textSize = 16f; text = "Sin conectar" }
        root.addView(statusView)
        controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(controls)
        root.addView(title("Registro"))
        logView = TextView(this).apply { textSize = 12f; typeface = android.graphics.Typeface.MONOSPACE }
        root.addView(logView)
    }

    private fun buildControls() {
        controls.removeAllViews()
        featureToggles.clear()
        keyDropdowns.clear()
        cycleChecks.clear()
        dynBars.clear()
        controls.addView(button("Actualizar estado") { refresh() })

        // --- control de ruido ---
        controls.addView(title("Control de ruido"))
        noiseLabel = TextView(this)
        controls.addView(noiseLabel)
        levelDropdown = Dropdown(
            listOf("Smart", "Max", "Moderate", "Mild"),
            listOf(Noise.SMART, Noise.MAX, Noise.MODERATE, Noise.MILD),
        ) { send(Cmd.SET_NOISE, 1, 1, it) }
        controls.addView(row(
            "Noise cancellation" to { send(Cmd.SET_NOISE, 1, 1, levelDropdown.current()) },
            "Off" to { send(Cmd.SET_NOISE, 1, 1, Noise.NORMAL) },
            "Transparency" to { send(Cmd.SET_NOISE, 1, 1, Noise.TRANSPARENT) },
        ))
        controls.addView(label("Nivel de cancelación"))
        controls.addView(levelDropdown.view)
        addToggle("Enhance voices", Feature.ENHANCE_VOICES)
        addToggle("Smart de-wind technology", Feature.SMART_DEWIND)

        // --- efectos de sonido ---
        controls.addView(title("Efectos de sonido"))
        eqLabel = TextView(this)
        controls.addView(eqLabel)
        eqDropdown = Dropdown(
            listOf("Elegir modo...", "Clear Vocals", "Nature Balance", "Clear Bass", "Bass Boost"),
            listOf(-1, Eq.CLEAR_VOCALS, Eq.NATURE_BALANCE, Eq.CLEAR_BASS, Eq.BASS_BOOST),
        ) { send(Cmd.SET_EQ, it) }
        controls.addView(eqDropdown.view)
        addToggle("Spatial audio", Feature.SPATIAL_AUDIO)
        addToggle("Dynamic audio", Feature.DYNAMIC_AUDIO)
        listOf("Low freq" to 1, "Med freq" to 2, "High freq" to 3).forEach { (name, id) ->
            dynBars[id] = slider(name, -5, 5, 0) { v ->
                sendData(Cmd.SET_DYNAMIC_SLIDER, byteArrayOf(0xfb.toByte(), 0x05, v.toByte(), id.toByte()), false)
            }
        }
        volumeToggle = Toggle("Volume enhancer") {
            send(Cmd.SET_HIGH_VOLUME, if (it) 1 else 0)
        }
        controls.addView(volumeToggle.view)

        // --- ecualizador personalizado ---
        controls.addView(title("Ecualizador personalizado"))
        controls.addView(label("Al mover un deslizador se activa el modo Personalizado."))
        eqGains.fill(0)
        eqBars.clear()
        Protocol.EQ_FREQS.forEachIndexed { i, hz ->
            val bar = slider(if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz", -6, 6, 0) { v ->
                eqGains[i] = v
                scheduleCustomEq()
            }
            eqBars.add(bar)
        }
        controls.addView(button("Restablecer (deslizadores a 0 y Clear Bass)") { resetEq() })

        // --- otros ---
        controls.addView(title("Otros"))
        addToggle("Game mode", Feature.GAME_MODE)
        controls.addView(button("Earbud fit test") { send(Cmd.FIT_TEST, 1) })
        addToggle("Dual-device connection", Feature.DUAL_CONNECTION)
        devicesBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        controls.addView(devicesBox)
        addToggle("Find my phone", Feature.FIND_MY_PHONE)
        addToggle("Auto answer", Feature.AUTO_ANSWER)

        // --- botones ---
        controls.addView(title("Botón multifunción"))
        val fnLabels = listOf("None", "Play/Pause", "Next track", "Previous track", "Voice assistant", "Game mode")
        val fnValues = listOf(KeyFn.NONE, KeyFn.PLAY_PAUSE, KeyFn.NEXT, KeyFn.PREVIOUS, KeyFn.VOICE_ASSISTANT, KeyFn.GAME_MODE)
        listOf("Single tap", "Double tap", "Triple tap", "Touch and hold").forEachIndexed { i, name ->
            val action = i + 1
            val d = Dropdown(fnLabels, fnValues) { fn ->
                sendData(Cmd.SET_KEYS, byteArrayOf(1, 1, 1, action.toByte(), fn.toByte()))
            }
            keyDropdowns[BudsApi.keyId(1, action)] = d
            controls.addView(label(name))
            controls.addView(d.view)
        }

        controls.addView(title("Botón de cancelación de ruido (un toque: cambiar modo)"))
        listOf(
            "Noise cancellation" to CycleBit.NOISE_CANCELLATION,
            "Off" to CycleBit.OFF,
            "Transparency" to CycleBit.TRANSPARENCY,
        ).forEach { (name, bit) ->
            val box = CheckBox(this).apply { text = name }
            box.setOnCheckedChangeListener { _, _ ->
                if (!updatingCycle) applyCycle(box)
            }
            cycleChecks[bit] = box
            controls.addView(box)
        }
    }

    /**
     * Envía el ecualizador personalizado. Si los auriculares aún no lo tienen, primero se crea
     * (operación 1, con ceros, como hace realme Link) y después se actualiza (operación 2);
     * al actualizarlo, los auriculares pasan solos al modo Personalizado.
     */
    private fun applyCustomEq() {
        val c = conn ?: return
        val gains = eqGains.copyOf()
        val freqs = eqFreqs.copyOf()
        val create = !customExists
        io.execute {
            if (create) {
                val r = c.request(Cmd.SET_CUSTOM_EQ, Protocol.customEqData(1, 0, IntArray(gains.size), freqs))
                log("EQ crear -> " + if (r?.isOk == true) "OK" else "fallo")
                customExists = true
            }
            val r = c.request(Cmd.SET_CUSTOM_EQ, Protocol.customEqData(2, 4, gains, freqs))
            log("EQ actualizar " + gains.joinToString(",") + " -> " + if (r?.isOk == true) "OK" else "fallo")
            refreshEqMode(c)
        }
    }

    /** Agrupa los cambios rápidos de los deslizadores en un solo envío. */
    private fun scheduleCustomEq() {
        handler.removeCallbacks(eqRunnable)
        handler.postDelayed(eqRunnable, 300)
    }

    /** Deja el ecualizador personalizado a 0 y vuelve al perfil por defecto (Clear Bass). */
    private fun resetEq() {
        val c = conn ?: return
        handler.removeCallbacks(eqRunnable)
        eqGains.fill(0)
        eqBars.forEach { it.progress = 6 }
        val freqs = eqFreqs.copyOf()
        val exists = customExists
        io.execute {
            if (exists) {
                val z = c.request(Cmd.SET_CUSTOM_EQ, Protocol.customEqData(2, 4, IntArray(freqs.size), freqs))
                log("EQ a cero -> " + if (z?.isOk == true) "OK" else "fallo")
            }
            val r = c.request(Cmd.SET_EQ, byteArrayOf(Eq.CLEAR_BASS.toByte()))
            log("EQ Clear Bass -> " + if (r?.isOk == true) "OK" else "fallo")
            lastSliderStop = 0L
            readAndShow(c)
        }
    }

    /** Solo el modo de EQ: es una lectura corta, para no saturar a los auriculares tras cada deslizador. */
    private fun refreshEqMode(c: BudsConnection) {
        val r = c.request(Cmd.QUERY_EQ)
        val id = if (r != null && r.isOk && r.data.size > 1) r.data[1].toInt() and 0xff else null
        runOnUiThread { showEqMode(id) }
    }

    private fun showEqMode(eqId: Int?) {
        eqLabel.text = "Actual: " + when {
            eqId == null -> "?"
            eqId == 4 -> "Personalizado"
            else -> eqNames[eqId] ?: "desconocido ($eqId)"
        }
        eqDropdown.show(eqId)
    }

    /** True si el usuario está tocando un deslizador o lo soltó hace poco. */
    private fun slidersBusy(): Boolean =
        slidersActive > 0 || SystemClock.uptimeMillis() - lastSliderStop < 1500

    private fun applyCycle(changed: CheckBox) {
        var mask = 0
        for ((bit, box) in cycleChecks) if (box.isChecked) mask = mask or bit
        if (Integer.bitCount(mask) < 2) {
            log("El ciclo necesita al menos dos modos.")
            updatingCycle = true
            changed.isChecked = true
            updatingCycle = false
            return
        }
        val c = conn ?: return
        io.execute {
            val a = c.request(Cmd.SET_NOISE, byteArrayOf(2, 1, mask.toByte()))
            val b = c.request(Cmd.SET_KEYS, byteArrayOf(1, 1, 4, 1, KeyFn.NOISE_CONTROL.toByte()))
            log("Ciclo 0x%02x -> %s / %s".format(mask, if (a?.isOk == true) "OK" else "fallo", if (b?.isOk == true) "OK" else "fallo"))
            readAndShow(c)
        }
    }

    private fun addToggle(name: String, id: Int) {
        val t = Toggle(name) { on -> send(Cmd.SET_FEATURE, id, if (on) 1 else 0) }
        featureToggles[id] = t
        controls.addView(t.view)
    }

    private fun slider(name: String, lo: Int, hi: Int, start: Int, onDone: (Int) -> Unit): SeekBar {
        val tv = TextView(this)
        tv.text = "$name: $start"
        val sb = SeekBar(this)
        sb.max = hi - lo
        sb.progress = start - lo
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                tv.text = "$name: ${p + lo}"
            }

            override fun onStartTrackingTouch(s: SeekBar?) {
                slidersActive++
            }

            override fun onStopTrackingTouch(s: SeekBar?) {
                slidersActive = maxOf(0, slidersActive - 1)
                lastSliderStop = SystemClock.uptimeMillis()
                onDone((s?.progress ?: 0) + lo)
            }
        })
        controls.addView(tv)
        controls.addView(sb)
        return sb
    }

    /** Lista desplegable que no dispara su acción al fijarla desde el estado leído. */
    private inner class Dropdown(labels: List<String>, val values: List<Int>, val onPick: (Int) -> Unit) {
        val view = Spinner(this@MainActivity)
        private var shown = 0

        init {
            view.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, labels)
            view.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    if (pos == shown) return
                    shown = pos
                    if (values[pos] >= 0) onPick(values[pos])
                }

                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }

        fun current(): Int = values[view.selectedItemPosition].let { if (it < 0) values.first { v -> v >= 0 } else it }

        fun show(value: Int?) {
            if (value == null) return
            val i = values.indexOf(value)
            if (i >= 0) {
                shown = i
                view.setSelection(i)
            }
        }
    }

    /** Interruptor que no dispara su acción al fijarlo desde el estado leído. */
    private inner class Toggle(name: String, val onChange: (Boolean) -> Unit) {
        val view = Switch(this@MainActivity)
        private var updating = false

        init {
            view.text = name
            view.setOnCheckedChangeListener { _, checked -> if (!updating) onChange(checked) }
        }

        fun show(on: Boolean?) {
            if (on == null) return
            updating = true
            view.isChecked = on
            updating = false
        }
    }

    private fun row(vararg items: Pair<String, () -> Unit>): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            items.forEach { (text, action) ->
                addView(
                    button(text, action),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                )
            }
        }

    private fun button(text: String, action: () -> Unit) =
        Button(this).apply {
            this.text = text
            isAllCaps = false
            setOnClickListener { action() }
        }

    private fun title(text: String) = TextView(this).apply {
        this.text = text
        textSize = 18f
        setPadding(0, dp(20), 0, dp(4))
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        setPadding(0, dp(8), 0, 0)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun log(msg: String) {
        runOnUiThread {
            logView.text = "$msg\n${logView.text}".take(4000)
        }
    }
}
