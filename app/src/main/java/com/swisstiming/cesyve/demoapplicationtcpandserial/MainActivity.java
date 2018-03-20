package com.swisstiming.cesyve.demoapplicationtcpandserial;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.util.Set;

public class MainActivity extends AppCompatActivity
{

    //......................................................................
    // Constructor(s)
    //......................................................................

    //......................................................................
    // Public functions:
    //......................................................................
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
        this.myIsConnected = false;
    }

    @Override
    public void onResume()
    {
        super.onResume();
        setFilters();  // Start listening notifications from UsbService
        startService(UsbSerial.class, usbConnection, null); // Start UsbService(if it was not started before) and Bind it
    }

    @Override
    public void onPause()
    {
        super.onPause();
        unregisterReceiver(mUsbReceiver);
        unbindService(usbConnection);
    }

    //......................................................................
    // Private functions:
    //......................................................................
    private void init()
    {
        // Variables initialisation:
        usbHandler = new MyHandler(this);

        // Txt:
        txtSerial = findViewById(R.id.textSerial);
        txtSerial.setText("Serial connexion:");
        txtSerial.setTextColor(Color.RED);
        txtSerial.setTextSize(20);

        txtTCP = findViewById(R.id.textTCP);
        txtTCP.setText("TCP Connection:");
        txtTCP.setTextColor(Color.RED);
        txtTCP.setTextSize(20);

        txtLog  = findViewById(R.id.textLog);
        txtLog.setText("");
        txtLog.setTextSize(14);
        txtLog.setTextColor(Color.BLACK);

        scrollViewLog = findViewById(R.id.scrollView);
        txtLog.setOnLongClickListener(new View.OnLongClickListener()
        {
            @Override
            public boolean onLongClick(View view)
            {
                txtLog.setText("");
                return false;
            }
        });

        // EditViews:
        etSerialBaud = findViewById(R.id.serialBaud);
        etSerialBaud.setText("460800");

        etSerialPort = findViewById(R.id.serialPort);
        etSerialPort.setText("2");

        etTcpIP = findViewById(R.id.tcpIP);
        etTcpIP.setText("176.128.187.69");

        etTcpPort = findViewById(R.id.tcpPort);
        etTcpPort.setText("22000");

        // Buttons:
        openSerial = findViewById(R.id.openSerial);
        openSerial.setText("Open Serial");
        openSerial.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {


            }
        });

        openTCP = findViewById(R.id.openTCP);
        openTCP.setText("Open TCP");
        openTCP.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                if(myIsConnected)
                {
                    // Disconnect
                    myIsConnected = false;
                    etTcpIP.setEnabled(true);
                    etTcpPort.setEnabled(true);
                    openTCP.setText("Open TCP");
                    if (myTcpClient != null)
                    {
                        myTcpClient.stopClient();
                    }
                }
                else
                {
                    // Try to connect
                    myIsConnected = true;
                    etTcpIP.setEnabled(false);
                    etTcpPort.setEnabled(false);
                    openTCP.setText("Close TCP");
                    new ConnectTask().execute("");
                }
            }
        });

    }

    private void startService(Class<?> service, ServiceConnection serviceConnection, Bundle extras)
    {
        if (!UsbSerial.SERVICE_CONNECTED)
        {
            Intent startService = new Intent(this, service);
            if (extras != null && !extras.isEmpty())
            {
                Set<String> keys = extras.keySet();
                for (String key : keys)
                {
                    String extra = extras.getString(key);
                    startService.putExtra(key, extra);
                }
            }
            startService(startService);
        }
        Intent bindingIntent = new Intent(this, service);
        bindService(bindingIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void setFilters()
    {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbSerial.ACTION_USB_PERMISSION_GRANTED);
        filter.addAction(UsbSerial.ACTION_NO_USB);
        filter.addAction(UsbSerial.ACTION_USB_DISCONNECTED);
        filter.addAction(UsbSerial.ACTION_USB_NOT_SUPPORTED);
        filter.addAction(UsbSerial.ACTION_USB_PERMISSION_NOT_GRANTED);
        registerReceiver(mUsbReceiver, filter);
    }

    private final ServiceConnection usbConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            usbSerial = ((UsbSerial.UsbBinder) arg1).getService();
            usbSerial.setHandler(usbHandler);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0)
        {
            usbSerial = null;
        }
    };


    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case UsbSerial.ACTION_USB_PERMISSION_GRANTED: // USB PERMISSION GRANTED
                    Toast.makeText(context, "USB Ready", Toast.LENGTH_SHORT).show();
                    break;
                case UsbSerial.ACTION_USB_PERMISSION_NOT_GRANTED: // USB PERMISSION NOT GRANTED
                    Toast.makeText(context, "USB Permission not granted", Toast.LENGTH_SHORT).show();
                    break;
                case UsbSerial.ACTION_NO_USB: // NO USB CONNECTED
                    Toast.makeText(context, "No USB connected", Toast.LENGTH_SHORT).show();
                    break;
                case UsbSerial.ACTION_USB_DISCONNECTED: // USB DISCONNECTED
                    Toast.makeText(context, "USB disconnected", Toast.LENGTH_SHORT).show();
                    break;
                case UsbSerial.ACTION_USB_NOT_SUPPORTED: // USB NOT SUPPORTED
                    Toast.makeText(context, "USB device not supported", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    private static class MyHandler extends Handler
    {
        private final WeakReference<MainActivity> mActivity;

        public MyHandler(MainActivity activity)
        {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UsbSerial.MESSAGE_FROM_SERIAL_PORT:
                    String data = (String) msg.obj;

                    if(mActivity.get().myIsConnected)
                    {
                        //mActivity.get().myTcpClient.sendMessage(new String(data));
                        mActivity.get().myTcpClient.sendMessage(String.format("%02x\n", new BigInteger(1, data.getBytes())));
                    }
                    mActivity.get().txtLog.append(String.format(" % 02x\n", new BigInteger(1, data.getBytes())));
                    mActivity.get().scrollViewLog.fullScroll(ScrollView.FOCUS_DOWN);
                    Log.i("Received from USB", data);
                    break;
            }
        }
    }

    public class ConnectTask extends AsyncTask<String, String, TcpClient>
    {

        @Override
        protected TcpClient doInBackground(String... message) {

            //we create a TCPClient object
            myTcpClient = new TcpClient(new TcpClient.OnMessageReceived() {
                @Override
                //here the messageReceived method is implemented
                public void messageReceived(String message) {
                    //this method calls the onProgressUpdate
                    publishProgress(message);
                }
            });
            myTcpClient.run();

            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);
            //response received from server
            Log.d("test", "response " + values[0]);
        }
    }

    //......................................................................
    // Class variables:
    //......................................................................
    private Button openSerial;
    private Button openTCP;
    private EditText etTcpIP;
    private EditText etTcpPort;
    private EditText etSerialBaud;
    private EditText etSerialPort;
    private TextView txtSerial;
    private TextView txtTCP;
    private TextView txtLog;
    private ScrollView scrollViewLog;

    private UsbSerial usbSerial;
    private MyHandler usbHandler;

    private TcpClient myTcpClient;
    private boolean myIsConnected;
}
