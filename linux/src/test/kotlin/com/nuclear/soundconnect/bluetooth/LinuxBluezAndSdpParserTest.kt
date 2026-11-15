package com.nuclear.soundconnect.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinuxBluezAndSdpParserTest {

    @Test
    fun `parses gdbus ObjectManager GetManagedObjects for Sony headset and hex Battery1`() {
        val fixture = """
            ({
              objectpath '/org/bluez/hci0': {
                'org.freedesktop.DBus.Introspectable': {},
                'org.bluez.Adapter1': {
                  'Address': <'00:1A:7D:DA:71:13'>,
                  'Powered': <true>,
                  'Discoverable': <false>
                }
              },
              objectpath '/org/bluez/hci0/dev_A4_1D_10_AB_CD_EF': {
                'org.bluez.Device1': {
                  'Address': <'A4:1D:10:AB:CD:EF'>,
                  'Name': <'WF-1000XM6'>,
                  'Alias': <'WF-1000XM6'>,
                  'Paired': <true>,
                  'Connected': <true>,
                  'UUIDs': <['0000110b-0000-1000-8000-00805f9b34fb', '956c7b26-d49a-4ba8-b03f-b17d393cb6e2']>
                },
                'org.bluez.Battery1': {
                  'Percentage': <byte 0x5A>
                }
              },
              objectpath '/org/bluez/hci0/dev_11_22_33_44_55_66': {
                'org.bluez.Device1': {
                  'Address': <'11:22:33:44:55:66'>,
                  'Name': <'Logitech MX Master 3S'>,
                  'Paired': <true>,
                  'Connected': <true>,
                  'UUIDs': <['00001124-0000-1000-8000-00805f9b34fb']>
                }
              }
            },)
        """.trimIndent()

        val snap = LinuxBluezMonitor.parseGdbusManagedObjects(fixture)
        assertTrue(snap.isAdapterPowered)
        assertEquals(2, snap.devices.size)

        val sony = snap.devices[0]
        assertEquals("A4:1D:10:AB:CD:EF", sony.address)
        assertEquals("WF-1000XM6", sony.name)
        assertTrue(sony.isConnected)
        assertEquals(90, sony.osBatteryPercent) // 0x5A = 90
        assertTrue(SonyRfcommClient.isSonyAudioDevice(sony))

        val mouse = snap.devices[1]
        assertFalse(SonyRfcommClient.isSonyAudioDevice(mouse))
    }

    @Test
    fun `parses sdptool browse output resolving Sony V2 Fallback and SPP RFCOMM channels`() {
        val sdptoolFixture = """
            Browsing A4:1D:10:AB:CD:EF ...
            Service Name: Headset Audio Gateway
            Service RecHandle: 0x10001
            Protocol Descriptor List:
              "L2CAP" (0x0100)
              "RFCOMM" (0x0003)
                Channel: 12

            Service RecHandle: 0x10008
            Service Class ID List:
              UUID 128: 956c7b26-d49a-4ba8-b03f-b17d393cb6e2
            Protocol Descriptor List:
              "L2CAP" (0x0100)
              "RFCOMM" (0x0003)
                Channel: 9

            Service Name: Serial Port
            Service RecHandle: 0x1000a
            Service Class ID List:
              "Serial Port" (0x1101)
            Protocol Descriptor List:
              "L2CAP" (0x0100)
              "RFCOMM" (0x0003)
                Channel: 4
        """.trimIndent()

        assertEquals(9, LinuxBluezMonitor.parseSdptoolOutputForUuid(sdptoolFixture, SonyRfcommClient.UUID_SONY_V2))
        assertEquals(4, LinuxBluezMonitor.parseSdptoolOutputForUuid(sdptoolFixture, SonyRfcommClient.UUID_SONY_SPP))
        assertNull(LinuxBluezMonitor.parseSdptoolOutputForUuid(sdptoolFixture, SonyRfcommClient.UUID_SONY_FALLBACK))
    }
}
