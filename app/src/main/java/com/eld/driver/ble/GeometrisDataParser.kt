package com.eld.driver.ble

import android.util.Log
import com.eld.driver.ble.models.GeometrisEldData
import com.eld.driver.ble.models.UnidentifiedEvent
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Date

/**
 * Geometris Data Parser
 * Parses BLE packets from Geometris device and extracts ELD data
 */
class GeometrisDataParser {
    private val TAG = "GeometrisDataParser"

    private val packets = mutableMapOf<Int, ByteArray>()
    private var protocolVersion: Int = 1
    private var totalPackets: Int = 0
    private var expectedDataLength: Int = 0

    // Public debug logs
    val debugLogs = mutableListOf<String>()

    private fun addDebugLog(message: String) {
        debugLogs.add(message)
        Log.d(TAG, message)
        // Keep only last 50 logs
        if (debugLogs.size > 50) {
            debugLogs.removeAt(0)
        }
    }

    /**
     * Add a BLE packet to the parser
     * Returns true if all packets received and data is ready to parse
     */
    fun addPacket(packet: ByteArray): Boolean {
        if (packet.isEmpty()) return false

        val packetIndex = packet[0].toInt()
        addDebugLog("Received packet #$packetIndex (${packet.size} bytes)")

        // First packet contains metadata
        if (packetIndex == 0) {
            if (packet.size > 3 && packet[1] == GeometrisBleService.PACKET_IDENTIFIER) {
                protocolVersion = packet[2].toInt()
                totalPackets = packet[3].toInt()

                if (packet.size > 6) {
                    // Calculate expected data length
                    val lengthBytes = byteArrayOf(packet[5], packet[6], 0, 0)
                    expectedDataLength = ByteBuffer.wrap(lengthBytes)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .getInt() * 2 - 2
                }

                addDebugLog("Protocol: v$protocolVersion, Total packets: $totalPackets, Expected: $expectedDataLength bytes")
            } else {
                protocolVersion = 0
                totalPackets = 7  // Old protocol
                addDebugLog("Old protocol detected (v0)")
            }
        }

        packets[packetIndex] = packet

        // Check if we have all packets
        val allReceived = packets.size >= totalPackets
        if (allReceived) {
            addDebugLog("All $totalPackets packets received!")
        }
        return allReceived
    }

    /**
     * Parse all received packets and extract ELD data
     */
    fun parseEldData(): GeometrisEldData? {
        if (packets.size < totalPackets) {
            Log.w(TAG, "Not all packets received: ${packets.size}/$totalPackets")
            return null
        }

        try {
            // Reconstruct full data sequence from packets
            val fullData = reconstructDataSequence()
            addDebugLog("Reconstructed ${fullData.size} bytes total")

            if (fullData.size >= 20) {
                val first20 = fullData.take(20).toByteArray().toHexString()
                addDebugLog("First 20 bytes: $first20")
            }

            // Parse data based on protocol version
            val eldData = GeometrisEldData(protocolVersion = protocolVersion)

            if (protocolVersion == 0) {
                addDebugLog("Parsing with Protocol V0")
                parseProtocolV0(fullData, eldData)
            } else {
                addDebugLog("Parsing with Protocol V1")
                parseProtocolV1(fullData, eldData)
            }

            addDebugLog("Parsing completed successfully")
            return eldData
        } catch (e: Exception) {
            addDebugLog("ERROR: ${e.message}")
            Log.e(TAG, "Error parsing ELD data", e)
            return null
        } finally {
            reset()
        }
    }

    /**
     * Reconstruct full data sequence from multiple BLE packets
     */
    private fun reconstructDataSequence(): ByteArray {
        val dataList = mutableListOf<Byte>()

        for (i in 0 until totalPackets) {
            val packet = packets[i] ?: continue

            if (protocolVersion == 0) {
                // Protocol V0: ALL packets just have 1-byte index, skip it
                for (j in 1 until packet.size) {
                    dataList.add(packet[j])
                }
            } else {
                // Protocol V1+
                if (i == 0) {
                    // First packet: skip header (7 bytes) and add data
                    for (j in 7 until packet.size) {
                        dataList.add(packet[j])
                    }
                } else {
                    // Subsequent packets: skip index (1 byte) and add data
                    for (j in 1 until packet.size) {
                        dataList.add(packet[j])
                    }
                }
            }
        }

        return dataList.toByteArray()
    }

    /**
     * Parse Protocol V0 (older firmware)
     * V0 uses fixed packet positions (7 packets total, 0-6)
     * Based on official Geometris documentation
     */
    private fun parseProtocolV0(data: ByteArray, eldData: GeometrisEldData) {
        addDebugLog("Parsing V0 protocol with ${data.size} bytes")
        addDebugLog("Raw bytes: ${data.take(minOf(64, data.size)).toByteArray().toHexString()}")

        // V0 protocol: 7 packets (0-6)
        // Packets 0-1: VIN (string)
        // Packet 2: Odometer(offset 1), RPM(offset 5), Speed(offset 13)
        // Packet 6: Engine Hours(offset 5)

        try {
            // Packets 0-1 are empty (1 byte each = just index)
            // Packet 2 data starts at reconstructed index 0
            // Java code "packet[1]" = our reconstructed data[0]

            if (data.size >= 16) {
                // Odometer: Java packet[1] → reconstructed data[0]
                val odometer = readUInt32LittleEndian(data, 0)
                addDebugLog("Odometer raw: $odometer (0x${odometer.toString(16)})")
                if (odometer != 0xFFFFFFFF && odometer > 0) {
                    eldData.odometer = odometer.toDouble()
                    eldData.odometerTimestamp = Date()
                    addDebugLog("Odometer: ${eldData.odometer} km")
                } else {
                    addDebugLog("Odometer invalid or zero")
                }

                // RPM: Java packet[5] → reconstructed data[4]
                val rpm = readUInt32LittleEndian(data, 4)
                if (rpm != 0xFFFFFFFF && rpm < 10000) {
                    eldData.rpm = rpm.toDouble()
                    eldData.rpmTimestamp = Date()
                    addDebugLog("RPM: ${eldData.rpm}")
                }

                // Speed: Java packet[13] → reconstructed data[12]
                val speed = readUInt32LittleEndian(data, 12)
                if (speed != 0xFFFFFFFF && speed < 500) {
                    eldData.speed = speed.toDouble()
                    eldData.speedTimestamp = Date()
                    addDebugLog("Speed: ${eldData.speed} km/h")
                }
            }

            // Packet 6 starts at: 0(p0) + 0(p1) + 16(p2) + 16(p3) + 16(p4) + 8(p5) = 56
            // Engine Hours: Java packet[5] → reconstructed data[56 + 4] = 60
            if (data.size >= 64) {
                val engineHours = readUInt32LittleEndian(data, 60)
                if (engineHours != 0xFFFFFFFF && engineHours < 10000000) {
                    eldData.engineHours = engineHours.toDouble() / 10.0
                    eldData.engineHoursTimestamp = Date()
                    addDebugLog("Engine Hours: ${eldData.engineHours} hrs")
                }
            }

            // Try to extract VIN from packets 0-1 if they contain ASCII data
            if (data.size >= 32) {
                val vinBytes = mutableListOf<Byte>()
                for (i in 0 until minOf(17, data.size)) {
                    val b = data[i]
                    if (b in 32..126) { // Printable ASCII
                        vinBytes.add(b)
                    }
                }
                if (vinBytes.size >= 10) {
                    eldData.vin = String(vinBytes.toByteArray(), Charsets.US_ASCII)
                    addDebugLog("VIN: ${eldData.vin}")
                }
            }

            addDebugLog("V0 parsing completed")
        } catch (e: Exception) {
            addDebugLog("V0 parsing error: ${e.message}")
        }
    }

    /**
     * Parse Protocol V1 (current firmware)
     */
    private fun parseProtocolV1(data: ByteArray, eldData: GeometrisEldData) {
        addDebugLog("Starting V1 parse, ${data.size} bytes")
        var index = 2  // Skip first 2 bytes
        val currentUnidentifiedEvent = UnidentifiedEvent()
        var itemCount = 0

        while (index < data.size - 1) { // Need at least 2 bytes for item ID
            val itemId = data[index]
            val itemIdHex = "0x${itemId.toString(16).padStart(2, '0')}"
            addDebugLog("Item #$itemCount at [$index]: $itemIdHex")

            index += 2  // Skip item ID (2 bytes with 0x00)
            itemCount++

            if (index >= data.size) {
                addDebugLog("No more data after item ID")
                break
            }

            when (itemId) {
                GeometrisBleService.ITEM_VIN -> {
                    // Variable length
                    val length = data[index].toInt()
                    index += 2
                    if (length > 0) {
                        val vinBytes = ByteArray(length) { i ->
                            data[index + i * 2]
                        }
                        eldData.vin = String(vinBytes, Charsets.US_ASCII)
                        Log.d(TAG, "VIN: ${eldData.vin}")
                        index += length * 2
                    }
                }

                GeometrisBleService.ITEM_ODOMETER -> {
                    val fixedBytes = fixUint32Endian(data, index)
                    val value = readUInt32LittleEndian(fixedBytes, 0)
                    eldData.odometer = value.toDouble()
                    eldData.odometerTimestamp = Date()
                    Log.d(TAG, "Odometer: ${eldData.odometer} km")
                    index += 4
                }

                GeometrisBleService.ITEM_RPM -> {
                    val fixedBytes = fixUint32Endian(data, index)
                    val value = readUInt32LittleEndian(fixedBytes, 0)
                    eldData.rpm = value.toDouble()
                    eldData.rpmTimestamp = Date()
                    Log.d(TAG, "RPM: ${eldData.rpm}")
                    index += 4
                }

                GeometrisBleService.ITEM_SPEED -> {
                    val fixedBytes = fixUint32Endian(data, index)
                    val value = readUInt32LittleEndian(fixedBytes, 0)
                    eldData.speed = value.toDouble()
                    eldData.speedTimestamp = Date()
                    Log.d(TAG, "Speed: ${eldData.speed} km/h")
                    index += 4
                }

                GeometrisBleService.ITEM_ENGINE_HOURS -> {
                    val fixedBytes = fixUint32Endian(data, index)
                    val value = readUInt32LittleEndian(fixedBytes, 0)
                    eldData.engineHours = value.toDouble() / 10.0  // Device sends * 10
                    eldData.engineHoursTimestamp = Date()
                    Log.d(TAG, "Engine Hours: ${eldData.engineHours} hrs")
                    index += 4
                }

                GeometrisBleService.ITEM_LATITUDE -> {
                    val fixedBytes = fixUint32Endian(data, index)
                    val value = readUInt32LittleEndian(fixedBytes, 0)
                    eldData.latitude = value.toDouble() / 100000.0
                    Log.d(TAG, "Latitude: ${eldData.latitude}")
                    index += 4
                }

                GeometrisBleService.ITEM_LONGITUDE -> {
                    val fixedBytes = fixUint32Endian(data, index)
                    val value = readUInt32LittleEndian(fixedBytes, 0)
                    eldData.longitude = value.toDouble() / 100000.0
                    Log.d(TAG, "Longitude: ${eldData.longitude}")
                    index += 4
                }

                GeometrisBleService.ITEM_LOCATION_TIME -> {
                    val fixedBytes = fixUint32Endian(data, index)
                    val value = readUInt32LittleEndian(fixedBytes, 0)
                    eldData.gpsTime = value.toLong() * 1000  // Convert to milliseconds
                    eldData.locationTimestamp = Date()
                    Log.d(TAG, "GPS Time: ${Date(eldData.gpsTime!!)}")
                    index += 4
                }

                // Unidentified event data
                GeometrisBleService.ITEM_UDRV_REASON -> {
                    val fixedBytes = fixUint32Endian(data, index)
                    currentUnidentifiedEvent.reason = readUInt32LittleEndian(fixedBytes, 0).toInt()
                    Log.d(TAG, "UDRV Reason: ${currentUnidentifiedEvent.getReasonString()}")
                    index += 4
                }

                GeometrisBleService.ITEM_UDRV_TIME -> {
                    val fixedBytes = fixUint32Endian(data, index)
                    currentUnidentifiedEvent.timestamp = readUInt32LittleEndian(fixedBytes, 0).toLong()
                    index += 4
                }

                GeometrisBleService.ITEM_UDRV_ENGINE_HOURS -> {
                    val fixedBytes = fixUint32Endian(data, index)
                    currentUnidentifiedEvent.engineHours = readUInt32LittleEndian(fixedBytes, 0).toDouble() / 10.0
                    index += 4
                }

                GeometrisBleService.ITEM_UDRV_SPEED -> {
                    val fixedBytes = fixUint32Endian(data, index)
                    currentUnidentifiedEvent.speed = readUInt32LittleEndian(fixedBytes, 0).toDouble()
                    index += 4
                }

                GeometrisBleService.ITEM_UDRV_ODOMETER -> {
                    val fixedBytes = fixUint32Endian(data, index)
                    currentUnidentifiedEvent.odometer = readUInt32LittleEndian(fixedBytes, 0).toDouble()
                    index += 4
                }

                GeometrisBleService.ITEM_UDRV_LATITUDE -> {
                    val fixedBytes = fixUint32Endian(data, index)
                    currentUnidentifiedEvent.latitude = readUInt32LittleEndian(fixedBytes, 0).toDouble() / 100000.0
                    index += 4
                }

                GeometrisBleService.ITEM_UDRV_LONGITUDE -> {
                    val fixedBytes = fixUint32Endian(data, index)
                    currentUnidentifiedEvent.longitude = readUInt32LittleEndian(fixedBytes, 0).toDouble() / 100000.0
                    index += 4
                }

                GeometrisBleService.ITEM_UDRV_LOCATION_TIME -> {
                    val fixedBytes = fixUint32Endian(data, index)
                    currentUnidentifiedEvent.gpsTimestamp = readUInt32LittleEndian(fixedBytes, 0).toLong()
                    index += 4
                }

                GeometrisBleService.ITEM_UDRV_COUNT -> {
                    val fixedBytes = fixUint32Endian(data, index)
                    eldData.totalUnidentifiedEvents = readUInt32LittleEndian(fixedBytes, 0).toInt()
                    Log.d(TAG, "Total unidentified events: ${eldData.totalUnidentifiedEvents}")
                    index += 4
                }

                GeometrisBleService.ITEM_SERIAL -> {
                    // Variable length
                    val length = data[index].toInt()
                    index += 2
                    if (length > 0) {
                        val serialBytes = ByteArray(length) { i ->
                            data[index + i * 2]
                        }
                        eldData.serialNumber = String(serialBytes, Charsets.US_ASCII)
                        Log.d(TAG, "Serial: ${eldData.serialNumber}")
                        index += length * 2
                    }
                }

                else -> {
                    Log.w(TAG, "Unknown item ID: 0x${itemId.toString(16)}")
                    break  // Exit parsing on unknown item
                }
            }
        }

        // Add unidentified event if it has timestamp
        if (currentUnidentifiedEvent.timestamp != null) {
            eldData.unidentifiedEvents.add(currentUnidentifiedEvent)
        }
    }

    /**
     * Read 4-byte unsigned integer (standard little-endian)
     * Used for Protocol V0 (reads directly from packet)
     */
    private fun readUInt32LittleEndian(data: ByteArray, offset: Int): Long {
        if (offset + 4 > data.size) return 0

        val b0 = data[offset].toLong() and 0xFF
        val b1 = data[offset + 1].toLong() and 0xFF
        val b2 = data[offset + 2].toLong() and 0xFF
        val b3 = data[offset + 3].toLong() and 0xFF

        // Standard little-endian: b0 + (b1 << 8) + (b2 << 16) + (b3 << 24)
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    /**
     * Fix Geometris V1 endianness: [D0, D1, D2, D3] → [D2, D3, D0, D1]
     * Used for Protocol V1 (must fix bytes before reading)
     */
    private fun fixUint32Endian(data: ByteArray, offset: Int): ByteArray {
        if (offset + 4 > data.size) return byteArrayOf(0, 0, 0, 0)

        return byteArrayOf(
            data[offset + 2],
            data[offset + 3],
            data[offset],
            data[offset + 1]
        )
    }

    /**
     * Reset parser state
     */
    fun reset() {
        packets.clear()
        protocolVersion = 1
        totalPackets = 0
        expectedDataLength = 0
    }

    /**
     * Extension function to convert ByteArray to hex string
     */
    private fun ByteArray.toHexString(): String {
        return joinToString(", ") { String.format("0x%02x", it) }
    }
}
