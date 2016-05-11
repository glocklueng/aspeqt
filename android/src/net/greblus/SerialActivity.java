package net.greblus;

import java.lang.System;
import android.widget.Toast;
import android.os.Bundle;
import java.lang.String;
import java.lang.Thread;
import java.util.List;
import android.util.Log;

import org.qtproject.qt5.android.bindings.QtActivity;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbId;

import android.content.Context;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import java.util.HashMap;
import java.util.Iterator;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.widget.Toast;
import android.view.WindowManager;
import net.greblus.FT311UARTInterface;


public class SerialActivity extends QtActivity
{
        private static ByteBuffer bbuf = ByteBuffer.allocateDirect(4096);
        private static byte rb[] = new byte [4096];
        private static byte wb[] = new byte [4096];
        private static byte t[] = new byte [4096];
        private static int counter;
        public static native void sendBufAddr(ByteBuffer buf);
        private static boolean debug = true;
        public static String m_chosen;
        private static int m_filter;
        private static String m_action;
        private static FT311UARTInterface uartInterface;
        private static int tmt = 10;

        public static SerialActivity s_activity = null;

        @Override
	public void onCreate(Bundle savedInstanceState)
 	{
            s_activity = this;
            super.onCreate(savedInstanceState);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            sendBufAddr(bbuf);
            uartInterface = new FT311UARTInterface(this, null);

            int baudRate = 19200; /*baud rate*/
            byte stopBit = 1; /*1:1stop bits, 2:2 stop bits*/
            byte dataBit = 8; /*8:8bit, 7: 7bit*/
            byte parity = 0;  /* 0: none, 1: odd, 2: even, 3: mark, 4: space*/
            byte flowControl = 0; /*0:none, 1: flow control(CTS,RTS)*/
            uartInterface.SetConfig(baudRate, dataBit, stopBit, parity, flowControl);
        }

        @Override
        public void onPause() {
            m_chosen = "Cancelled";
            super.onPause();
        }


        public static void runFileChooser(int filter, int action) {
            m_chosen = "None";
            m_filter = filter;

            if (action == 0)
                m_action = "FileOpen";
            else
                m_action = "FileSave";

            SerialActivity.s_activity.runOnUiThread( new FileChooser() );

         }

         public static void runDirChooser() {
            m_chosen = "None";
            m_filter = 0;
            SerialActivity.s_activity.runOnUiThread( new DirChooser() );

          }

        @Override
	protected void onDestroy()
	{
                super.onDestroy();
                s_activity = null;
                closeDevice();
	}

        @Override
        protected void onResume()
        {
                super.onResume();
                uartInterface.ResumeAccessory();
        }


        public void fileChooser()
        {
            SimpleFileDialog FileOpenDialog =  new SimpleFileDialog(SerialActivity.this, m_action, m_filter,
                                                            new SimpleFileDialog.SimpleFileDialogListener()
            {
                    @Override
                    public void onChosenDir(String chosenDir)
                    {
                            // The code in this function will be executed when the dialog OK button is pushed
                            m_chosen = chosenDir;
                            if (m_chosen != "Cancelled") Toast.makeText(SerialActivity.this, "Chosen File: " +
                                            m_chosen, Toast.LENGTH_LONG).show();
                    }
            });

            //You can change the default filename using the public variable "Default_File_Name"
            FileOpenDialog.Default_File_Name = "";
            FileOpenDialog.chooseFile_or_Dir();
        }

        public void dirChooser()
        {
            SimpleFileDialog FileOpenDialog =  new SimpleFileDialog(SerialActivity.this, "FolderChoose", m_filter,
                                                            new SimpleFileDialog.SimpleFileDialogListener()
            {
                    @Override
                    public void onChosenDir(String chosenDir)
                    {
                            // The code in this function will be executed when the dialog OK button is pushed
                            m_chosen = chosenDir;
                            if (m_chosen != "Cancelled") Toast.makeText(SerialActivity.this, "Chosen Directory: " +
                                            m_chosen, Toast.LENGTH_LONG).show();
                    }
            });

            FileOpenDialog.chooseFile_or_Dir();
        }

        public static void registerBroadcastReceiver() {
                if (SerialActivity.s_activity != null) {
                        // Qt is running on a different thread than Android.
                        // In order to register the receiver we need to execute it in the UI thread
                        SerialActivity.s_activity.runOnUiThread( new RegisterReceiverRunnable());
                        Log.i("USB", "Broadcast Receiver registered");
                }
        }

        public static int openDevice() {
            uartInterface.ResumeAccessory();            
            return (uartInterface.accessory_attached ? 1 : 0);
       }

     public static void closeDevice() {
            uartInterface.DestroyAccessory(true);
     }

     public static int setSpeed(int speed) {

        byte stopBit = 1; /*1:1stop bits, 2:2 stop bits*/
        byte dataBit = 8; /*8:8bit, 7: 7bit*/
        byte parity = 0;  /* 0: none, 1: odd, 2: even, 3: mark, 4: space*/
        byte flowControl = 0; /*0:none, 1: flow control(CTS,RTS)*/

        uartInterface.SetConfig(speed, dataBit, stopBit, parity, flowControl);
        return speed;
     }

    public static int read(int size, int total)
    {
        bbuf.position(total);
        int ret = 0; int[] rd = new int[1];

        uartInterface.ReadData(size, rb, rd);
        try { Thread.sleep(tmt); }
	catch (Exception e) {}

        bbuf.put(rb, 0, rd[0]);
        return rd[0];
    }

    public static int write(int size, int total) {
        int ret = 0, wn = 0;
        bbuf.position(total);
        bbuf.get(wb, 0, size);

        uartInterface.SendData(size, wb);
        try { Thread.sleep(tmt); }
	catch (Exception e) {}
	
        return size;
    }

    public static boolean purge() {
        return true;
    }

    public static boolean purgeTX() {
        return true;
    }

    public static boolean purgeRX() {
        return true;
    }

    public static void qLog(String msg) {
        if (debug) Log.i("USB", msg);
    }

    public static int getModemStatus() {
        int ret = -2;
        return ret;
    }

    public static int getSWCommandFrame() {
        int expected = 0, sync_attempts = 0, got = 1, total_retries = 0;
        int ret = 0, total = 0;
        boolean prtl = false;

        bbuf.position(0);
        mainloop:
        while (true) {
            ret = 0; total = 0; total_retries = 0; int[] rd = new int[1];
            do {
                if (total_retries > 2) return 2;
                uartInterface.ReadData(5-total, rb, rd);
                try { Thread.sleep(tmt); }
                catch (Exception e) {}
                ret = rd[0];
                if (ret == 5) break;

                if ((ret > 0) && (ret < 5)) {
                    System.arraycopy(rb, 0, t, total, ret);
                    prtl = true;
                    total += ret;
                }

                if ((total == 5) && (prtl == true))
                        System.arraycopy(t, 0, rb, 0, 5);

                if (ret <= 0)
                    total_retries++;
            } while (total<5);

            expected = rb[4] & 0xff;
            got = sioChecksum(rb, 4) & 0xff;

            if (checkDesync(rb, got, expected) > 0) {
                if (debug) Log.i("USB", "Apparent desync");
                if (sync_attempts < 4) {
                        sync_attempts++;
                        for (int i = 0; i < 4; i++)
                                rb[i] = rb[i+1];
                        ret = 0;
                        do {
                            int[] red = new int[1];
                            uartInterface.ReadData(1, t, red);
                            try { Thread.sleep(tmt); }
                            catch (Exception e) {}
                            ret = red[0];
                        } while (ret < 1);
                        rb[4] = t[0];
                } else
                    continue mainloop;
            } else {
                if (debug) Log.i("USB", "No desync");

                for (int i=0; i<4; i++)
                   bbuf.put((byte)(rb[i] & 0xff));

                   if (debug) {
                       String data = "";
                       for (int i=0; i<4; i++)
                           data += Integer.toString(rb[i] & 0xff) + " ";
                       Log.i("USB", "Command Frame: " + data);
                   }
                break;
            }
         };
         if (debug) Log.i("USB", "got=" + got + " expected= " + expected);
         return 1;
    }

    public static int sioChecksum(byte[] data, int size)
    {
            int sum = 0;
            for (int i=0; i < size; i++) {

                sum += data[i] & 0xff;
                if (sum > 255) {
                    sum -= 255;
                }
            }
            return sum;
    }

    public static int checkDesync(byte[] cmd, int got, int expected)
    {
        if (got != expected)
            return 1;

        int ccom = (byte) cmd[1] & 0xff;

        int cdev = (byte) cmd[0] & 0xff;
        int cid = cmd[0] & 0xf0;

        if ((cid != 0x20) && (cid != 0x30) && (cid != 0x40) && (cid != 0x50) &&
            (cid != 0x60) && (cid != 0xf0))
             return 1;

        return 0;
    }
}

class RegisterReceiverRunnable implements Runnable
{
        // this method is called on Android Ui Thread
        @Override
        public void run() {
                IntentFilter filter = new IntentFilter();
                filter.addAction("com.android.example.USB_PERMISSION");
                // this method must be called on Android Ui Thread
                SerialActivity.s_activity.registerReceiver(new USBReceiver(), filter);
                }
}

class FileChooser implements Runnable
{
    @Override
    public void run() {
        SerialActivity.s_activity.fileChooser();
    }
}

class DirChooser implements Runnable
{
    @Override
    public void run() {
        SerialActivity.s_activity.dirChooser();
    }
}

class USBReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
        // Step 4
        if (intent.getAction().equals("com.android.example.USB_PERMISSION"))
        synchronized (this) {
            UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                Log.v("USB", "Received permission result OK");
                if(device != null){
                    Log.i("USB", "Device OK");
                }
                else {
                    Log.i("USB", "Permission denied for device " + device);
                }
            }
        }
    }
}
