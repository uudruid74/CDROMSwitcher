package systems.eddon.android.cdromswitcher;

import android.app.Activity;
import android.app.Application;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
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

    public final static String DefaultFileName  = "/storage/emulated/0/default.iso";

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
                messageHandler.postDelayed(finishIt,2000);
                //messageHandler.post(finishIt);
            } else {
                messageHandler.postDelayed(finishIt,5000);
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
                    Process p = Runtime.getRuntime().exec("su - root");
                    InputStream pi = p.getInputStream();
                    DataOutputStream po = new DataOutputStream(p.getOutputStream());
                    if (UsbEventReceiver.suwait(po, pi)) {
                        displayMessage("Open an ISO file from any app to mount it");
                    } else {
                        displayMessage("This app requires root");
                    }
                }
                catch (Exception e){
                    popupMessage("Sorry, but something went wrong: "
                            + e.getLocalizedMessage());
                }
            } else{
                displayMessage("Action is " + action + "\nURL is a null!  FAIL!");
            }
            return false;
        } else {
            displayMessage("URL: " + u.getEncodedPath());
        }
        String scheme = u.getScheme();
        displayMessage("Using scheme " + scheme);
        if (ContentResolver.SCHEME_FILE.equals(scheme) ||
                ((ContentResolver.SCHEME_CONTENT.equals(scheme))
                        && (u.getPath().startsWith("/")))) {
            String filePath = u.getPath();
            if (filePath.startsWith("/"))
                filePath = u.getEncodedPath().replaceAll("%20", " ")
                        .replaceAll("%26","&")
                        .replaceAll("&amp;", "&");
            if (filePath == null) {
                displayMessage("The path to the file was a null - FAIL!");
                return false;
            }
            displayMessage("Using filename: " + filePath);
            s = filePath;
        } else if (ContentResolver.SCHEME_CONTENT.equals(scheme)) {
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
                String Error = "Couldn't read data from app - FAIL due to "
                        + err.getLocalizedMessage();
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
            s = DefaultFileName;
            displayMessage("Unhandled Intent");
            popupMessage("I don't know how to get that info!");
        }
        writeToPreferences(s);
        return true;
    }

    private void writeToPreferences(final String file)
    {
        SharedPreferences.Editor settings = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit();
        settings.putString("defaultiso",file).apply();
        UsbEventReceiver.writeToSystem(file);
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