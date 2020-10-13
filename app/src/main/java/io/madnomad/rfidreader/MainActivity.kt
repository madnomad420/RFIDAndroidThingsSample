package io.madnomad.rfidreader

import android.R.attr
import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.galarzaa.androidthings.Rc522
import com.google.android.things.pio.Gpio
import com.google.android.things.pio.PeripheralManager
import com.google.android.things.pio.SpiDevice
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*
import kotlin.concurrent.thread


class MainActivity : Activity() {
    private var mRc522: Rc522? = null
    private val defaultKey = byteArrayOf(
        0xFF.toByte(),
        0xFF.toByte(),
        0xFF.toByte(),
        0xFF.toByte(),
        0xFF.toByte(),
        0xFF.toByte()
    )
    private val statusText: TextView by lazy {
        findViewById<TextView>(R.id.statusText)
    }
    private var device: SpiDevice? = null
    private var resetPin: Gpio? = null
    private val readBtn: Button by lazy {
        findViewById<Button>(R.id.readBtn)
    }
    private val writeBtn: Button by lazy {
        findViewById<Button>(R.id.writeBtn)
    }
    private val DEVICE_NAME = "SPI0.0"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val manager = PeripheralManager.getInstance()
        val deviceList: List<String> = manager.spiBusList
        if(deviceList.isEmpty()) {
            statusText.text ="No devices found"
        } else {
            statusText.text = "Devices: " + deviceList.joinToString(separator = ",")
        }

        if(deviceList.contains(DEVICE_NAME)) {
            try {
                device = manager.openSpiDevice(DEVICE_NAME)
                resetPin = manager.openGpio("BCM25")
                mRc522 = Rc522(device, resetPin)
                readBtn.setEnabled(true)
            } catch(e: Exception) {
                e.printStackTrace()
            }
        } else {
            statusText.text = "RFID device not found"
        }
        readBtn.setOnClickListener {
            mRc522!!.stopCrypto()
            thread(start=true) {
                var endTimestamp = System.currentTimeMillis() + 3000
                runOnUiThread {
                    statusText.text = "Listening..."
                    it.setEnabled(false)
                }
                while(System.currentTimeMillis() < endTimestamp) {
                    try {
                        if(!mRc522!!.request()) {
                            continue
                        }
                        if(!mRc522!!.antiCollisionDetect()) {
                            continue
                        }
                        val uuid: ByteArray = mRc522!!.uid
                        mRc522!!.selectTag(uuid)
                        readData()
                    } catch (e : java.lang.Exception) {
                        e.printStackTrace()
                        runOnUiThread {
                            statusText.text = "Ошибка"
                        }
                        break
                    }
                }
                runOnUiThread {
                    it.setEnabled(true)
                }
                Thread.currentThread().interrupt()
            }
        }
        writeBtn.setOnClickListener {
            mRc522!!.stopCrypto()
            thread(start=true) {
                var endTimestamp = System.currentTimeMillis() + 3000
                val generated = UUID.randomUUID()
                runOnUiThread {
                    statusText.text = "Generated UUID: " + generated.toString() + ". Waiting card..."
                    it.setEnabled(false)
                }
                while(System.currentTimeMillis() < endTimestamp) {
                    try {
                        if(!mRc522!!.request()) {
                            continue
                        }
                        if(!mRc522!!.antiCollisionDetect()) {
                            continue
                        }
                        val uuid: ByteArray = mRc522!!.uid
                        mRc522!!.selectTag(uuid)
                        writeUUID(generated)
                    } catch (e : java.lang.Exception) {
                        e.printStackTrace()
                        runOnUiThread {
                            statusText.text = "Ошибка"
                        }
                        break
                    }
                }
                runOnUiThread {
                    it.setEnabled(true)
                }
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun readData()  {
        val address = Rc522.getBlockAddress(1, 1)
        if(!mRc522!!.authenticateCard(Rc522.AUTH_A, address, defaultKey)) {
            throw java.lang.Exception("Auth failed: False returned")
        }
        var buffer = ByteArray(16)
        if(!mRc522!!.readBlock(address, buffer)) {
            throw java.lang.Exception("Read block failed: False returned")
        }
        var bb: ByteBuffer = ByteBuffer.wrap(buffer)
        var uuid = UUID(bb.long, bb.long)
        runOnUiThread {
            statusText.text = "UUID: " + uuid.toString()
        }
    }

    private fun writeUUID(uuid: UUID) {
        var buffer = ByteBuffer.allocate(16).putLong(uuid.mostSignificantBits).putLong(uuid.leastSignificantBits).array()
        val address = Rc522.getBlockAddress(1, 1)
        if(!mRc522!!.authenticateCard(Rc522.AUTH_A, address, defaultKey)) {
            throw java.lang.Exception("Auth failed: False returned")
        }
        if(!mRc522!!.writeBlock(address, buffer)) {
            throw java.lang.Exception("Write block failed: False returned")
        }
        runOnUiThread {
            statusText.text = "Complete"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            device?.close()
            resetPin?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
