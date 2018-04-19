package systems.eddon.android.cdromswitcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;

public class UsbEventReceiver extends BroadcastReceiver {
    final int ISO_MAX_LEN = 40;
    final static String usbStateChangeAction = "android.hardware.usb.action.USB_DEVICE_ATTACHED";
    final static String SystemBaseDevice = "/sys/class/android_usb/android0/";
    final static String SystemInterface  = SystemBaseDevice + "f_mass_storage/lun/";
    static Context savedContext = null;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i("onReceive","Got a USB ATTACH event!");
        savedContext = context;
        if(intent.getAction().equalsIgnoreCase(usbStateChangeAction)) { //Check if change in USB state
            if(intent.getExtras().getBoolean("connected")) {
                final String ISOFilename = getISOFile();
                writeToSystem(ISOFilename);
                // need to create notification here, display ISO name
                Log.i("onReceive",readISOName(ISOFilename));
            } else {
                removeCD();
                // remove notification here
            }
        }
    }
    private String readISOName(final String file) {
        byte[] magic = new byte[ISO_MAX_LEN];
        try {
            RandomAccessFile raf = new RandomAccessFile(file, "rw");
            raf.seek(32808);
            raf.readFully(magic);
            return magic.toString();
        }
        catch (java.io.IOException e) {
            Log.e("readISOName", e.getLocalizedMessage());
            return "Unknown ISO File";
        }
    }
    private String getISOFile() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(savedContext);
        return settings.getString("defaultiso",MainActivity.DefaultFileName);
    }

    public static boolean writeToSystem(final String file) {
        Process p;
        Log.i("writeToSystem","Starting root commands");
        try{
            p = Runtime.getRuntime().exec("su - root");
            InputStream pi = p.getInputStream();
            DataOutputStream po = new DataOutputStream(p.getOutputStream());
            if (suwait(po,pi)) {
                String origfuncs = powrite(po, pi, "cat " + SystemBaseDevice + "functions\n");
                powrite(po, pi, ("echo 0 >" + SystemBaseDevice + "enable\n"));
                Thread.sleep(1000);
                if (!origfuncs.contains("mass_storage")) {
                    powrite(po, pi, ("echo \"mass_storage," + origfuncs + "\" >" +
                            SystemBaseDevice + "functions\n"));
                }
                // Log.i("writeToSystem","About to start sleep loop");
                powrite(po, pi, ("echo \'" + file +"\' >" + SystemInterface + "file\n"));
                String newfile = powrite(po, pi, ("cat " + SystemInterface + "file\n"));
                powrite(po, pi, ("echo 1 >" + SystemBaseDevice + "enable\n"));
                Log.i("writeToSystem","All DONE!  SUCCESS!");
                return true;
            } else {
                Log.e("writeToSystem","FAIL! Couldn't get root!");
                return false;
            }
        }
        catch (Exception e)
        {
            String Error = "FAIL! Sorry, " + e.getLocalizedMessage();
            Log.e("writeToSystem",Error);
            return false;
        }
    }
    private boolean removeCD() {
        Process p;
        try{
            p = Runtime.getRuntime().exec("su - root");
            InputStream pi = p.getInputStream();
            DataOutputStream po = new DataOutputStream(p.getOutputStream());
            if (suwait(po,pi)) {
                powrite(po, pi, ("echo 0 >" + SystemBaseDevice + "enable\n"));
                Log.i("removeCD","All DONE!  SUCCESS!");
                return true;
            } else {
                Log.e("removeCD","FAIL! Couldn't get root!");
                return false;
            }
        }
        catch (Exception e)
        {
            String Error = "FAIL! Sorry, " + e.getLocalizedMessage();
            Log.e("removeCD",Error);
            return false;
        }
    }
    public static String powrite (DataOutputStream po, InputStream pi, String s)
            throws java.io.IOException, java.lang.InterruptedException {
        Log.d("powrite-send",s.trim());
        po.writeBytes(s);
        Thread.sleep(100);
        po.flush();

        if (pi != null) {
            Thread.sleep(100);
            if (pi.available() == 0) {
                return null;
            }
            byte[] buf = new byte[pi.available()];
            pi.read(buf);
            String result = new String(buf).trim();
            if (result.length() > 0) {
                Log.i("powrite#",result);
                return result;
            }
        } else Log.d("powrite", "pi is null");
        return null;
    }

    public static boolean suwait (DataOutputStream po, InputStream pi) throws java.io.IOException, java.lang.InterruptedException {
        Log.i("suwait","Aquiring root!");
        po.writeBytes("whoami\n");
        po.flush();
        int seconds = 0;
        while ((pi.available() == 0) && (seconds++ < 20)){
            Thread.sleep(1000);
        }
        if (pi.available() > 0) {
            byte[] buf = new byte[pi.available()];
            pi.read(buf);
            String result = new String(buf).trim();
            if (result.length() > 0) {
                Log.i("suwait","I am " + result);
                if (result.equals("root"))
                    return true;
            }
        }
        return false;
    }
}
