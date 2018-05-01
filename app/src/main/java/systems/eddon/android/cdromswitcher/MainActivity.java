package systems.eddon.android.cdromswitcher;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v13.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;

public class MainActivity extends Activity {
    static Handler messageHandler = new Handler();
    static TextView tv = null;
    static ArrayList<String> storage = new ArrayList<String>();
    static Context myContext = null;

    public final static String DefaultFileName  = "/storage/emulated/0/default.iso";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myContext = getApplicationContext();
        new Thread(threadIntent).start();
    }

    public String popupMessage(final String errorText) {
        Runnable doPopupMessage = new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), errorText, Toast.LENGTH_SHORT).show();
            }
        };
        Log.d("PopUP", errorText);
        messageHandler.post(doPopupMessage);
        return errorText;
    }

    public void displayMessage(final String dispText) {
        Runnable doDisplayMessage = new Runnable() {
            public void run() {
                tv.append(dispText);
                tv.append("\n");
            }
        };
        Log.d("TextDisplay", dispText);
        if (tv != null)
            messageHandler.post(doDisplayMessage);
        else {
            storage.add(dispText);
        }
    }
    public void displayError(final String dispText) {
        Runnable startFullDisplay = new Runnable() {
            public void run() {
                Log.i("DisplayError","Opening the full window for " + dispText);
                setContentView(R.layout.layout);
                tv = findViewById(R.id.textView);
                tv.setMovementMethod(new ScrollingMovementMethod());
                for (String item: storage) {
                    tv.append(item);
                    tv.append("\n");
                }
                tv.append(dispText);
                tv.append("\n");
            }
        };
        if (tv == null) {
            Log.e("displayError",dispText);
            messageHandler.post(startFullDisplay);
        } else {
            displayMessage(dispText);
        }
    }

    private Runnable finishIt = new Runnable() {
        public void run() {
            finish();
        }
    };

    private Runnable threadIntent = new Runnable() {
        public void run() {
            if (ImportIntent()) {
                popupMessage("Mounted " + UsbEventReceiver.readISOName(
                        UsbEventReceiver.getISOFile(myContext)
                ));
                messageHandler.post(finishIt);
                //messageHandler.postDelayed(finishIt,2000);
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
            displayError("Intent is a null!  Sorry, but I failed!");
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
                displayError("We can't handle streams!  FAIL!");
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
                        displayError("Open an ISO file from any app to mount it");
                    } else {
                        displayError("This app requires root");
                    }
                    if (ContextCompat.checkSelfPermission(this,Manifest.permission.READ_EXTERNAL_STORAGE)
                            != PackageManager.PERMISSION_GRANTED) {
                        displayError("Storage permission is needed to read the ISO disc name!");
                        ActivityCompat.requestPermissions(this,
                                new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},
                                1);
                    }
                }
                catch (Exception e){
                    displayError(
                        popupMessage("Sorry, but something went wrong: "
                            + e.getLocalizedMessage()));
                }
            } else{
                displayError("Action is " + action + "\nURL is a null!  FAIL!");
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
                displayError("The path to the file was a null - FAIL!");
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
                    displayError("Couldn't open inputStream!  FAIL!");
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
                    displayError("Couldn't open inputStream.  FAIL!");
                    return false;
                }
                displayMessage("Copying from URL ...");
                if (copyToRootFile(is, DefaultFileName))
                    s = DefaultFileName;
                else
                    s = null;
            } catch (IOException err) {
                displayError(
                        popupMessage(
                                "Couldn't read URL - FAIL due to " + err.getLocalizedMessage()));
                return false;
            }
        } else {
            s = DefaultFileName;
            displayError(
                popupMessage("Unhandled Intent.  I don't know how to get that info!"));
        }
        writeToPreferences(s);
        return true;
    }

    private void writeToPreferences(final String file)
    {
        SharedPreferences.Editor settings = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit();
        settings.putString(UsbEventReceiver.PREF_ISONAME,file).apply();
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
            displayError(popupMessage("FAIL! Sorry, " + e.getLocalizedMessage()));
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
            displayError(popupMessage("FAIL! Couldn't copy data due to "
                    + err.getLocalizedMessage()));
            return false;
        }
    }
}