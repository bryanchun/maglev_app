package com.usel.maglev.maglev_app

import android.app.Activity
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.widget.Toast
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import java.lang.Thread
import android.content.IntentFilter
import android.util.Log
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

inline fun attempt(block: ()->Unit, subblock: ()->Unit = {}) {       // inline is macro
    try {
        block()
    } catch (e: IOException) {
        e.printStackTrace()
        subblock()
    }
}

class MainActivity : AppCompatActivity() {

    /**
     * @property RequestCodes when extra activity pops up for user input, this object contains a list of request codes for communicating with onActivityResult
     */
    object RequestCodes {
        val REQUEST_ENABLE_BT = 1
    }
    private val TAG = "MainActivity"
    object protocol {
        val delimiter = "\n"
    }

    /**
     * @property bluetoothAdapter maintains bluetooth connection
     * @property discoveryFinishReceiver finds devices to connect to
     * @property bluetoothConnection manipulatable for sending data to bluetooth device
     */
    private val bluetoothAdapter: BluetoothAdapter by lazy {
        BluetoothAdapter.getDefaultAdapter()
    }
//    private lateinit var discoveryFinishReceiver: BroadcastReceiver
    /*val bluetoothConnection: BluetoothConnection by lazy {
        BluetoothConnection(it)
    }*/
    private var bluetoothConnection: BluetoothConnection? = null


    /**
     * Entry point of the app
     * setup UI listeners
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bluetoothAdapter.let {
            when (it.isEnabled) {
                true -> {
                    Toast.makeText(this, "Bluetooth is enabled", Toast.LENGTH_LONG).show()
                    setupConnection(this)
                }
                false -> {
                    Toast.makeText(this, "Bluetooth is to be enabled", Toast.LENGTH_LONG).show()
                    val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    startActivityForResult(enableIntent, RequestCodes.REQUEST_ENABLE_BT)
                }
            }
        }

        submit1.setOnClickListener {
            sendMessage("1submit")
        }
    }

    /**
     * second node
     * General handler for startActivityForResult
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            RequestCodes.REQUEST_ENABLE_BT -> when (resultCode) {
                Activity.RESULT_OK -> setupConnection(this)
                Activity.RESULT_CANCELED -> Toast.makeText(this, "Bluetooth switching-on is aborted", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun discoverBluetooth() {
        // no need to be discovered
    }

    /**
     * third node
     * If bluetoothAdapter isEnabled, setup connection
     */
    private fun setupConnection(context: Context) {
        val pairedDevices = bluetoothAdapter.bondedDevices
        /**
         * @todo multiple paired (memorised) devices, don't need to be connected || connect to multiple at the same time?
         *
         * @deprecated manual pairing
         */
//        discoveryFinishReceiver = object : BroadcastReceiver() {
//            override fun onReceive(context: Context, intent: Intent) {
//                val action = intent.action
//                if (BluetoothDevice.ACTION_FOUND == action) {
//                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
//                    val deviceInfo = device.getName() + "\n" + device.getAddress()
//                    Toast.makeText(context, deviceInfo, Toast.LENGTH_LONG).show()
//                }
//            }
//        }
//        registerReceiver(discoveryFinishReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))

        pairedDevices.map {
            val deviceInfo = it.name + "\n" + it.address
            Toast.makeText(context, deviceInfo, Toast.LENGTH_LONG).show()
            Log.d(TAG, deviceInfo)

            /**
             * Thread goes here with it
             * hardcoded: if (it is arduino || mac) {...}
             */
            if (it.address == "98:01:A7:AE:92:22") {
                val _connection = BluetoothConnection(it)
                _connection.start()
                bluetoothConnection = _connection
            }
        }


    }

    private fun sendMessage(msg: String) {
        bluetoothConnection?.let {
            it.write((msg + protocol.delimiter).toByteArray())
            Toast.makeText(this, "sendMessage: "+msg.toByteArray(), Toast.LENGTH_LONG).show()
        }
    }


    /**
     * Free up discoveryFinishReceiver by unregistering it on exit
     */
    override fun onDestroy() {
        super.onDestroy()
//        discoveryFinishReceiver?.let {
//            unregisterReceiver(it)
//        }
    }




    /**
     * Thread class for connection
     * inner class can access parent properties
     * pair -> connect -> use
     *
     * @constructor pass bluetoothDevice and isSecure to create an instance thread
     * @run overridden to execute socket connection
     */
    inner class BluetoothConnection(private val device: BluetoothDevice) : Thread() {
        /**
         * @property TAG name of thread for Log.d logging
         * @property uuid The UUID (Universally Unique Identifier) parameter is a standardized 128-bit format string ID that uniquely identifies your app’s Bluetooth service. Whenever a client attempts to connect to a server, it’ll carry a UUID that identifies the service it’s looking for. The server will only accept a connection request if the client’s UUID matches the one registered with the listening server socket.
        */
        private val TAG = "BluetoothConnection"
        private val uuid: UUID = device.uuids[0].uuid

        /**
         * @property socket
         * @property inputSteam
         * @property outputStream
         * @property buffer container for read messages
         */
        var socket: BluetoothSocket
        var inputStream: InputStream
        var outputStream: OutputStream
        lateinit var buffer: ByteArray


        /**
         * @constructor init socket and start thread to connect to socket
         *
         * @todo catch if not connected
         */
        init {
            lateinit var tmp: BluetoothSocket
            attempt({
                tmp = device.createRfcommSocketToServiceRecord(uuid)
            })
            socket = tmp
            socket.connect()

            val connectionThread = Thread(object: Runnable {
                override fun run() {
                    bluetoothAdapter.cancelDiscovery()
                    attempt({ socket.connect() }, {
                        attempt({ socket.close() })
                    })
                }
            })

            connectionThread.start()
            lateinit var tmpIn: InputStream
            lateinit var tmpOut: OutputStream
            val bufferSize = 1024
            attempt({
                tmpIn = socket.inputStream
                tmpOut = socket.outputStream
                buffer = ByteArray(bufferSize)
            })
            inputStream = tmpIn
            outputStream = tmpOut
        }

        /**
         * @deprecated isSecure
         */
        // fun getSocketType() = if (isSecure) "_Secure" else "_Insecure"

        override fun run() {
            while (true) {
                try {
                    inputStream.read(buffer)
                } catch (e: IOException) {
                    e.printStackTrace()
                    break
                }
            }
        }

        fun write(buffer: ByteArray) {
            attempt({
                outputStream.write(buffer)
                outputStream.flush()
            })
        }

        fun cancel() {
            attempt({
                socket.close()
            })
        }
    }
}