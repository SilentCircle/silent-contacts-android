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

/*
 * This  implementation is edited version of original Android sources.
 */

package com.silentcircle.contacts.calllog;

import com.actionbarsherlock.app.SherlockListFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.MenuInflater;

import com.silentcircle.contacts.utils.EmptyLoader;
import com.silentcircle.contacts.ContactsUtils;
import com.silentcircle.contacts.R;
import com.silentcircle.silentcontacts.ScContactsContract;
import com.silentcircle.silentcontacts.ScCallLog.ScCalls;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;


public class ScCallLogFragment extends SherlockListFragment implements ScCallLogQueryHandler.Listener, ScCallLogAdapter.CallFetcher {

    private static final String TAG = "ScCallLogFragment";

    /**
     * ID of the empty loader to defer other fragments.
     */
    private static final int EMPTY_LOADER_ID = 0;

    private ScCallLogAdapter mAdapter;
    private ScCallLogQueryHandler mCallLogQueryHandler;
    private boolean mScrollToTop;

    private boolean mEmptyLoaderRunning;
    private boolean mCallLogFetched;
    private boolean hideMenu;

    private final Handler mHandler = new Handler();

    private TextView mFilterStatusView;
    private KeyguardManager mKeyguardManager;

    // Default to all calls.
    private int mCallTypeFilter = ScCallLogQueryHandler.CALL_TYPE_ALL;

    private class CustomContentObserver extends ContentObserver {
        public CustomContentObserver() {
            super(mHandler);
        }
        @Override
        public void onChange(boolean selfChange) {
            mRefreshDataRequired = true;
            refreshData();
        }
    }

    // See issue 6363009
    private final ContentObserver mCallLogObserver = new CustomContentObserver();
    private final ContentObserver mContactsObserver = new CustomContentObserver();
    private boolean mRefreshDataRequired = true;

    // Exactly same variable is in Fragment as a package private.
    private boolean mMenuVisible = true;


    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        mCallLogQueryHandler = new ScCallLogQueryHandler(getActivity().getContentResolver(), this);
        mKeyguardManager = (KeyguardManager)getActivity().getSystemService(Context.KEYGUARD_SERVICE);
        getActivity().getContentResolver().registerContentObserver(ScCalls.CONTENT_URI, true, mCallLogObserver);
        getActivity().getContentResolver().registerContentObserver(ScContactsContract.AUTHORITY_URI, true, mContactsObserver);
        String currentCountryIso = ContactsUtils.getCurrentCountryIso(getActivity());
        mAdapter = new ScCallLogAdapter(getActivity(), this, new ContactInfoHelper(getActivity(), currentCountryIso));
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        View view = inflater.inflate(R.layout.call_log_fragment, container, false);
        mFilterStatusView = (TextView) view.findViewById(R.id.filter_status);
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setListAdapter(mAdapter);
        getListView().setItemsCanFocus(true);
    }

    /**
     * Based on the new intent, decide whether the list should be configured
     * to scroll up to display the first item.
     */
    public void configureScreenFromIntent(Intent newIntent) {
        // Typically, when switching to the call-log we want to show the user
        // the same section of the list that they were most recently looking
        // at.  However, under some circumstances, we want to automatically
        // scroll to the top of the list to present the newest call items.
        // For example, immediately after a call is finished, we want to
        // display information about that call.
        mScrollToTop = ScCalls.CONTENT_TYPE.equals(newIntent.getType());
    }


    /** Called by the CallLogQueryHandler when the list of calls has been fetched or updated. */
    @Override
    public void onCallsFetched(Cursor cursor) {
        if (getActivity() == null || getActivity().isFinishing()) {
            return;
        }
        mAdapter.setLoading(false);
        mAdapter.changeCursor(cursor);
        // This will update the state of the "Clear call log" menu item.
        getActivity().supportInvalidateOptionsMenu();
        if (mScrollToTop) {
            final ListView listView = getListView();
            // The smooth-scroll animation happens over a fixed time period.
            // As a result, if it scrolls through a large portion of the list,
            // each frame will jump so far from the previous one that the user
            // will not experience the illusion of downward motion.  Instead,
            // if we're not already near the top of the list, we instantly jump
            // near the top, and animate from there.
            if (listView.getFirstVisiblePosition() > 5) {
                listView.setSelection(5);
            }
            // Workaround for framework issue: the smooth-scroll doesn't
            // occur if setSelection() is called immediately before.
            mHandler.post(new Runnable() {
               @Override
               public void run() {
                   if (getActivity() == null || getActivity().isFinishing()) {
                       return;
                   }
                   listView.smoothScrollToPosition(0);
               }
            });

            mScrollToTop = false;
        }
        mCallLogFetched = true;
        destroyEmptyLoaderIfAllDataFetched();
    }

    private void destroyEmptyLoaderIfAllDataFetched() {
        if (mCallLogFetched && mEmptyLoaderRunning) {
            mEmptyLoaderRunning = false;
            getLoaderManager().destroyLoader(EMPTY_LOADER_ID);
        }
    }


    @Override
    public void onStart() {
        // Start the empty loader now to defer other fragments.  We destroy it when both calllog
        // and the voicemail status are fetched.
        getLoaderManager().initLoader(EMPTY_LOADER_ID, null, new EmptyLoader.Callback(getActivity()));
        mEmptyLoaderRunning = true;
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshData();
    }

    /**
     * ScCallLogAdapter.CallFetcher
     */
    @Override
    public void fetchCalls() {
        mCallLogQueryHandler.fetchCalls(mCallTypeFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        // Kill the requests thread
        mAdapter.stopRequestProcessing();
    }

    @Override
    public void onStop() {
        super.onStop();
        updateOnExit();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mAdapter.stopRequestProcessing();
        mAdapter.changeCursor(null);
        getActivity().getContentResolver().unregisterContentObserver(mCallLogObserver);
        getActivity().getContentResolver().unregisterContentObserver(mContactsObserver);
//        unregisterPhoneCallReceiver();
    }

    public void startCallsQuery() {
        mAdapter.setLoading(true);
        mCallLogQueryHandler.fetchCalls(mCallTypeFilter);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.call_log_options, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        final MenuItem itemDeleteAll = menu.findItem(R.id.delete_all);
        // Check if all the menu items are inflated correctly. As a shortcut, we assume all
        // menu items are ready if the first item is non-null.
        if (itemDeleteAll != null) {
            itemDeleteAll.setEnabled(mAdapter != null && !mAdapter.isEmpty());
            itemDeleteAll.setVisible(hideMenu);

            showAllFilterMenuOptions(menu);     // show menu depending on hideMenu boolean
            hideCurrentFilterMenuOption(menu);
        }
    }

    public void hideMenu(boolean hide) {
        hideMenu = hide;
    }
    private void hideCurrentFilterMenuOption(Menu menu) {
        MenuItem item = null;
        switch (mCallTypeFilter) {
            case ScCallLogQueryHandler.CALL_TYPE_ALL:
                item = menu.findItem(R.id.show_all_calls);
                break;
            case ScCalls.INCOMING_TYPE:
                item = menu.findItem(R.id.show_incoming_only);
                break;
            case ScCalls.OUTGOING_TYPE:
                item = menu.findItem(R.id.show_outgoing_only);
                break;
            case ScCalls.MISSED_TYPE:
                item = menu.findItem(R.id.show_missed_only);
                break;
        }
        if (item != null) {
            item.setVisible(false);
        }
    }

    private void showAllFilterMenuOptions(Menu menu) {
        menu.findItem(R.id.show_all_calls).setVisible(hideMenu);
        menu.findItem(R.id.show_incoming_only).setVisible(hideMenu);
        menu.findItem(R.id.show_outgoing_only).setVisible(hideMenu);
        menu.findItem(R.id.show_missed_only).setVisible(hideMenu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.delete_all:
                ClearCallLogDialog.show(getFragmentManager());
                return true;

            case R.id.show_outgoing_only:
                // We only need the phone call receiver when there is an active call type filter.
                // Not many people may use the filters so don't register the receiver until now .
//                registerPhoneCallReceiver();
                mCallLogQueryHandler.fetchCalls(ScCalls.OUTGOING_TYPE);
                updateFilterTypeAndHeader(ScCalls.OUTGOING_TYPE);
                return true;

            case R.id.show_incoming_only:
//                registerPhoneCallReceiver();
                mCallLogQueryHandler.fetchCalls(ScCalls.INCOMING_TYPE);
                updateFilterTypeAndHeader(ScCalls.INCOMING_TYPE);
                return true;

            case R.id.show_missed_only:
//                registerPhoneCallReceiver();
                mCallLogQueryHandler.fetchCalls(ScCalls.MISSED_TYPE);
                updateFilterTypeAndHeader(ScCalls.MISSED_TYPE);
                return true;


            case R.id.show_all_calls:
                // Filter is being turned off, receiver no longer needed.
//                unregisterPhoneCallReceiver();
                mCallLogQueryHandler.fetchCalls(ScCallLogQueryHandler.CALL_TYPE_ALL);
                updateFilterTypeAndHeader(ScCallLogQueryHandler.CALL_TYPE_ALL);
                return true;

            default:
                return false;
        }
    }

    private void updateFilterTypeAndHeader(int filterType) {
        mCallTypeFilter = filterType;

        switch (filterType) {
            case ScCallLogQueryHandler.CALL_TYPE_ALL:
                mFilterStatusView.setVisibility(View.GONE);
                break;
            case ScCalls.INCOMING_TYPE:
                showFilterStatus(R.string.call_log_incoming_header);
                break;
            case ScCalls.OUTGOING_TYPE:
                showFilterStatus(R.string.call_log_outgoing_header);
                break;
            case ScCalls.MISSED_TYPE:
                showFilterStatus(R.string.call_log_missed_header);
                break;
        }
    }

    private void showFilterStatus(int resId) {
        mFilterStatusView.setText(resId);
        mFilterStatusView.setVisibility(View.VISIBLE);
    }

    // TODO Currently not used??
    public void callSelectedEntry() {
        int position = getListView().getSelectedItemPosition();
        if (position < 0) {
            // In touch mode you may often not have something selected, so
            // just call the first entry to make sure that [send] [send] calls the
            // most recent entry.
            position = 0;
        }
        final Cursor cursor = (Cursor)mAdapter.getItem(position);
        if (cursor != null) {
            String number = cursor.getString(ScCallLogQuery.NUMBER);
            if (TextUtils.isEmpty(number)
                    || number.equals(PhoneNumberHelper.UNKNOWN_NUMBER)
                    || number.equals(PhoneNumberHelper.PRIVATE_NUMBER)
                    || number.equals(PhoneNumberHelper.PAYPHONE_NUMBER)) {
                // This number can't be called, do nothing
                return;
            }
            Intent intent;
            // If "number" is really a SIP address, construct a sip: URI.
//            if (PhoneNumberUtils.isUriNumber(number)) {
//                intent = ContactsUtils.getCallIntent(
//                        Uri.fromParts(Constants.SCHEME_SIP, number, null));
//            } else {
                // We're calling a regular PSTN phone number.
                // Construct a tel: URI, but do some other possible cleanup first.
                int callType = cursor.getInt(ScCallLogQuery.CALL_TYPE);
                if (!number.startsWith("+") &&
                       (callType == ScCalls.INCOMING_TYPE || callType == ScCalls.MISSED_TYPE)) {
                    // If the caller-id matches a contact with a better qualified number, use it
                    String countryIso = cursor.getString(ScCallLogQuery.COUNTRY_ISO);
                    number = mAdapter.getBetterNumberFromContacts(number, countryIso);
                }
//                intent = ContactsUtils.getCallIntent(Uri.fromParts(Constants.SCHEME_TEL, number, null));
//            }
//            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
//            startActivity(intent);
                Log.d(TAG, "Call selected number: " + number);
        }
    }

//    @VisibleForTesting
    ScCallLogAdapter getAdapter() {
        return mAdapter;
    }

    @Override
    public void setMenuVisibility(boolean menuVisible) {
        super.setMenuVisibility(menuVisible);
        if (mMenuVisible != menuVisible) {
            mMenuVisible = menuVisible;
            if (!menuVisible) {
                updateOnExit();
            } else if (isResumed()) {
                refreshData();
            }
        }
    }

    /** Requests updates to the data to be shown. */
    private void refreshData() {
        // Prevent unnecessary refresh.
        if (mRefreshDataRequired) {
            // Mark all entries in the contact info cache as out of date, so they will be looked up
            // again once being shown.
            mAdapter.invalidateCache();
            startCallsQuery();
            updateOnEntry();
            mRefreshDataRequired = false;
        }
    }

    /** Removes the missed call notifications. */
    private void removeMissedCallNotifications() {
//        try {
//            ITelephony telephony =
//                    ITelephony.Stub.asInterface(ServiceManager.getService("phone"));
//            if (telephony != null) {
//                telephony.cancelMissedCallsNotification();
//            } else {
//                Log.w(TAG, "Telephony service is null, can't call " +
//                        "cancelMissedCallsNotification");
//            }
//        } catch (RemoteException e) {
//            Log.e(TAG, "Failed to clear missed calls notification due to remote exception");
//        }
    }

    /** Updates call data and notification state while leaving the call log tab. */
    private void updateOnExit() {
        updateOnTransition(false);
    }

    /** Updates call data and notification state while entering the call log tab. */
    private void updateOnEntry() {
        updateOnTransition(true);
    }

    private void updateOnTransition(boolean onEntry) {
        // We don't want to update any call data when keyguard is on because the user has likely not
        // seen the new calls yet.
        // This might be called before onCreate() and thus we need to check null explicitly.
        if (mKeyguardManager != null && !mKeyguardManager.inKeyguardRestrictedInputMode()) {
            // On either of the transitions we reset the new flag and update the notifications.
            // While exiting we additionally consume all missed calls (by marking them as read).
            // This will ensure that they no more appear in the "new" section when we return back.
            mCallLogQueryHandler.markNewCallsAsOld();
            if (!onEntry) {
                mCallLogQueryHandler.markMissedCallsAsRead();
            }
            removeMissedCallNotifications();
        }
    }
}
