/**
 * Copyright (c) 2025 BlackCyan.
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package top.blackcyan.alberteinstein

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.blackcyan.alberteinstein.ui.theme.AlbertEinsteinTheme
import java.io.IOException
import java.nio.charset.Charset
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val bluetoothManager by lazy {
        getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager.adapter
    }
    private var bluetoothSocket: BluetoothSocket? = null
    private var connectedDeviceAddress: String? = null
    private var connectedDeviceName: String? = null
    private var isConnected by mutableStateOf(false)
    private val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var pairedDevices by mutableStateOf<List<BluetoothDevice>>(emptyList())
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                mainScope.launch {
                    getPairedDevices()
                }
            } else {
                Toast.makeText(this, getString(R.string.permission_request_failed), Toast.LENGTH_SHORT).show()
            }
        }

    private val bluetoothConnectionReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    connectedDeviceAddress = device?.address
                    connectedDeviceName = device?.name
                    isConnected = true
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    connectedDeviceAddress = null
                    connectedDeviceName = null
                    isConnected = false
                    closeSocket()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothConnectionReceiver, getConnectionIntentFilter(),
                RECEIVER_NOT_EXPORTED
            )
        }

        if (checkBluetoothPermissions()) {
            CoroutineScope(Dispatchers.IO).launch {
                getPairedDevices()
            }
        } else {
            requestBluetoothPermissions()
        }

        setContent {
            AlbertEinsteinTheme {
                BluetoothAppUI(
                    isConnected = isConnected,
                    connectedDeviceName = connectedDeviceName,
                    pairedDevices = pairedDevices,
                    onDeviceClick = { mainScope.launch { connectToDevice(it) } },
                    onSendClick = { sendData(it) },
                    onDisconnectClick = {
                        closeSocket()
                        isConnected = false
                        connectedDeviceName = null
                        connectedDeviceAddress = null
                        CoroutineScope(Dispatchers.IO).launch {
                            getPairedDevices()
                        }
                    }
                )
            }
        }
    }

    private fun getConnectionIntentFilter(): IntentFilter {
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        return filter
    }

    private fun checkBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBluetoothPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
        requestPermissionLauncher.launch(permissions)
    }

    @SuppressLint("MissingPermission")
    private fun getPairedDevices() {
        bluetoothAdapter?.bondedDevices?.let {
            pairedDevices = it.toList()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectToDevice(device: BluetoothDevice) {
        withContext(Dispatchers.IO) {
            var socket: BluetoothSocket? = null
            try {
                socket = device.createRfcommSocketToServiceRecord(uuid)
                socket.connect()
                bluetoothSocket = socket
                withContext(Dispatchers.Main) {
                    isConnected = true
                    connectedDeviceName = device.name
                    connectedDeviceAddress = device.address
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.connected) + device.name,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: IOException) {
                closeSocket(socket)
                withContext(Dispatchers.Main) {
                    isConnected = false
                    connectedDeviceName = null
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.connect_failed) + e.message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun sendData(data: String) {
        if (!isConnected || bluetoothSocket == null) {
            Toast.makeText(this, getString(R.string.disconnected), Toast.LENGTH_SHORT).show()
            return
        }
        val formattedData = "@$data\r\n"
        try {
            val outputStream = bluetoothSocket!!.outputStream
            outputStream.write(formattedData.toByteArray(Charset.forName("GBK")))
            Toast.makeText(this, getString(R.string.send_success), Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.send_failed), Toast.LENGTH_SHORT).show()
            closeSocket()
        }
    }

    private fun closeSocket() {
        try {
            bluetoothSocket?.close()
            bluetoothSocket = null
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun closeSocket(socket: BluetoothSocket? = this.bluetoothSocket) {
        try {
            socket?.close()
            this.bluetoothSocket = null
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        closeSocket()
        unregisterReceiver(bluetoothConnectionReceiver)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun BluetoothAppUI(
    isConnected: Boolean,
    connectedDeviceName: String?,
    pairedDevices: List<BluetoothDevice>,
    onDeviceClick: (BluetoothDevice) -> Unit,
    onSendClick: (String) -> Unit,
    onDisconnectClick: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var scrollDirection by remember { mutableStateOf("1") }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.display)) },
                modifier = Modifier
                    .background(Color.White)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isConnected) stringResource(R.string.connected) + "$connectedDeviceName" else stringResource(R.string.disconnected),
                color = if (isConnected) Color.Green else Color.Red,
                modifier = Modifier
                    .padding(bottom = 16.dp)
            )

            if (!isConnected) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(pairedDevices) { device ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = device.name ?: device.address,
                                modifier = Modifier
                                    .clickable {
                                        onDeviceClick(device)
                                    }
                                    .padding(16.dp)
                                    .align(Alignment.Center)
                            )
                        }
                        HorizontalDivider()
                    }
                }
            } else {
                Button(
                    onClick = onDisconnectClick,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(stringResource(R.string.disconnect))
                }
            }

            TextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text(stringResource(R.string.input)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Row {
                Button(
                    onClick = {
                        scrollDirection = "1"
                    },
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(stringResource(R.string.left))
                }

                Button(
                    onClick = {
                        scrollDirection = "2"
                    },
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(stringResource(R.string.right))
                }
            }

            Button(
                onClick = {
                    // 发送数据
                    if (inputText.isNotEmpty()) {
                        onSendClick("!$scrollDirection,$inputText&")
                    }
                },
                modifier = Modifier.padding(16.dp)
            ) {
                Text(stringResource(R.string.send))
            }
        }
    }
}