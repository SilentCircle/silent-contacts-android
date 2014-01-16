/*
Copyright © 2013-2014, Silent Circle, LLC.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Any redistribution, use, or modification is done solely for personal 
      benefit and not for any commercial purpose or for monetary gain
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name Silent Circle nor the names of its contributors may 
      be used to endorse or promote products derived from this software 
      without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL SILENT CIRCLE, LLC BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.silentcircle.keymngr;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.silentcircle.contacts.R;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Set;

public class KeyManagerActivity extends SherlockFragmentActivity {

    static private String TAG = "KeyManagerActivity";
    static private String KEY_CHAIN_READY = "com.silentcircle.keymngr.action.READY";

    private static final int KEY_STORE_READY = 1;
    private static final int KEY_STORE_FAILED = 2;
    private static final int UPDATE_APP_REGISTERED = 3;

    private KeyService keyService;

    private EditText passwordInput;
    private EditText passwordInput2;
    private EditText passwordInputOld;
    private TextView pwStrength;
    private CheckBox passwordShow;

    private ActionBar actionBar;

    private ListView listView;
    private ArrayAdapter<String> registeredApps;

    private Button lockUnlock;

    private PasswordFilter pwFilter = new PasswordFilter();

    private boolean readyIntent;
    private boolean lockedDuringPwChange;

    private boolean storeCreation;

    private boolean mExternalStorageAvailable;
    private boolean mExternalStorageWriteable;

    private UnlockClick unlockListener = new UnlockClick();
    private LockClick lockListener = new LockClick();
    /**
     * Internal handler to receive and process key store state messages.
     */
    private static InternalHandler mHandler = null;

    // Code Section that handles the KeyService creation and binding
    private ServiceConnection keyConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service. Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            keyService = ((KeyService.LocalBinder) service).getService();
            serviceBound();
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            keyService = null;
        }
    };

    private void doBindService() {
        // Establish a connection with the service. We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        bindService(new Intent(KeyManagerActivity.this, KeyService.class), keyConnection, Context.BIND_AUTO_CREATE);
    }
    private void doUnbindService() {
        if (keyService != null) {
            // Detach our existing connection.
            unbindService(keyConnection);
        }
    }

    /*
     * The lifecycle functions
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PRNGFixes.apply();
        mHandler = new InternalHandler(this);
        Intent intent = new Intent(this, KeyService.class);
        startService(intent);
        doBindService();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getSupportMenuInflater().inflate(R.menu.key_manager, menu);
        return true;
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        // enable password changing only if key store is open, i.e. correct password entered
        menu.findItem(R.id.change_password).setVisible(KeyService.isReady());
        menu.findItem(R.id.backup_store).setVisible(mExternalStorageWriteable);
        menu.findItem(R.id.restore_store).setVisible(mExternalStorageAvailable);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);

        switch (item.getItemId()) {
            case R.id.key_manager_help:
                showKeyManagerInfo(R.string.info_about_key_manager);
                break;

            case R.id.change_password:
                storeCreation = true;       // PW change is similar to key store creation
                changePassword();
                break;

            case R.id.backup_store: {
                final File file = Environment.getExternalStorageDirectory();
                if (!file.exists() || !file.isDirectory() || !file.canWrite()) {
                    showInputInfo(getString(R.string.backup_access));
                    return true;
                }
                final File backupFile = new File(file, "sc_keymngrdb.scsave");
                if (!KeyStoreDatabase.backupStoreTo(backupFile)) {           // Should this go to a async task?
                    showInputInfo(getString(R.string.backup_failed));
                    return true;
                }
                Toast.makeText(this, getString(R.string.backup_created), Toast.LENGTH_LONG).show();
                break;
            }
            case R.id.restore_store: {
                final File file = new File(Environment.getExternalStorageDirectory(), "sc_keymngrdb.scsave");
                if (!file.exists() || !file.canRead()) {
                    showInputInfo(getString(R.string.restore_access));
                    return true;
                }
                // User selected to restore the database instead of creating a new one
                // Remove the creation specific setting, then restore and show the normal
                // password screen.
                if (storeCreation) {
                    passwordInput.removeTextChangedListener(pwFilter);
                    passwordInput2.setVisibility(View.GONE);
                    pwStrength.setVisibility(View.GONE);
                    listView.setVisibility(View.VISIBLE);
                    storeCreation = false;
                }
                else {                              // restore an existing, open DB, thus overwriting it
                    keyService.closeDatabase();
                    ProviderDbBackend.sendLockRequests();
                }
                if (!KeyStoreDatabase.restoreStoreFrom(file)) {
                    showInputInfo(getString(R.string.restore_failed));
                    return true;
                }
                showNormalScreen();
                Toast.makeText(this, getString(R.string.restore_created), Toast.LENGTH_LONG).show();
                break;
            }
        }
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (keyService == null)
            return;

        showNormalScreen();
        updateListOfApps();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doUnbindService();
    }

    @Override
    protected void onNewIntent (Intent intent) {
        processIntent(intent);
    }

    private void serviceBound() {
        processIntent(getIntent());
    }

    // This is called after the KeyService was bound, thus it's save to use KeyService functions
    private void processIntent(Intent intent) {
        if (intent == null) {
            finish();
            return;
        }
        String action = intent.getAction();
        readyIntent = KEY_CHAIN_READY.equals(action);

        // If we got a KEY_CHAIN_READY and the KeyService is ready: everything OK.
        if (readyIntent && KeyService.isReady()) {
            setResult(RESULT_OK);
            finish();
            return;
        }
        setupActionBar();
        setContentView(R.layout.key_manager_activity);
        lockUnlock = (Button)findViewById(R.id.lockUnlock);
        passwordInput = (EditText) findViewById(R.id.passwordInput);
        passwordShow = (CheckBox)findViewById((R.id.passwordShow));
        listView = (ListView)findViewById(R.id.listView);

        TextView txt = new TextView(this);
        txt.setText(getString(R.string.registered_apps));

        listView.addHeaderView(txt);
        registeredApps = new ArrayAdapter(this, android.R.layout.simple_list_item_1);
        listView.setAdapter(registeredApps);
        updateListOfApps();

        if (!KeyStoreDatabase.isDbFileAvailable()) {
            updateExternalStorageState();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                invalidateOptionsMenu();
            }
            storeCreation = true;
            createKeyStore();
            showKeyManagerInfo(R.string.info_about_key_manager);
            return;
        }
        showNormalScreen();
    }

    private void setupActionBar() {
        actionBar = getSupportActionBar();
        if (actionBar == null)
            return;
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setHomeButtonEnabled(false);

        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
    }

    private void createKeyStore() {
        passwordInput.addTextChangedListener(pwFilter);
        passwordInput.requestFocus();
        passwordInput2 = (EditText) findViewById(R.id.passwordInput2);
        passwordInput2.setVisibility(View.VISIBLE);
        pwStrength = (TextView) findViewById(R.id.passwordStrength);
        pwStrength.setVisibility(View.VISIBLE);
        passwordShow.setVisibility(View.VISIBLE);
        lockUnlock.setText(R.string.create);
        listView.setVisibility(View.INVISIBLE);

        lockUnlock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (checkPassword(passwordInput.getText(), passwordInput2.getText())) {
                    if (keyService.openOrCreateDatabase(passwordInput.getText())) {
                        passwordInput.removeTextChangedListener(pwFilter);
                        passwordInput2.setVisibility(View.GONE);
                        pwStrength.setVisibility(View.GONE);
                        listView.setVisibility(View.VISIBLE);
                        storeCreation = false;
                        showNormalScreen();
                        ProviderDbBackend.sendUnlockRequests();
                        mHandler.sendEmptyMessage(KEY_STORE_READY);
                    }
                    else {
                        showInputInfo(getString(R.string.cannot_create_store));
                    }
                }
                passwordInput2.setText(null);
                passwordInput.setText(null);
                passwordInput.requestFocus();
            }
        });
    }

    /*
     * Very similar to create key store, but not the same :-)
     * database.rawExecSQL(String.format("PRAGMA key = '%s'", newPassword);
     */
    private void changePassword() {
        passwordInputOld = (EditText) findViewById(R.id.oldPasswordInput);
        passwordInputOld.setVisibility(View.VISIBLE);

        passwordInput.addTextChangedListener(pwFilter);
        passwordInput.setHint(R.string.password_hint_new);      // ask user for a new password
        passwordInput.requestFocus();
        passwordInput.setVisibility(View.VISIBLE);
        passwordInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        passwordInput2 = (EditText) findViewById(R.id.passwordInput2);
        passwordInput2.setVisibility(View.VISIBLE);
        pwStrength = (TextView) findViewById(R.id.passwordStrength);
        pwStrength.setVisibility(View.VISIBLE);
        passwordShow.setVisibility(View.VISIBLE);
        lockUnlock.setText(R.string.perform_change);
        listView.setVisibility(View.INVISIBLE);

        lockUnlock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (checkPassword(passwordInput.getText(), passwordInput2.getText())) {
                    keyService.closeDatabase();
                    if (!keyService.openOrCreateDatabase(passwordInputOld.getText())) {
                        showInputInfo(getString(R.string.old_password_wrong));
                        passwordInputOld.setText(null);
                        passwordInputOld.requestFocus();
                        if (!lockedDuringPwChange) {
                            ProviderDbBackend.sendLockRequests();
                            lockedDuringPwChange = true;
                        }
                    }
                    else if (ProviderDbBackend.changePassword(passwordInput.getText())) {
                        if (lockedDuringPwChange) {
                            ProviderDbBackend.sendUnlockRequests();
                            lockedDuringPwChange = false;
                        }
                        passwordInput.removeTextChangedListener(pwFilter);
                        passwordInput.setHint(R.string.password_hint);
                        passwordInput2.setVisibility(View.GONE);
                        passwordInputOld.setVisibility(View.GONE);
                        passwordInputOld.setText(null);
                        pwStrength.setVisibility(View.GONE);
                        listView.setVisibility(View.VISIBLE);
                        storeCreation = false;
                        showNormalScreen();
                    }
                    else {
                        showInputInfo(getString(R.string.cannot_change_password));
                    }
                }
                else {
                    passwordInput.requestFocus();
                }
                passwordInput2.setText(null);
                passwordInput.setText(null);
            }
        });
    }

    // Click on "unlock" gets the password and checks if it works with key store.
    private class UnlockClick implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            if (passwordInput.getText() == null || passwordInput.getText().length() == 0) {
                showInputInfo(getString(R.string.no_password));
                return;
            }
            if (keyService.openOrCreateDatabase(passwordInput.getText())) {
                showNormalScreen();
                ProviderDbBackend.sendUnlockRequests();
                mHandler.sendEmptyMessage(KEY_STORE_READY);
            }
            else {
                keyService.closeDatabase();         // close DB, but do not stop service
                showInputInfo(getString(R.string.cannot_load_store));
                passwordInput.requestFocus();
            }
            passwordInput.setText(null);
        }
    }

    // LongClick on "lock" locks the key store and sends the lock notification.
    private class LockClick implements View.OnLongClickListener {
        @Override
        public boolean onLongClick(View  view) {
            keyService.closeDatabase();             // clear keys etc, but do not stop service
            showNormalScreen();
            ProviderDbBackend.sendLockRequests();
            return true;
        }
    }

    // Shows the normal screen, depending on key store state. Also switches the click listeners
    // on the button.
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void showNormalScreen() {
        if (storeCreation)
            return;
        // If READY then set for LOCK
        if (KeyService.isReady()) {
            lockUnlock.setText(R.string.lock);
            lockUnlock.setOnClickListener(null);
            lockUnlock.setOnLongClickListener(lockListener);
            passwordInput.setVisibility(View.GONE);
            passwordShow.setVisibility(View.GONE);
            passwordShow.setChecked(false);
            showPasswordCheck(passwordShow);
        }
        else {
            passwordInput.setVisibility(View.VISIBLE);
            passwordInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
            passwordInput.requestFocus();
            lockUnlock.setText(R.string.unlock);
            passwordShow.setVisibility(View.VISIBLE);
            lockUnlock.setOnClickListener(unlockListener);
            lockUnlock.setOnLongClickListener(null);
        }
        updateExternalStorageState();
        keyService.updateNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            invalidateOptionsMenu();
        }
    }

    private void updateListOfApps() {
        if (registeredApps == null)
            return;
        registeredApps.clear();
        Set<String> registeredNames = KeyService.getRegisteredApps().keySet();
        for (String name : registeredNames) {
            String appName = KeyService.getRegisteredApps().get(name).displayName;
            registeredApps.add(appName);
        }
        registeredApps.notifyDataSetChanged();
        listView.invalidate();
    }

    /**
     * Switch between visible and invisible password.
     *
     * @param v the Checkbox
     */
    public void showPasswordCheck(View v) {
        CheckBox cbv = (CheckBox)v;
        if (cbv.isChecked()) {
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            if (passwordInput2 != null)
                passwordInput2.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            if (passwordInputOld != null)
                passwordInputOld.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        }
        else {
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            if (passwordInput2 != null)
                passwordInput2.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            if (passwordInputOld != null)
                passwordInputOld.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        if (!TextUtils.isEmpty(passwordInput.getText()))
            passwordInput.setSelection(passwordInput.getText().length());
        if (passwordInput2 != null && !TextUtils.isEmpty(passwordInput.getText()))
            passwordInput2.setSelection(passwordInput2.getText().length());
        if (passwordInputOld != null && !TextUtils.isEmpty(passwordInput.getText()))
            passwordInputOld.setSelection(passwordInputOld.getText().length());
    }

    /**
     * A simple check if two passwords are equal.
     *
     * Returns false if one or both passwords are null or empty or if the passwords
     * don't match.
     *
     * @param pw1 first password
     * @param pw2 second password
     * @return true if both passwords match and are not empty.
     */
    private boolean checkPassword(CharSequence pw1, CharSequence pw2) {
        if (pw1 == null || pw2 == null || pw1.length() == 0 || pw2.length() == 0) {
            showInputInfo(getString(R.string.no_password));
            return false;
        }
        if (pw1.length() != pw2.length()) {
            showInputInfo(getString(R.string.password_match));
            return false;
        }
        int len = pw1.length();
        for (int i = 0; i < len; i++) {
            if (pw1.charAt(i) != pw2.charAt(i)) {
                showInputInfo(getString(R.string.password_match));
                return false;
            }
        }
        return true;
    }

    void updateExternalStorageState() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            mExternalStorageAvailable = mExternalStorageWriteable = true;
        } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            mExternalStorageAvailable = true;
            mExternalStorageWriteable = false;
        } else {
            mExternalStorageAvailable = mExternalStorageWriteable = false;
        }
    }

    /**
     * A simple password strength check.
     *
     * This check just looks for length and some different characters to give an indication
     * about a password strength.
     *
     * @author werner
     *
     */
    private class PasswordFilter implements TextWatcher {

        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        public void afterTextChanged(Editable s) {
            String str = s.toString();
            int strLength = str.length();
            int strength = 0;

            if (strLength == 0) {
                pwStrength.setText(null);
                return;
            }
            boolean digit = false;
            boolean lower = false;
            boolean upper = false;
            boolean other = false;

            for (int i = 0; i < strLength; i++) {
                char chr = str.charAt(i);
                if (Character.isDigit(chr))
                    digit = true;
                else if (Character.isLowerCase(chr))
                    lower = true;
                else if (Character.isUpperCase(chr))
                    upper = true;
                else
                    other = true;
            }
            strength += (digit) ? 1 : 0;
            strength += (lower) ? 1 : 0;
            strength += (upper) ? 1 : 0;
            strength += (other) ? 1 : 0;

            pwStrength.setText(getString(R.string.pwstrength_weak));
            if (((strength >= 2 && strLength >= 7) || (strength >= 3 && strLength >= 6)) || strLength > 8) {
                pwStrength.setText(getString(R.string.pwstrength_good));
            }
            if ((strength >= 3 && strLength >= 7) || strLength > 10) {
                pwStrength.setText(getString(R.string.pwstrength_strong));
            }
        }
    }

    static void updateAppList() {
        if (mHandler != null) {
            mHandler.sendEmptyMessage(UPDATE_APP_REGISTERED);
        }
    }

    /**
     * Internal message handler class.
     *
     * @author werner
     *
     */
    private static class InternalHandler extends Handler {
        private final WeakReference<KeyManagerActivity> mTarget;

        InternalHandler(KeyManagerActivity parent) {
            mTarget = new WeakReference<KeyManagerActivity>(parent);
        }

        @Override
        public void handleMessage(Message msg) {
            KeyManagerActivity parent = mTarget.get();
            if (parent == null)
                return;

            if (msg.what == UPDATE_APP_REGISTERED) {
                parent.updateListOfApps();
                return;
            }
            if (!parent.readyIntent)
                return;
            switch (msg.what) {
                case KEY_STORE_READY:
                    parent.setResult(RESULT_OK);
                    break;
                case KEY_STORE_FAILED:
                    parent.setResult(RESULT_CANCELED);
                    break;
            }
            parent.finish();
        }
    }

    private void showInputInfo(String msg) {
        InfoMsgDialogFragment infoMsg = InfoMsgDialogFragment.newInstance(msg);
        FragmentManager fragmentManager = getSupportFragmentManager();
        infoMsg.show(fragmentManager, "SilentCircleKeyManagerInfo");
    }

    /*
     * Dialog classes to display Error and Information messages.
     */
    private static String MESSAGE = "message";
    public static class InfoMsgDialogFragment extends DialogFragment {

        public static InfoMsgDialogFragment newInstance(String msg) {
            InfoMsgDialogFragment f = new InfoMsgDialogFragment();

            Bundle args = new Bundle();
            args.putString(MESSAGE, msg);
            f.setArguments(args);

            return f;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(getString(R.string.information_dialog))
                    .setMessage(getArguments().getString(MESSAGE))
                    .setPositiveButton(getString(R.string.confirm_dialog), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                        }
                    });
            // Create the AlertDialog object and return it
            return builder.create();
        }
    }

    private void showKeyManagerInfo(int msgId) {
        InfoKeyManagerDialog infoMsg = InfoKeyManagerDialog.newInstance(msgId);
        FragmentManager fragmentManager = getSupportFragmentManager();
        infoMsg.show(fragmentManager, "InfoKeyManagerDialog");
    }

    public static class InfoKeyManagerDialog extends DialogFragment {
        private static String MESSAGE_ID = "messageId";

        public static InfoKeyManagerDialog newInstance(int msgId) {
            InfoKeyManagerDialog f = new InfoKeyManagerDialog();

            Bundle args = new Bundle();
            args.putInt(MESSAGE_ID, msgId);
            f.setArguments(args);

            return f;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.information_dialog)
                    .setMessage(getArguments().getInt(MESSAGE_ID))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                        }
                    });
            // Create the AlertDialog object and return it
            return builder.create();
        }
    }
}
