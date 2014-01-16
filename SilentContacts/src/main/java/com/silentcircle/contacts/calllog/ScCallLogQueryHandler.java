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

/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.silentcircle.contacts.calllog;

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteFullException;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.silentcircle.silentcontacts.ScCallLog.ScCalls;
import com.silentcircle.contacts.utils.Closeables;
// import com.google.common.collect.Lists;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

// import javax.annotation.concurrent.GuardedBy;

/** Handles asynchronous queries to the call log. */
/*package*/ class ScCallLogQueryHandler extends AsyncQueryHandler {
    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    private static final String TAG = "CallLogQueryHandler";
    private static final int NUM_LOGS_TO_DISPLAY = 1000;

    /** The token for the query to fetch the new entries from the call log. */
    private static final int QUERY_NEW_CALLS_TOKEN = 53;
    /** The token for the query to fetch the old entries from the call log. */
    private static final int QUERY_OLD_CALLS_TOKEN = 54;
    /** The token for the query to mark all missed calls as old after seeing the call log. */
    private static final int UPDATE_MARK_AS_OLD_TOKEN = 55;
    /** The token for the query to mark all new voicemails as old. */
    private static final int UPDATE_MARK_VOICEMAILS_AS_OLD_TOKEN = 56;
    /** The token for the query to mark all missed calls as read after seeing the call log. */
    private static final int UPDATE_MARK_MISSED_CALL_AS_READ_TOKEN = 57;
    /** The token for the query to fetch voicemail status messages. */
    private static final int QUERY_VOICEMAIL_STATUS_TOKEN = 58;

    /**
     * Call type similar to ScCalls.INCOMING_TYPE used to specify all types instead of one particular
     * type.
     */
    public static final int CALL_TYPE_ALL = -1;

    /**
     * The time window from the current time within which an unread entry will be added to the new
     * section.
     */
    private static final long NEW_SECTION_TIME_WINDOW = TimeUnit.DAYS.toMillis(7);

    private final WeakReference<Listener> mListener;

    /** The cursor containing the new calls, or null if they have not yet been fetched. */
    /* @GuardedBy("this") */ private Cursor mNewCallsCursor;
    /** The cursor containing the old calls, or null if they have not yet been fetched. */
    /* @GuardedBy("this") */ private Cursor mOldCallsCursor;
    /**
     * The identifier of the latest calls request.
     * <p>
     * A request for the list of calls requires two queries and hence the two cursor
     * {@link #mNewCallsCursor} and {@link #mOldCallsCursor} above, corresponding to
     * {@link #QUERY_NEW_CALLS_TOKEN} and {@link #QUERY_OLD_CALLS_TOKEN}.
     * <p>
     * When a new request is about to be started, existing cursors are closed. However, it is
     * possible that one of the queries completes after the new request has started. This means that
     * we might merge two cursors that do not correspond to the same request. Moreover, this may
     * lead to a resource leak if the same query completes and we override the cursor without
     * closing it first.
     * <p>
     * To make sure we only join two cursors from the same request, we use this variable to store
     * the request id of the latest request and make sure we only process cursors corresponding to
     * the this request.
     */
    /* @GuardedBy("this") */ private int mCallsRequestId;

    /**
     * Simple handler that wraps background calls to catch
     * {@link SQLiteException}, such as when the disk is full.
     */
    protected class CatchingWorkerHandler extends AsyncQueryHandler.WorkerHandler {
        public CatchingWorkerHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            try {
                // Perform same query while catching any exceptions
                super.handleMessage(msg);
            } catch (SQLiteDiskIOException e) {
                Log.w(TAG, "Exception on background worker thread", e);
            } catch (SQLiteFullException e) {
                Log.w(TAG, "Exception on background worker thread", e);
            } catch (SQLiteDatabaseCorruptException e) {
                Log.w(TAG, "Exception on background worker thread", e);
            }
        }
    }

    @Override
    protected Handler createHandler(Looper looper) {
        // Provide our special handler that catches exceptions
        return new CatchingWorkerHandler(looper);
    }

    public ScCallLogQueryHandler(ContentResolver contentResolver, Listener listener) {
        super(contentResolver);
        mListener = new WeakReference<Listener>(listener);
    }

    /** Creates a cursor that contains a single row and maps the section to the given value. */
    private Cursor createHeaderCursorFor(int section) {
        MatrixCursor matrixCursor =  new MatrixCursor(ScCallLogQuery.EXTENDED_PROJECTION);
        // The values in this row correspond to default values for _PROJECTION from CallLogQuery
        // plus the section value.
        matrixCursor.addRow(new Object[] {
        //       0   1   2     3    4     5      6     7      8        9        10       11      12        13       14        15      16   17
        //     _Id   #  date  dur  type  c-iso  loc  ch-nm  ch-type  ch-#-lbl  ch-uri  ch-m-#  ch-norm-#  ph-id  ch-form-#  is-read
                0L,  "",  0L,  0L,  0,    "",    "",  null,   0,      null,     null,   null,   null,       0L,   null,       0,  section, null
        });
        return matrixCursor;
    }

    /** Returns a cursor for the old calls header. */
    private Cursor createOldCallsHeaderCursor() {
        return createHeaderCursorFor(ScCallLogQuery.SECTION_OLD_HEADER);
    }

    /** Returns a cursor for the new calls header. */
    private Cursor createNewCallsHeaderCursor() {
        return createHeaderCursorFor(ScCallLogQuery.SECTION_NEW_HEADER);
    }

    /**
     * Fetches the list of calls from the call log for a given type.
     * <p>
     * It will asynchronously update the content of the list view when the fetch completes.
     */
    public void fetchCalls(int callType) {
        cancelFetch();
        int requestId = newCallsRequest();
        fetchCalls(QUERY_NEW_CALLS_TOKEN, requestId, true /*isNew*/, callType);
        fetchCalls(QUERY_OLD_CALLS_TOKEN, requestId, false /*isNew*/, callType);
    }

    /** Fetches the list of calls in the call log, either the new one or the old ones. */
    private void fetchCalls(int token, int requestId, boolean isNew, int callType) {
        // We need to check for NULL explicitly otherwise entries with where READ is NULL
        // may not match either the query or its negation.
        // We consider the calls that are not yet consumed (i.e. IS_READ = 0) as "new".
        String selection = String.format("%s IS NOT NULL AND %s = 0 AND %s > ?", ScCalls.IS_READ, ScCalls.IS_READ, ScCalls.DATE);
        List<String> selectionArgs = new ArrayList<String>();
        selectionArgs.add(Long.toString(System.currentTimeMillis() - NEW_SECTION_TIME_WINDOW));

        if (!isNew) {           // Negate the query.
            selection = String.format("NOT (%s)", selection);
        }
        if (callType > CALL_TYPE_ALL) {
            selection = String.format("(%s) AND (%s = ?)", selection, ScCalls.TYPE);
            selectionArgs.add(Integer.toString(callType));
        }
        Uri uri = ScCalls.CONTENT_URI.buildUpon()
                .appendQueryParameter(ScCalls.LIMIT_PARAM_KEY, Integer.toString(NUM_LOGS_TO_DISPLAY))
                .build();
        startQuery(token, requestId, uri,
                ScCallLogQuery._PROJECTION, selection, selectionArgs.toArray(EMPTY_STRING_ARRAY), ScCalls.DEFAULT_SORT_ORDER);
    }

    /** Cancel any pending fetch request. */
    private void cancelFetch() {
        cancelOperation(QUERY_NEW_CALLS_TOKEN);
        cancelOperation(QUERY_OLD_CALLS_TOKEN);
    }

    /** Updates all new calls to mark them as old. */
    public void markNewCallsAsOld() {
        // Mark all "new" calls as not new anymore.
        StringBuilder where = new StringBuilder();
        where.append(ScCalls.NEW);
        where.append(" = 1");

        ContentValues values = new ContentValues(1);
        values.put(ScCalls.NEW, "0");

        startUpdate(UPDATE_MARK_AS_OLD_TOKEN, null, ScCalls.CONTENT_URI, values, where.toString(), null);
    }

    /** Updates all missed calls to mark them as read. */
    public void markMissedCallsAsRead() {
        // Mark all "new" calls as not new anymore.
        StringBuilder where = new StringBuilder();
        where.append(ScCalls.IS_READ).append(" = 0");
        where.append(" AND ");
        where.append(ScCalls.TYPE).append(" = ").append(ScCalls.MISSED_TYPE);

        ContentValues values = new ContentValues(1);
        values.put(ScCalls.IS_READ, "1");

        startUpdate(UPDATE_MARK_MISSED_CALL_AS_READ_TOKEN, null, ScCalls.CONTENT_URI, values, where.toString(), null);
    }

    /**
     * Start a new request and return its id. The request id will be used as the cookie for the
     * background request.
     * <p>
     * Closes any open cursor that has not yet been sent to the requester.
     */
    private synchronized int newCallsRequest() {
        Closeables.closeQuietly(mNewCallsCursor);
        Closeables.closeQuietly(mOldCallsCursor);
        mNewCallsCursor = null;
        mOldCallsCursor = null;
        return ++mCallsRequestId;
    }

    @Override
    protected synchronized void onQueryComplete(int token, Object cookie, Cursor cursor) {
        if (token == QUERY_NEW_CALLS_TOKEN) {
            int requestId = ((Integer) cookie).intValue();
            if (requestId != mCallsRequestId) {     // Ignore this query since it does not correspond to the latest request.
                return;
            }
            Closeables.closeQuietly(mNewCallsCursor);
            mNewCallsCursor = new ExtendedCursor(cursor, ScCallLogQuery.SECTION_NAME, ScCallLogQuery.SECTION_NEW_ITEM,
                    ScCallLogQuery.REL_DATE_NAME);
        } else if (token == QUERY_OLD_CALLS_TOKEN) {
            int requestId = ((Integer) cookie).intValue();
            if (requestId != mCallsRequestId) {  // Ignore this query since it does not correspond to the latest request.
                return;
            }
            Closeables.closeQuietly(mOldCallsCursor);
            mOldCallsCursor = new ExtendedCursor(cursor, ScCallLogQuery.SECTION_NAME, ScCallLogQuery.SECTION_OLD_ITEM,
                    ScCallLogQuery.REL_DATE_NAME);
        } else {
            Log.w(TAG, "Unknown query completed: ignoring: " + token);
            return;
        }
        if (mNewCallsCursor != null && mOldCallsCursor != null) {
            updateAdapterData(createMergedCursor());
        }
    }

    /** Creates the merged cursor representing the data to show in the call log. */
    /* TODO @GuardedBy("this") */
    private Cursor createMergedCursor() {
        try {
            final boolean hasNewCalls = mNewCallsCursor.getCount() != 0;
            final boolean hasOldCalls = mOldCallsCursor.getCount() != 0;

            if (!hasNewCalls) {
                Closeables.closeQuietly(mNewCallsCursor);  // Return only the old calls, without the header.
                return mOldCallsCursor;
            }

            if (!hasOldCalls) {                
                Closeables.closeQuietly(mOldCallsCursor);  // Return only the new calls.
                return new MergeCursor(new Cursor[]{ createNewCallsHeaderCursor(), mNewCallsCursor });
            }
            return new MergeCursor(new Cursor[]{
                    createNewCallsHeaderCursor(), mNewCallsCursor,
                    createOldCallsHeaderCursor(), mOldCallsCursor});
        } finally {  // Any cursor still open is now owned, directly or indirectly, by the caller.
            mNewCallsCursor = null;
            mOldCallsCursor = null;
        }
    }

    /**
     * Updates the adapter in the call log fragment to show the new cursor data.
     */
    private void updateAdapterData(Cursor combinedCursor) {
        final Listener listener = mListener.get();
        if (listener != null) {
            listener.onCallsFetched(combinedCursor);
        }
    }

    /** Listener to completion of various queries. */
    public interface Listener {
        /**
         * Called when {@link ScCallLogQueryHandler#fetchCalls(int)}complete.
         */
        void onCallsFetched(Cursor combinedCursor);
    }
}
