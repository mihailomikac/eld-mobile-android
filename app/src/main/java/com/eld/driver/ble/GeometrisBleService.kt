package com.eld.driver.ble

import java.util.UUID

/**
 * Geometris BLE Service Constants
 * Contains UUIDs and constants for Geometris device communication
 */
object GeometrisBleService {
    // Service UUID
    val SERVICE_UUID: UUID = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb")

    // Characteristic UUIDs
    val OBD_DATA_UUID: UUID = UUID.fromString("0002a5b-0000-1000-8000-00805f9b34fb")
    val OBD_CONTROL_UUID: UUID = UUID.fromString("00002a57-0000-1000-8000-00805f9b34fb")
    val OBD_DEVICE_ADDRESS_UUID: UUID = UUID.fromString("00002a59-0000-1000-8000-00805f9b34fb")

    // Device name prefix
    const val DEVICE_NAME_PREFIX = "WQ-"

    // Commands for OBD_CONTROL characteristic
    val CMD_GET_LATEST_ELD_DATA = byteArrayOf(0x02, 0x01)  // Request latest ELD data format
    val CMD_START_UNIDENTIFIED_EVENTS = byteArrayOf(0x01, 0x02)  // Start unidentified events
    val CMD_STOP_UNIDENTIFIED_EVENTS = byteArrayOf(0x00, 0x02)  // Stop unidentified events
    val CMD_PURGE_UNIDENTIFIED_EVENTS = byteArrayOf(0x01, 0x03)  // Delete all stored events

    // Packet constants
    const val PACKET_IDENTIFIER: Byte = 0xCB.toByte()
    const val PROTOCOL_ID_V0: Byte = 0x00
    const val PROTOCOL_ID_V1: Byte = 0x01
    const val MAX_BLE_PACKET_SIZE = 21

    // Data item identifiers
    const val ITEM_VIN: Byte = 0x01
    const val ITEM_ODOMETER: Byte = 0x02
    const val ITEM_RPM: Byte = 0x03
    const val ITEM_SPEED: Byte = 0x05
    const val ITEM_ENGINE_HOURS: Byte = 0x11
    const val ITEM_LATITUDE: Byte = 0x13
    const val ITEM_LONGITUDE: Byte = 0x14
    const val ITEM_LOCATION_TIME: Byte = 0x15
    const val ITEM_UDRV_REASON: Byte = 0x16
    const val ITEM_UDRV_TIME: Byte = 0x17
    const val ITEM_UDRV_ENGINE_HOURS: Byte = 0x18
    const val ITEM_UDRV_SPEED: Byte = 0x19
    const val ITEM_UDRV_ODOMETER: Byte = 0x1A
    const val ITEM_UDRV_LATITUDE: Byte = 0x1B
    const val ITEM_UDRV_LONGITUDE: Byte = 0x1C
    const val ITEM_UDRV_LOCATION_TIME: Byte = 0x1D
    const val ITEM_UDRV_COUNT: Byte = 0x1E
    const val ITEM_SERIAL: Byte = 0x0F

    // Unidentified event reasons
    const val REASON_END_STOP = 13
    const val REASON_BEGIN_STOP = 12
    const val REASON_BLE_DISCONNECT = 40
    const val REASON_BLE_CONNECT = 39
    const val REASON_BUS_MALFUNCTION = 41
    const val REASON_BUS_MALFUNCTION_END = 42
}
