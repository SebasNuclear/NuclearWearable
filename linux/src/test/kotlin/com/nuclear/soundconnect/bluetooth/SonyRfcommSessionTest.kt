package com.nuclear.soundconnect.bluetooth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class SonyRfcommSessionTest {

    private class PipeRfcommSocket(
        override val inputStream: PipedInputStream,
        override val outputStream: PipedOutputStream
    ) : RfcommSocketConnection {
        @Volatile
        private var open = true
        override val isConnected: Boolean get() = open
        override fun close() {
            open = false
            runCatching { inputStream.close() }
            runCatching { outputStream.close() }
        }
    }

    @Test
    fun `persistent SessionTransport performs V2 handshake, dual ARQ ACKs, and 7-byte SET_NC_ASM`() = runBlocking {
        val deviceToClientIn = PipedInputStream(4096)
        val headsetTx = PipedOutputStream(deviceToClientIn)

        val clientToHeadsetIn = PipedInputStream(4096)
        val clientTx = PipedOutputStream(clientToHeadsetIn)

        val capturedTxPayloads = CopyOnWriteArrayList<ByteArray>()
        val capturedAckSeqs = CopyOnWriteArrayList<Pair<Byte, Byte>>()

        // Fake Sony WF-1000XM6 RFCOMM firmware Responder thread
        val responder = Thread({
            val buf = ByteArray(1024)
            var remainder = ByteArray(0)
            var rxSeqForDevice = 0
            try {
                while (true) {
                    val read = clientToHeadsetIn.read(buf)
                    if (read <= 0) break
                    val (frames, nextRemainder) = SonyProtocolParser.extractFrames(remainder + buf.copyOf(read))
                    remainder = nextRemainder
                    for (parsed in frames) {
                        if (parsed.dataType == SonyProtocolParser.DATA_TYPE_ACK) {
                            capturedAckSeqs += parsed.dataType to parsed.sequence
                            continue
                        }
                        if (parsed.payload.isEmpty()) continue
                        capturedTxPayloads += parsed.payload
                        // Send immediate ACK back to client
                        headsetTx.write(SonyProtocolParser.buildAckPacket(SonyProtocolParser.nextAckSequenceForIncoming(parsed.sequence)))
                        headsetTx.flush()

                        val op = parsed.payload[0]
                        val sub = if (parsed.payload.size > 1) parsed.payload[1] else 0.toByte()
                        val devSeq = (rxSeqForDevice % 2).toByte().also { rxSeqForDevice++ }
                        val replyDataType = parsed.dataType
                        val replyPayload: ByteArray? = when {
                            op == SonyProtocolParser.CMD_CONNECT_GET_PROTOCOL_INFO ->
                                byteArrayOf(0x01, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00) // V2 (>= 8 bytes)
                            op == SonyProtocolParser.CMD_CONNECT_GET_DEVICE_INFO && sub == SonyProtocolParser.DEVICE_INFO_MODEL_NAME ->
                                byteArrayOf(SonyProtocolParser.CMD_CONNECT_RET_DEVICE_INFO, SonyProtocolParser.DEVICE_INFO_MODEL_NAME, 0x0A) +
                                    "WF-1000XM6".toByteArray(Charsets.US_ASCII)
                            op == SonyProtocolParser.CMD_CONNECT_GET_DEVICE_INFO && sub == SonyProtocolParser.DEVICE_INFO_FW_VERSION ->
                                byteArrayOf(SonyProtocolParser.CMD_CONNECT_RET_DEVICE_INFO, SonyProtocolParser.DEVICE_INFO_FW_VERSION, 0x05) +
                                    "1.0.2".toByteArray(Charsets.US_ASCII)
                            op == SonyProtocolParser.CMD_CONNECT_GET_SUPPORT_FUNCTION && replyDataType == SonyProtocolParser.DATA_TYPE_MDR ->
                                byteArrayOf(SonyProtocolParser.CMD_CONNECT_RET_SUPPORT_FUNCTION, 0x00, 0x01, 0x63, 0x00)
                            op == SonyProtocolParser.CMD_CONNECT_GET_SUPPORT_FUNCTION && replyDataType == SonyProtocolParser.DATA_TYPE_MDR_NO2 ->
                                byteArrayOf(SonyProtocolParser.CMD_CONNECT_RET_SUPPORT_FUNCTION, 0x00, 0x01, 0x00, 0x00)
                            op == SonyProtocolParser.CMD_GET_BATTERY && sub == SonyProtocolParser.BATTERY_TYPE_DUAL_V2 ->
                                byteArrayOf(SonyProtocolParser.CMD_RET_BATTERY, SonyProtocolParser.BATTERY_TYPE_DUAL_V2, 85, 0x00, 90, 0x00)
                            op == SonyProtocolParser.CMD_GET_BATTERY && sub == SonyProtocolParser.BATTERY_TYPE_CASE_V2 ->
                                byteArrayOf(SonyProtocolParser.CMD_RET_BATTERY, SonyProtocolParser.BATTERY_TYPE_CASE_V2, 60, 0x00)
                            op == SonyProtocolParser.CMD_GET_NC_ASM ->
                                byteArrayOf(SonyProtocolParser.CMD_RET_NC_ASM, sub, 0x01, 0x01, 0x00, 0x00, 20)
                            op == SonyProtocolParser.CMD_COMMON_GET_STATUS_V2 && sub == SonyProtocolParser.COMMON_TYPE_AUDIO_CODEC ->
                                byteArrayOf(SonyProtocolParser.CMD_COMMON_RET_STATUS_V2, SonyProtocolParser.COMMON_TYPE_AUDIO_CODEC, 0x10)
                            op == SonyProtocolParser.CMD_SET_NC_ASM -> {
                                val nextMode = if (parsed.payload.size >= 7 && parsed.payload[4].toInt() == 1) 1 else 0
                                byteArrayOf(SonyProtocolParser.CMD_NTFY_NC_ASM, 0x17, 0x01, parsed.payload[3], nextMode.toByte(), 0x00, parsed.payload[6])
                            }
                            else -> null
                        }
                        if (replyPayload != null) {
                            headsetTx.write(SonyProtocolParser.buildPacket(replyDataType, devSeq, replyPayload))
                            headsetTx.flush()
                        }
                    }
                }
            } catch (_: Exception) {}
        }, "MockSonyFirmware").apply {
            isDaemon = true
            start()
        }

        val dev = SonyBluetoothDevice("A4:1D:10:AB:CD:EF", "WF-1000XM6", listOf(SonyRfcommClient.UUID_SONY_V2), true)
        val socket = PipeRfcommSocket(deviceToClientIn, clientTx)

        val connector = object : RfcommConnector {
            override fun isAdapterPowered() = true
            override fun findPairedDevices() = listOf(dev)
            override fun connect(device: SonyBluetoothDevice, uuid: UUID): RfcommSocketConnection? =
                if (uuid == SonyRfcommClient.UUID_SONY_V2) socket else null
        }

        val client = SonyRfcommClient(connector)
        val initial = HeadsetState(deviceName = "WF-1000XM6", deviceAddress = dev.address, isConnected = true)
        val sessionRes = client.ensurePersistentSession(dev, initial)
        assertTrue("Expected handshake to succeed", sessionRes.isSuccess)
        val stateAfterHandshake = sessionRes.getOrThrow()
        assertEquals(2, stateAfterHandshake.protocolVersion)
        assertEquals("WF-1000XM6", stateAfterHandshake.hardwareModelName)
        assertEquals("1.0.2", stateAfterHandshake.firmwareVersion)
        assertEquals("LDAC", stateAfterHandshake.codecName)
        assertEquals(85, stateAfterHandshake.leftBattery.level)
        assertEquals(90, stateAfterHandshake.rightBattery.level)
        assertEquals(60, stateAfterHandshake.caseBattery.level)

        val setRes = client.setNoiseMode(dev, NoiseControlMode.AMBIENT, 15, false, stateAfterHandshake)
        assertTrue(setRes.isSuccess)
        assertTrue("Expected dual ARQ ACKs back from client", capturedAckSeqs.isNotEmpty())
        val setPayload = capturedTxPayloads.lastOrNull { it[0] == SonyProtocolParser.CMD_SET_NC_ASM }
        assertNotNull(setPayload)
        // Strict 7-byte V2 NC_ASM frame on 0x17
        assertEquals(7, setPayload!!.size)
        assertEquals(0x17, setPayload[1].toInt() and 0xFF)
        assertEquals(15, setPayload[6].toInt() and 0xFF)

        client.closePersistentSession("Test end")
        runCatching { headsetTx.close() }
        runCatching { clientToHeadsetIn.close() }
        responder.interrupt()
    }
}
