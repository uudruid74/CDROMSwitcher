package systems.eddon.android.cdromswitcher;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class MainActivity extends Activity {
    static final int BUFSIZE = 65536;
    Handler messageHandler = new Handler();
    final String SystemBaseDevice = "/sys/class/android_usb/android0/";
    final String OSDefaultCDFile  = "/system/etc/usb_drivers.iso";
    final String SystemInterface  = "/sys/class/android_usb/android0/f_mass_storage/cdrom/";
    final String DefaultFileName  = "/storage/emulated/0/usb_drivers.iso";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout);
        TextView tv = (TextView) findViewById(R.id.textView);
        tv.setMovementMethod(new ScrollingMovementMethod());
        new Thread(threadIntent).start();
    }

    public void popupMessage(final String errorText) {
        Runnable doPopupMessage = new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), errorText, Toast.LENGTH_SHORT).show();
            }
        };
        Log.d("PopUP", errorText);
        messageHandler.post(doPopupMessage);
    }

    public void displayMessage(final String dispText) {
        Runnable doDisplayMessage = new Runnable() {
            public void run() {
                TextView tv = (TextView) findViewById(R.id.textView);
                String content = tv.getText().toString() + "\n" + dispText;
                tv.setText(content);
            }
        };
        Log.d("TextDisplay", dispText);
        messageHandler.post(doDisplayMessage);
    }

    private Runnable finishIt = new Runnable() {
        public void run() {
            finish();
        }
    };

    private Runnable threadIntent = new Runnable() {
        public void run() {
            if (ImportIntent()) {
                //messageHandler.postDelayed(finishIt,500);
                messageHandler.post(finishIt);
            } else {
                messageHandler.postDelayed(finishIt,8000);
            }
        }
    };

    private boolean ImportIntent () {
        InputStream is;
        Intent i = getIntent();
        //displayMessage("Importing Data Using Intent");
        if (i == null) {
            displayMessage("Intent is a null!  Sorry, but I failed!");
            return false;
        }
        String action = i.getAction();
        String s = "";
        Uri u = null;
        if (action.equalsIgnoreCase(Intent.ACTION_SEND)) {
            if (i.hasExtra(Intent.EXTRA_TEXT)) {
                s = i.getStringExtra(Intent.EXTRA_TEXT);
                u = Uri.parse(s);
            }
            if (i.hasExtra(Intent.EXTRA_STREAM)) {
                displayMessage("We can't handle streams!  FAIL!");
                u = (Uri) getIntent().getExtras().get(Intent.EXTRA_STREAM);
            }
            if (u == null)
                u = i.getData();
            if (u != null)
                displayMessage("Data is: " + u.toString());
        } else
            u = i.getData();

        if (u == null) {
            if (action.equals("android.intent.action.MAIN")) {
                try {
                    Process p = Runtime.getRuntime().exec("su");
                    InputStream pi = p.getInputStream();
                    DataOutputStream po = new DataOutputStream(p.getOutputStream());
                    if (suwait(po, pi)) {
                        displayMessage("Thanks! Now open an ISO file");
                        return true;
                    }
                    else
                        displayMessage("Sorry!  I need root!");
                }
                catch (Exception e) {}
            } else{
                displayMessage("Action is " + action + "\nURL is a null!  FAIL!");
            }
            return false;
        } else {
            displayMessage("  to URL " + u.getEncodedPath());
        }
        String scheme = u.getScheme();
        //displayMessage("Using scheme " + scheme);

        if (ContentResolver.SCHEME_CONTENT.equals(scheme)) {
            try {
                ContentResolver cr = getContentResolver();
                //AssetFileDescriptor afd = cr.openAssetFileDescriptor(u, "r");
                //long length = afd.getLength();
                //byte[] filedata = new byte[(int) length];
                is = cr.openInputStream(u);
                if (is == null) {
                    displayMessage("Couldn't open inputStream!  FAIL!");
                    return false;
                }
                displayMessage("Attempting to read image from app");
                if (copyToRootFile(is, DefaultFileName))
                    s = DefaultFileName;
                else
                    s = null;
            } catch (IOException err) {
                String Error = "Couldn't read data from app - FAIL due to " + err.getLocalizedMessage();
                displayMessage(Error);
                popupMessage(Error);
                return false;
            }
        } else if ((scheme != null) && (scheme.contains("http"))) {
            try {
                URL website = new URL(u.toString());
                //URLConnection urlConn = website.openConnection();
                //is = urlConn.getInputStream();
                is = website.openStream();
                if (is == null) {
                    displayMessage("Couldn't open inputStream.  FAIL!");
                    return false;
                }
                displayMessage("Copying from URL ...");
                if (copyToRootFile(is, DefaultFileName))
                    s = DefaultFileName;
                else
                    s = null;
            } catch (IOException err) {
                String Error = "Couldn't read URL - FAIL due to " + err.getLocalizedMessage();
                displayMessage(Error);
                popupMessage(Error);
                return false;
            }

        } else {
            String filePath = u.getPath();
            /* filePath = u.getEncodedPath().replaceAll("%20", " ")
                        .replaceAll("%26","&")
                        .replaceAll("&amp;", "&"); */
            if (filePath == null) {
                displayMessage("The path to the file was a null - FAIL!");
                return false;
            }
            //displayMessage("Using filename: " + filePath);
            s = filePath;
        }
        return writeToSystem(SystemInterface, s);
    }

    private boolean writeToSystem(final String system, final String file) {
        Process p;
        try{
            // Preform su to get root
            // OLD WAY
            /*
            byte[] buf = new byte[1024];
            p = Runtime.getRuntime().exec(new String[] { "su", "-c", "blkid " + file });
            InputStream pi = p.getInputStream();
            p.waitFor();
            pi.read(buf,0,1024);
            displayMessage("result: " + new String(buf));
            Arrays.fill(buf, (byte)0);
            p = Runtime.getRuntime().exec(new String[] { "su", "-c", "echo " + file + " > " + system });
            p.waitFor();
            p = Runtime.getRuntime().exec(new String[] { "su", "-c", "cat " + system });
            pi = p.getInputStream();
            p.waitFor();
            pi.read(buf,0,1024);
            String result = new String(buf).trim();
            displayMessage("New ISO is set to " + result);
            if (! file.equals(result)) {
                displayMessage("FAIL! Make sure your USB is unplugged and try again!");
                return false;
            }
            return true;
            */
            p = Runtime.getRuntime().exec("su - root");
            InputStream pi = p.getInputStream();
            DataOutputStream po = new DataOutputStream(p.getOutputStream());
            if (suwait(po,pi)) {
                powrite(po, pi, ("echo 0 >" + SystemBaseDevice + "enable\n"));
                powrite(po, pi, ("busybox mount -o remount,rw /system\n"));
                powrite(po, pi, ("setprop sys.usb.cdrom \"" + file + "\"\n"));
                powrite(po, pi, ("if [ ! -e \"" + OSDefaultCDFile + ".orig.iso\" ]; then\nmv "
                        + OSDefaultCDFile + " " + OSDefaultCDFile + ".orig.iso\nfi\nrm "
                        + OSDefaultCDFile + "\n"));
                powrite(po, null, ("cat " + SystemInterface + "file\n"));
                powrite(po, pi, ("ln -s \"" + file + "\" " + OSDefaultCDFile + "\n"));
                powrite(po, pi, ("busybox mount -o remount,ro /system\n"));
                powrite(po, pi, ("echo 1 >" + SystemBaseDevice + "enable\n"));
                powrite(po, pi, ("while [ ! -e " + SystemInterface + "file ]; do\nsleep 1\ndone\n"));
                powrite(po, pi, ("echo \"" + file +"\" > " + SystemInterface + "file\n"));
                displayMessage("All DONE!  SUCCESS!");
                return true;
            } else {
                displayMessage("FAIL! Couldn't get root!");
                return false;
            }
        }
        catch (Exception e)
        {
            String Error = "FAIL! Sorry, " + e.getLocalizedMessage();
            displayMessage(Error);
            popupMessage(Error);
            return false;
        }
    }
    private void powrite (DataOutputStream po, InputStream pi, String s) throws java.io.IOException, java.lang.InterruptedException {
        //displayMessage(s.trim());
        po.writeBytes(s);
        po.flush();

        if (pi != null) {
            if (pi.available() > 0) {
                byte[] buf = new byte[pi.available()];
                pi.read(buf);
                String result = new String(buf).trim();
                if (result.length() > 0) {
                    displayMessage(result);
                }
            }
        }
    }
    private boolean suwait (DataOutputStream po, InputStream pi) throws java.io.IOException, java.lang.InterruptedException {
        displayMessage("Aquiring root!");
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
                displayMessage("I am " + result);
                if (result.equals("root"))
                    return true;
            }
        }
        return false;
    }
    private boolean copyToRootFile(InputStream is, String FileName)
    {
        Process p;
        try{
            // Preform su to get root
            p = Runtime.getRuntime().exec(new String[] { "su", "-c", "cat >" + FileName +
                    " && chmod 777 " + FileName });

            // Attempt to write a file to a root-only
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            return CopyStream (is,os);
        }
        catch (Exception e)
        {
            String Error = "FAIL! Sorry, " + e.getLocalizedMessage();
            displayMessage(Error);
            popupMessage(Error);
            return false;
        }
    }

    public boolean CopyStream(InputStream in, OutputStream os) {
        try {
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                os.write(buf, 0, len);
            }
            in.close();
            os.close();
            return true;
        }
        catch (Exception err) {
            String Error = "FAIL! Couldn't copy data due to "
                    + err.getLocalizedMessage();
            displayMessage(Error);
            popupMessage(Error);
            return false;
        }
    }
}