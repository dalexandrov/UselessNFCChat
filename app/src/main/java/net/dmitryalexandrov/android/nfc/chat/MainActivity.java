package net.dmitryalexandrov.android.nfc.chat;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class MainActivity extends Activity {

    public static final String ERROR_DETECTED = "No NFC tag detected!";
    public static final String WRITE_SUCCESS = "Text sent to NFC device successfully!";
    public static final String EMPTY_MESSAGE = "Message is empty!";
    public static final String WRITE_ERROR = "Could not send the message, place the devices closer:";
    private NfcAdapter nfcAdapter;
    private PendingIntent pendingIntent;
    private IntentFilter writeTagFilters[];
    private boolean writeMode;
    private Tag newTag;
    private Context context;

    TextView tvNFCContent;
    TextView message;
    Button btnWrite;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = this;

        tvNFCContent = (TextView) findViewById(R.id.nfc_contents);
        message = (TextView) findViewById(R.id.edit_message);
        btnWrite = (Button) findViewById(R.id.button);

        btnWrite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (newTag == null) {
                    Toast.makeText(context, ERROR_DETECTED, Toast.LENGTH_LONG).show();
                }
                if (message == null || message.getText() == null || message.getText().length() == 0) {
                    Toast.makeText(context, EMPTY_MESSAGE, Toast.LENGTH_LONG).show();
                } else {
                    sendMessage(message.getText().toString(), newTag);
                }

            }
        });

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            Toast.makeText(this, "This device doesn't support NFC.", Toast.LENGTH_LONG).show();
            finish();
        }
        readFromIntent(getIntent());

        pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        tagDetected.addCategory(Intent.CATEGORY_DEFAULT);
        writeTagFilters = new IntentFilter[]{tagDetected};
    }

    private void readFromIntent(Intent intent) {
        String action = intent.getAction();
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            NdefMessage[] msgs = null;
            if (rawMsgs != null) {
                msgs = new NdefMessage[rawMsgs.length];
                for (int i = 0; i < rawMsgs.length; i++) {
                    msgs[i] = (NdefMessage) rawMsgs[i];
                }
                writeDataToTag(msgs);
            } else {
                Tag tagFromIntent = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                byte[] extraID = tagFromIntent.getId();

                StringBuilder sb = new StringBuilder();
                for (byte b : extraID) {
                    sb.append(String.format("%02X", b));
                }
                ;

                tvNFCContent.setText(tvNFCContent.getText() + "NFC card: " + sb.toString()+"\n");
            }

        }
    }

    private void writeDataToTag(NdefMessage[] msgs) {
        if (msgs == null || msgs.length == 0) return;

        String text = "";
//        String tagId = new String(msgs[0].getRecords()[0].getType());
        byte[] payload = msgs[0].getRecords()[0].getPayload();
        String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16"; // Get the Text Encoding
        int languageCodeLength = payload[0] & 0063; // Get the Language Code, e.g. "en"
        // String languageCode = new String(payload, 1, languageCodeLength, "US-ASCII");

        try {
            // Get the Text
            text = new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);


        } catch (UnsupportedEncodingException e) {
            Log.e("UnsupportedEncoding", e.toString());
        }

        tvNFCContent.setText(tvNFCContent.getText() + text+"\n");
    }


    private void sendMessage(String text, Tag tag) {
        try {
            NdefRecord[] records = {createRecord(text)};
            NdefMessage message = new NdefMessage(records);
            Ndef ndef = Ndef.get(tag);
            ndef.connect();
            ndef.writeNdefMessage(message);
            ndef.close();
            Toast.makeText(context, WRITE_SUCCESS, Toast.LENGTH_LONG).show();
        } catch (NullPointerException e) {
            Toast.makeText(context, WRITE_ERROR, Toast.LENGTH_LONG).show();
            Log.e("IOException", e.toString());
        }        catch (IOException e) {
            Toast.makeText(context, WRITE_ERROR, Toast.LENGTH_LONG).show();
            Log.e("IOException", e.toString());
        } catch (FormatException e) {
            Toast.makeText(context, WRITE_ERROR, Toast.LENGTH_LONG).show();
            Log.e("FormatException", e.toString());
        }

    }

    private NdefRecord createRecord(String text) throws UnsupportedEncodingException {
        text = Settings.Global.getString(context.getContentResolver(), "device_name")+": "+text;
        tvNFCContent.setText(tvNFCContent.getText() + text+"\n");
        String lang = "en";
        byte[] textBytes = text.getBytes();
        byte[] langBytes = lang.getBytes("US-ASCII");
        int langLength = langBytes.length;
        int textLength = textBytes.length;
        byte[] payload = new byte[1 + langLength + textLength];
        payload[0] = (byte) langLength;
        System.arraycopy(langBytes, 0, payload, 1, langLength);
        System.arraycopy(textBytes, 0, payload, 1 + langLength, textLength);

        NdefRecord recordNFC = new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, new byte[0], payload);

        return recordNFC;
    }


    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        readFromIntent(intent);
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
            newTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        WriteModeOff();
    }

    @Override
    public void onResume() {
        super.onResume();
        WriteModeOn();
    }

    private void WriteModeOn() {
        writeMode = true;
        nfcAdapter.enableForegroundDispatch(this, pendingIntent, writeTagFilters, null);
    }


    private void WriteModeOff() {
        writeMode = false;
        nfcAdapter.disableForegroundDispatch(this);
    }
}