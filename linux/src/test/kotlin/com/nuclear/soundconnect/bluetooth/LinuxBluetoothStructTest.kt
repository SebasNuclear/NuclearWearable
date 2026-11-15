package com.nuclear.soundconnect.bluetooth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class LinuxBluetoothStructTest {

    @Test
    fun `sockaddr_rc matches BlueZ C ABI size and field offsets`() {
        val addr = LinuxBluetoothNative.buildSockAddrRc("AA:BB:CC:DD:EE:FF", channel = 9)
        assertEquals(10, addr.size())

        val bytes = addr.pointer.getByteArray(0, addr.size())
        // rc_family = AF_BLUETOOTH (31) at offset 0..1 (LE/native)
        val family = ByteBuffer.wrap(bytes, 0, 2).order(ByteOrder.nativeOrder()).short.toInt()
        assertEquals(31, family)

        // rc_bdaddr in reversed byte order at offset 2..7
        assertArrayEquals(
            byteArrayOf(
                0xFF.toByte(),
                0xEE.toByte(),
                0xDD.toByte(),
                0xCC.toByte(),
                0xBB.toByte(),
                0xAA.toByte()
            ),
            bytes.copyOfRange(2, 8)
        )

        // rc_channel at offset 8
        assertEquals(9, bytes[8].toInt() and 0xFF)
    }

    @Test
    fun `sockaddr_l2 matches BlueZ L2CAP SDP ABI size and PSM 1`() {
        val addr = LinuxBluetoothNative.buildSockAddrL2Sdp("11:22:33:44:55:66")
        assertEquals(14, addr.size())

        val bytes = addr.pointer.getByteArray(0, addr.size())
        val family = ByteBuffer.wrap(bytes, 0, 2).order(ByteOrder.nativeOrder()).short.toInt()
        val psm = ByteBuffer.wrap(bytes, 2, 2).order(ByteOrder.nativeOrder()).short.toInt()
        assertEquals(31, family)
        assertEquals(1, psm)
        assertArrayEquals(
            byteArrayOf(0x66, 0x55, 0x44, 0x33, 0x22, 0x11),
            bytes.copyOfRange(4, 10)
        )
    }

    @Test
    fun `SDP ServiceSearchAttributeRequest encodes Sony UUID V2 and ProtocolDescriptorList 0x0004`() {
        val uuid = UUID.fromString("956c7b26-d49a-4ba8-b03f-b17d393cb6e2")
        val pdu = LinuxBluetoothNative.buildSdpServiceSearchAttrRequest(uuid, txnId = 0x1234)
        assertEquals(0x06, pdu[0].toInt() and 0xFF)
        assertEquals(0x12, pdu[1].toInt() and 0xFF)
        assertEquals(0x34, pdu[2].toInt() and 0xFF)
        assertEquals(0x35, pdu[5].toInt() and 0xFF) // Data Element Sequence
        assertEquals(0x11, pdu[6].toInt() and 0xFF)
        assertEquals(0x1C, pdu[7].toInt() and 0xFF) // 128-bit UUID marker
        // First 4 bytes of UUID = 95 6C 7B 26
        assertEquals(0x95.toByte(), pdu[8])
        assertEquals(0x6C.toByte(), pdu[9])
        assertEquals(0x7B.toByte(), pdu[10])
        assertEquals(0x26.toByte(), pdu[11])
    }

    @Test
    fun `SDP response parser extracts RFCOMM channel from ProtocolDescriptorList`() {
        // Construct synthetic SDP_SVC_SEARCH_ATTR_RSP (PDU 0x07):
        // AttrListSeq: DES { AttrList: DES { AttrID(0x0004), Value: DES { DES{UUID16 0x0100(L2CAP)}, DES{UUID16 0x0003(RFCOMM), uint8(9)} } } }
        val innerProtoSeq = byteArrayOf(
            0x35, 0x05,
            0x19, 0x00, 0x03, // RFCOMM UUID
            0x08, 0x09        // uint8 channel 9
        )
        val l2capSeq = byteArrayOf(0x35, 0x03, 0x19, 0x01, 0x00)
        val protoDescValue = byteArrayOf(0x35, (l2capSeq.size + innerProtoSeq.size).toByte()) + l2capSeq + innerProtoSeq
        val attrPair = byteArrayOf(0x09, 0x00, 0x04) + protoDescValue
        val oneSvcDes = byteArrayOf(0x35, attrPair.size.toByte()) + attrPair
        val outerList = byteArrayOf(0x35, oneSvcDes.size.toByte()) + oneSvcDes

        val paramLen = 2 + outerList.size + 1
        val rsp = ByteBuffer.allocate(5 + paramLen).order(ByteOrder.BIG_ENDIAN).apply {
            put(0x07.toByte()) // SDP_SVC_SEARCH_ATTR_RSP
            putShort(0x0001)
            putShort(paramLen.toShort())
            putShort(outerList.size.toShort())
            put(outerList)
            put(0x00.toByte())
        }.array()

        assertEquals(9, LinuxBluetoothNative.parseSdpRfcommChannelFromResponse(rsp))
        assertNull(LinuxBluetoothNative.parseSdpRfcommChannelFromResponse(byteArrayOf(0x01, 0x00)))
    }
}
