package com.example.realmebuds

/** Estado leído de los auriculares. Los campos son null si no se pudo leer. */
class BudsState(
    val battery: Int?,
    val firmware: String?,
    val noise: Int?,            // 01 normal, 02 transparencia, 04/08/10/20 niveles de ANC
    val noiseCycle: Int?,       // máscara del ciclo del botón de cancelación
    val eq: Int?,               // 00..03 presets, 04 personalizado
    val volumeEnhancer: Boolean?,
    val features: Map<Int, Boolean>,
    val keys: Map<Int, Int>,    // (botón shl 8 or acción) -> función
    val devices: List<PairedDevice>,
    val dynamic: Map<Int, Int>,  // tipo (1 low, 2 med, 3 high) -> valor actual con signo
    val customEq: IntArray?,     // ganancias del EQ personalizado, si existe
    val customEqFreqs: IntArray?,
)

/** Equipo de la lista de Dual-device. [mac] va en el orden de bytes del protocolo. */
class PairedDevice(val mac: ByteArray, val name: String, val state: Int, val isThisDevice: Boolean) {
    /** Dirección en el formato habitual (el protocolo la envía invertida). */
    val macText: String get() = mac.reversed().joinToString(":") { "%02X".format(it) }
}

object BudsApi {
    /** Ids de 0x0403 que sabemos leer con 0x010d. */
    val FEATURE_IDS = intArrayOf(
        Feature.ENHANCE_VOICES, Feature.SMART_DEWIND, Feature.SPATIAL_AUDIO, Feature.DYNAMIC_AUDIO,
        Feature.GAME_MODE, Feature.AUTO_ANSWER, Feature.DUAL_CONNECTION, Feature.FIND_MY_PHONE,
    )

    /** Misma lista que envía realme Link al pedir las funciones de los botones. */
    private val KEYS_REQUEST = byteArrayOf(
        0x0e, 0x01, 0x03, 0x04, 0x05, 0x06, 0x0b, 0x0c, 0x08, 0x0d, 0x11, 0x1f, 0x1a, 0x1b, 0x00,
    )

    fun keyId(button: Int, action: Int): Int = (button shl 8) or action

    private fun ok(f: Frame?): ByteArray? = if (f != null && f.isOk) f.data else null

    private fun last(d: ByteArray?): Int? = d?.lastOrNull()?.toInt()?.and(0xff)

    fun readState(c: BudsConnection): BudsState {
        val battery = last(ok(c.request(Cmd.QUERY_STATUS)))

        val ver = ok(c.request(Cmd.QUERY_VERSION))
        val firmware = ver?.let { if (it.size > 1) String(it, 1, it.size - 1, Charsets.US_ASCII) else null }

        val noise = last(ok(c.request(Cmd.QUERY_NOISE, byteArrayOf(1, 1))))
        val cycle = last(ok(c.request(Cmd.QUERY_NOISE, byteArrayOf(2, 1))))

        val eq = ok(c.request(Cmd.QUERY_EQ))?.let { if (it.size > 1) it[1].toInt() and 0xff else null }
        val vol = ok(c.request(Cmd.QUERY_VOLUME))?.let { if (it.size > 1) it[1].toInt() != 0 else null }

        val features = HashMap<Int, Boolean>()
        val req = ByteArray(1 + FEATURE_IDS.size)
        req[0] = FEATURE_IDS.size.toByte()
        FEATURE_IDS.forEachIndexed { i, id -> req[1 + i] = id.toByte() }
        ok(c.request(Cmd.QUERY_FEATURES, req))?.let { d ->
            if (d.size > 1) {
                val n = d[1].toInt() and 0xff
                for (i in 0 until n) {
                    val p = 2 + i * 2
                    if (p + 1 < d.size) features[d[p].toInt() and 0xff] = d[p + 1].toInt() != 0
                }
            }
        }

        val keys = HashMap<Int, Int>()
        ok(c.request(Cmd.QUERY_KEYS, KEYS_REQUEST))?.let { d ->
            if (d.size > 1) {
                val n = d[1].toInt() and 0xff
                for (i in 0 until n) {
                    val p = 2 + i * 4
                    if (p + 3 < d.size) {
                        val button = d[p + 1].toInt() and 0xff
                        val action = d[p + 2].toInt() and 0xff
                        keys[keyId(button, action)] = d[p + 3].toInt() and 0xff
                    }
                }
            }
        }

        val devices = ArrayList<PairedDevice>()
        ok(c.request(Cmd.QUERY_PAIRED))?.let { d ->
            if (d.size > 1) {
                val n = d[1].toInt() and 0xff
                var p = 2
                for (i in 0 until n) {
                    if (p + 10 > d.size) break
                    val total = d[p + 6].toInt() and 0xff      // estado + marca + longitud del nombre + nombre
                    val state = d[p + 7].toInt() and 0xff
                    val flag = d[p + 8].toInt() and 0xff
                    val nameLen = d[p + 9].toInt() and 0xff
                    if (p + 10 + nameLen > d.size) break
                    devices.add(
                        PairedDevice(
                            d.copyOfRange(p, p + 6),
                            String(d, p + 10, nameLen, Charsets.UTF_8),
                            state,
                            flag == 1,
                        )
                    )
                    p += 7 + total
                }
            }
        }

        // 0x012c: n, [mín, máx, actual, tipo] * n
        val dynamic = HashMap<Int, Int>()
        ok(c.request(Cmd.QUERY_DYNAMIC))?.let { d ->
            if (d.size > 1) {
                val n = d[1].toInt() and 0xff
                for (i in 0 until n) {
                    val p = 2 + i * 4
                    if (p + 3 < d.size) dynamic[d[p + 3].toInt() and 0xff] = d[p + 2].toInt()
                }
            }
        }

        // 0x0122: n, [seleccionado, mín, máx, id, len, nombre, nBandas, (freq u16, ganancia i8) * nBandas] * n
        var customEq: IntArray? = null
        var customFreqs: IntArray? = null
        ok(c.request(Cmd.QUERY_ALL_EQ))?.let { d ->
            if (d.size > 1) {
                val n = d[1].toInt() and 0xff
                var p = 2
                for (i in 0 until n) {
                    if (p + 5 > d.size) break
                    val eqId = d[p + 3].toInt() and 0xff
                    val nameLen = d[p + 4].toInt() and 0xff
                    val bandsAt = p + 5 + nameLen
                    if (bandsAt >= d.size) break
                    val nb = d[bandsAt].toInt() and 0xff
                    val end = bandsAt + 1 + nb * 3
                    if (end > d.size) break
                    if (eqId == 4) {
                        val g = IntArray(nb)
                        val f = IntArray(nb)
                        for (k in 0 until nb) {
                            val q = bandsAt + 1 + k * 3
                            f[k] = (d[q].toInt() and 0xff) or ((d[q + 1].toInt() and 0xff) shl 8)
                            g[k] = d[q + 2].toInt()
                        }
                        customEq = g
                        customFreqs = f
                    }
                    p = end
                }
            }
        }

        return BudsState(
            battery, firmware, noise, cycle, eq, vol, features, keys, devices, dynamic, customEq, customFreqs,
        )
    }

    /** Acción 1 = conectar y 2 = eliminar (confirmadas); 0 = desconectar (sin probar). */
    fun multiConnectData(mac: ByteArray, action: Int): ByteArray {
        val out = ByteArray(8)
        out[0] = 1
        mac.copyInto(out, 1)
        out[7] = action.toByte()
        return out
    }
}
