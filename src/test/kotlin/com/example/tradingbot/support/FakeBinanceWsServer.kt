package com.example.tradingbot.support

import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

class FakeBinanceWsServer {

    private lateinit var serverSocket: ServerSocket
    private lateinit var socket: Socket

    private val running = AtomicBoolean(true)

    @Volatile
    private var connected = false

    val port: Int get() = serverSocket.localPort

    fun start() {
        serverSocket = ServerSocket(0)
        Thread({ acceptLoop() }, "fake-binance-ws-acceptor").start()
    }

    fun awaitConnection(timeoutMs: Long = 10_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (connected) return
            Thread.sleep(25)
        }
        throw AssertionError("WebSocket client never connected to fake server")
    }

    @Synchronized
    fun send(payload: String) {
        socket.getOutputStream().let { out ->
            out.write(textFrame(payload))
            out.flush()
        }
    }

    fun close() {
        running.set(false)
        runCatching { socket.close() }
        runCatching { serverSocket.close() }
    }

    private fun acceptLoop() {
        socket = serverSocket.accept()
        socket.soTimeout = 30_000
        val handshake = readHandshake(socket.getInputStream())
        val key = handshake.headers["sec-websocket-key"]
            ?: error("Handshake has no Sec-WebSocket-Key")
        val out = socket.getOutputStream()
        out.write(
            (
                "HTTP/1.1 101 Switching Protocols\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: ${acceptKey(key)}\r\n" +
                    "\r\n"
                ).toByteArray(StandardCharsets.UTF_8)
        )
        out.flush()
        connected = true
        Thread({ drain(socket.getInputStream()) }, "fake-binance-ws-drain").start()
    }

    private fun drain(input: java.io.InputStream) {
        val buffer = ByteArray(4096)
        try {
            while (running.get() && input.read(buffer) != -1) {
                // discard client frames (pings/pongs/close); server messages are fire-and-forget
            }
        } catch (e: Exception) {
            // socket closed or read timeout: expected during teardown
        }
    }

    private fun readHandshake(input: java.io.InputStream): Handshake {
        val raw = ByteArrayOutputStream()
        val buffer = ByteArray(1)
        while (raw.size() < 32 * 1024) {
            val read = input.read(buffer)
            if (read == -1) throw IllegalStateException("Connection closed during handshake")
            raw.write(buffer)
            val bytes = raw.toByteArray()
            if (String(bytes, StandardCharsets.UTF_8).contains("\r\n\r\n")) break
        }
        val request = String(raw.toByteArray(), StandardCharsets.UTF_8)
        val lines = request.split("\r\n")
        val headers = mutableMapOf<String, String>()
        lines.drop(1).filter { it.contains(':') }.forEach { line ->
            val idx = line.indexOf(':')
            headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
        }
        if (headers["sec-websocket-key"] == null) {
            throw IllegalStateException("Handshake has no Sec-WebSocket-Key. Raw request: [${request.take(400)}]")
        }
        return Handshake(requestLine = lines.first(), headers = headers)
    }

    private fun acceptKey(secWebSocketKey: String): String {
        val sha1 = MessageDigest.getInstance("SHA-1")
            .digest((secWebSocketKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(sha1)
    }

    private fun textFrame(payload: String): ByteArray {
        val data = payload.toByteArray(StandardCharsets.UTF_8)
        val header = ByteArrayOutputStream()
        header.write(0x81)
        when {
            data.size < 126 -> header.write(data.size)
            data.size <= 0xFFFF -> {
                header.write(126)
                header.write((data.size shr 8) and 0xFF)
                header.write(data.size and 0xFF)
            }
            else -> {
                header.write(127)
                val len = data.size.toLong()
                for (i in 7 downTo 0) {
                    header.write(((len shr (8 * i)) and 0xFF).toInt())
                }
            }
        }
        return header.toByteArray() + data
    }

    private data class Handshake(val requestLine: String, val headers: Map<String, String>)
}
