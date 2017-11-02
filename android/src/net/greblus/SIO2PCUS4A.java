package net.greblus;
import org.qtproject.example.AspeQt.R;

import java.lang.System;
import android.widget.Toast;
import android.os.Bundle;
import java.lang.String;
import java.util.List;
import android.util.Log;

import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.util.HashMap;
import java.util.Iterator;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Context;
import android.content.BroadcastReceiver;
import java.io.IOException;
import android.widget.Toast;
import android.view.WindowManager;

public class SIO2PCUS4A implements SerialDevice
{
    private int devCount = 0;
    private int counter;
    private UsbDevice device = null;
    private UsbSerialDevice sPort = null;
    private static byte t1[] = new byte [1];
    private UsbManager manager;
    private PendingIntent pintent;
    private static final String ACTION_USB_PERMISSION =
                                    "com.android.example.USB_PERMISSION";

    private boolean debug = false;
    private SerialActivity sa = SerialActivity.s_activity;

    SIO2PCUS4A() {
        manager = (UsbManager)sa.getSystemService(Context.USB_SERVICE);
    }

    public int openDevice() {
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();

        if (!deviceIterator.hasNext()) {
            sa.runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(sa, sa.getResources().getString(R.string.sio2pc_not_attached), Toast.LENGTH_LONG).show();
                }
            });
            return 0;
        }

        int dev_pid, dev_vid;
        boolean dev_found = false;
        do {
            device = deviceIterator.next();
            dev_pid = device.getProductId();
            dev_vid = device.getVendorId();
            if ((dev_vid == 1027) &&
                (
                  (dev_pid == 24577) || //Lotharek's Sio2PC-USB
                  (dev_pid == 33712) || //Ray's Sio2USB-1050PC
                  (dev_pid == 33713) ||
                  (dev_pid == 24597)    //Ray's Sio2PC-USB
                )
            ) { dev_found = true; break; }
        } while (deviceIterator.hasNext());

        if (dev_found) {
            pintent = PendingIntent.getBroadcast(sa, 0, new Intent(ACTION_USB_PERMISSION), 0);
            manager.requestPermission(device, pintent);
        } else {
            sa.runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(sa, sa.getResources().getString(R.string.sio2pc_not_attached), Toast.LENGTH_LONG).show();
                }
            });
            return 0;
        }

        Log.i("USB", "Device found!");
        UsbDeviceConnection connection = manager.openDevice(device);
        sPort = UsbSerialDevice.createUsbSerialDevice(device, connection);        
        sPort.setBaudRate(19200);
        sPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
        sPort.setParity(UsbSerialInterface.PARITY_ODD);
        sPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
        sPort.syncOpen();
        return (sPort != null) ? 1 : 0;
    }

    public void closeDevice() {
       if (sPort != null)                        
            sPort.syncClose();
    }

    public int setSpeed(int speed) {
        sPort.setBaudRate(speed);
        return speed;
    }

    public int read(int size, int total)
    {
        sa.rbuf.position(total);
        int ret = 0, rd = 0;
        byte[] rb = new byte[size];
        do {
             rd = sPort.syncRead(rb, 5000);
             sa.rbuf.put(rb, 0, rd);
             size -= rd; ret += rd;
        } while (size > 0);
        return ret;
    }

    public int write(int size, int total) {
        int ret = 0, wn = 0;
        byte[] wb = new byte[size];
        sa.wbuf.position(total);
        sa.wbuf.get(wb, 0, size);

        do {
            wn = sPort.syncWrite(wb, 5000);
            size -= wn; ret += wn;
        } while (size > 0);
        return ret;
    }

    public boolean purge() {
        return true;
    }

    public boolean purgeTX() {
        return true;
    }

    public boolean purgeRX() {
        return true;
    }

    public void qLog(String msg) {
    if (debug) Log.i("USB", msg);
    }

    public int getModemStatus() {
        return -2;
    }

    public int getSWCommandFrame() {
    int expected = 0, sync_attempts = 0, got = 1, total_retries = 0;
    int ret = 0, total = 0;
    boolean prtl = false;

    sa.rbuf.position(0);
    byte[] rb = new byte[5];

    mainloop:
    while (true) {
        ret = 0; total = 0; total_retries = 0;
        do {
            if (total_retries > 2) return 2;
            ret = sPort.syncRead(rb, 5000);
            if (ret == 5) break;

            if ((ret > 0) && (ret < 5)) {
                System.arraycopy(rb, 0, sa.t, total, ret);
                prtl = true;
                total += ret;
            }
            if ((total == 5) && (prtl == true))
                    System.arraycopy(sa.t, 0, rb, 0, 5);
            if (ret <= 0)
                total_retries++;
        } while (total<5);

        expected = rb[4] & 0xff;
        got = sioChecksum(rb, 4) & 0xff;

        if (checkDesync(sa.rb, got, expected) > 0) {
            if (debug) Log.i("USB", "Apparent desync");
            if (sync_attempts < 10) {
                    sync_attempts++;
                    for (int i = 0; i < 4; i++)
                            sa.rb[i] = sa.rb[i+1];
                    ret = 0;
                    do {                        
                        ret = sPort.syncRead(t1, 5000);
                    } while (ret < 1);
                    rb[4] = t1[0];
            } else
                continue mainloop;
        } else {
            if (debug) Log.i("USB", "No desync");

            for (int i=0; i<4; i++)
               sa.rbuf.put((byte)(rb[i] & 0xff));

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

public int getHWCommandFrame(int mMethod) {
//    int mask, total_retries, status, total, res = 0;
//    boolean ret;

//    switch (mMethod) {
//        case 0:
//            mask = 64;
//            break;
//        case 1:
//            mask = 32;
//            break;
//        case 2:
//            mask = 16;
//            break;
//        default:
//            mask = 32; }

//    sa.rbuf.position(0);
//    do {
//        status = 0; total_retries = 0;
//        do {
//            if (total_retries > 10e2) return 2;
//            status = getModemStatus();
//            total_retries += 1;

//            if (status < 0) {
//                if (debug) Log.i("USB", "Cannot retrieve serial port status");
//                return 0;
//            }
//        } while (!((status & mask) > 0));

//        ret = purge();
//        if (!ret) if (debug) Log.i("USB", "Cannot clear serial port");

//        total = 0; total_retries = 0;
//        do {
//            res = 0;
//            try {
//                if (total_retries > 4) return 2;
//                res = sPort.sread(sa.rb, 5-total, 5000); }
//            catch (IOException e) {};

//            if (res > 0) {
//                for (int i=0; i<res; i++) {
//                   if (debug) Log.i("USB", "CF: " + (sa.rb[i] & 0xff));
//                   sa.rbuf.put((byte)(sa.rb[i] & 0xff));
//                   total += 1;
//                }
//            } else
//                total_retries++;
//        } while (total<5);

//        int expected = (byte) sa.rb[4] & 0xff;
//        int got = sioChecksum(sa.rb, 4);

//        if (expected != got) return 2;

//        total_retries = 0;
//        do {
//            if (total_retries > 10e2) return 2;
//            status = getModemStatus();
//            total_retries += 1;

//            if (status < 0) {
//                if (debug) Log.i("USB", "Cannot retrieve serial port status");
//                return 0;
//            }
//        } while ((status & mask) > 0);
//        break;
//     } while (true);
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

