package com.usel.maglev.maglev_app

import android.app.Activity
import android.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.bluetooth.BluetoothAdapter
import android.widget.Toast
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.*
import java.lang.Thread
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.support.annotation.ColorInt
import android.util.Log
import android.util.TypedValue
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.view.*
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
    private var targetBluetoothAddress = "98:01:A7:AE:92:22"        // placeholder
    private fun getTargetBluetoothAddress() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Set target bluetooth address (MAC)")
        val input = EditText(this)
        builder.setView(input)
        builder.setPositiveButton("OK", object: DialogInterface.OnClickListener {
            override fun onClick(dialog: DialogInterface?, which: Int) {
                targetBluetoothAddress = input.text.toString()
            }
        }).setNegativeButton("Cancel", object: DialogInterface.OnClickListener {
            override fun onClick(dialog: DialogInterface?, which: Int) {
                dialog?.cancel()
            }
        })
        builder.show()
    }
    object protocol {
        val delimiter = ";"
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

        fun getColorPreviewColorInt(): Int = (color_preview.background as ColorDrawable).color
        data class component(val seekbar: SeekBar, val value: TextView)
        val seekbars = arrayOf(
            component(red_seekbar, red_value), component(green_seekbar, green_value), component(blue_seekbar, blue_value)
        )
        seekbars.forEachIndexed { i, component ->
            component.seekbar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val colorInt = getColorPreviewColorInt()
                    val newColor = Color.rgb(
                        if (i == 0) progress else Color.red(colorInt),
                        if (i == 1) progress else Color.green(colorInt),
                        if (i == 2) progress else Color.blue(colorInt)
                    )
                    color_preview.setBackgroundColor(newColor)
                    component.value.text = progress.toString()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) { }
                override fun onStopTrackingTouch(seekBar: SeekBar?) { }
            })
        }

        submit_color.setOnClickListener {
            val colorInt = getColorPreviewColorInt()
            val msg = "r${Color.red(colorInt)}g${Color.green(colorInt)}b${Color.blue(colorInt)}"
            sendMessage(msg)
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
     * **paired** -> connect -> use
     */
    private fun setupConnection(context: Context) {
        getTargetBluetoothAddress()
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
             * todo: dynamic to arduino
             */
            if (it.address == targetBluetoothAddress) {
                val _connection = BluetoothConnection(it)
                _connection.start()
                bluetoothConnection = _connection
            }
        }


    }

    /**
     * paired -> connect -> **use**
     */
    private fun sendMessage(msg: String) {
        bluetoothConnection?.let {
            val delimitedMsg = msg + protocol.delimiter
            val bitMsg = delimitedMsg.toByteArray()
            it.write(bitMsg)
            Toast.makeText(this, "sendBitMessage: $bitMsg\nsendDelimitedMessage: $delimitedMsg", Toast.LENGTH_LONG).show()
            Log.d(TAG, delimitedMsg)
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
        bluetoothConnection?.cancel()
    }




    /**
     * Thread class for connection
     * inner class can access parent properties
     * paired -> **connect** -> use
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
            attempt({ socket.connect() })

            val connectionThread = Thread(object: Runnable {
                override fun run() {
                    bluetoothAdapter.cancelDiscovery()
                    attempt({ socket.connect() }, {
                        attempt({ socket.close() })
                    })
                }
            })

//            connectionThread.start()
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
                    Log.d(TAG, "run is broken")
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