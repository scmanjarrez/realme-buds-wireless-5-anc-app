package com.example.realmebuds

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Conexión RFCOMM con los auriculares. Probar en orden: UUID SPP estándar, UUID de Qualcomm
 * (los dos que usa realme Link) y, como último recurso, el canal 1 directo (lo que funcionó
 * desde Linux). Se da por buena la primera que responde a la consulta de estado con el
 * protocolo AA.
 */
@SuppressLint("MissingPermission")
class BudsConnection(private val device: BluetoothDevice, private val log: (String) -> Unit) {

    private var socket: BluetoothSocket? = null
    private var reader: Thread? = null
    private val seq = AtomicInteger(0x40)
    private val pending = ConcurrentHashMap<Int, LinkedBlockingQueue<Frame>>()

    val isConnected: Boolean get() = socket?.isConnected == true

    /** Bloquea; llamar desde un hilo secundario. Devuelve la estrategia usada. */
    fun connect(): String {
        val strategies = listOf<Pair<String, () -> BluetoothSocket>>(
            "UUID SPP 00001101" to { device.createRfcommSocketToServiceRecord(SPP) },
            "UUID Qualcomm 079A" to { device.createRfcommSocketToServiceRecord(QUALCOMM) },
            "canal 1 directo" to {
                val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                m.invoke(device, 1) as BluetoothSocket
            },
        )
        for ((name, make) in strategies) {
            try {
                log("Probando $name...")
                val s = make()
                s.connect()
                socket = s
                startReader(s)
                val r = request(Cmd.QUERY_STATUS)
                if (r != null && r.isResponse) {
                    log("Conectado con $name")
                    return name
                }
                log("$name: conecta pero no responde al protocolo")
                close()
            } catch (e: Exception) {
                log("$name falló: ${e.message}")
                close()
            }
        }
        throw IOException("Ninguna estrategia funcionó")
    }

    private fun startReader(s: BluetoothSocket) {
        val t = Thread {
            val parser = Protocol.Parser()
            val buf = ByteArray(512)
            try {
                val input = s.inputStream
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    for (f in parser.feed(buf, n)) {
                        val q = pending[f.seq]
                        if (f.isResponse && q != null) q.offer(f)
                        else log("Aviso: $f")
                    }
                }
            } catch (ignored: IOException) {
            }
            log("Conexión cerrada")
        }
        t.isDaemon = true
        t.start()
        reader = t
    }

    /** Envía un comando y espera su respuesta (2 s). Devuelve null si no llega. */
    fun request(cmd: Int, data: ByteArray = ByteArray(0)): Frame? {
        val s = socket ?: return null
        val n = seq.incrementAndGet() and 0xff
        val q = LinkedBlockingQueue<Frame>()
        pending[n] = q
        try {
            synchronized(this) {
                s.outputStream.write(Protocol.build(cmd, n, data))
                s.outputStream.flush()
            }
            return q.poll(2, TimeUnit.SECONDS)
        } catch (e: IOException) {
            log("Error de envío: ${e.message}")
            return null
        } finally {
            pending.remove(n)
        }
    }

    fun close() {
        try {
            socket?.close()
        } catch (ignored: IOException) {
        }
        socket = null
    }

    companion object {
        val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        val QUALCOMM: UUID = UUID.fromString("0000079A-D102-11E1-9B23-00025B00A5A5")
    }
}
