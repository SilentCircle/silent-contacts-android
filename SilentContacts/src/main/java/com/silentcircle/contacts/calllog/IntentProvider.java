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

import com.silentcircle.contacts.ContactsUtils;
import com.silentcircle.contacts.ScCallDetailActivity;
import com.silentcircle.silentcontacts.ScCallLog.ScCalls;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.util.Log;

//import com.android.contacts.CallDetailActivity;
//import com.android.contacts.ContactsUtils;

/**
 * Used to create an intent to attach to an action in the call log.
 * <p>
 * The intent is constructed lazily with the given information.
 */
public abstract class IntentProvider {
    private static final String TAG = "IntentProvider";
    
    public int listItemPosition;

    public abstract Intent getIntent(Context context);

    private IntentProvider(int listPos) {
        listItemPosition = listPos;
    }

    public static IntentProvider getReturnCallIntentProvider(final String number) {
        return new IntentProvider(-1) {
            @Override
            public Intent getIntent(Context context) {
                Log.d(TAG, "Call selected number: " + number);
                return ContactsUtils.getCallIntent(number);
            }
        };
    }

    public static IntentProvider getCallDetailIntentProvider(
            final ScCallLogAdapter adapter, final int position, final long id, final int groupSize, final int listPos) {
        return new IntentProvider(listPos) {
            @Override
            public Intent getIntent(Context context) {
                Cursor cursor = adapter.getCursor();
                cursor.moveToPosition(position);
                if (ScCallLogQuery.isSectionHeader(cursor)) {
                    // Do nothing when a header is clicked.
                    return null;
                }
                Intent intent = new Intent(context, ScCallDetailActivity.class);

                if (groupSize > 1) {
                    // We want to restore the position in the cursor at the end.
                    long[] ids = new long[groupSize];
                    // Copy the ids of the rows in the group.
                    for (int index = 0; index < groupSize; ++index) {
                        ids[index] = cursor.getLong(ScCallLogQuery.ID);
                        cursor.moveToNext();
                    }
                    intent.putExtra(ScCallDetailActivity.EXTRA_CALL_LOG_IDS, ids);
                } else {
                    // If there is a single item, use the direct URI for it.
                    intent.setData(ContentUris.withAppendedId(ScCalls.CONTENT_URI, id));
                }
                Log.d(TAG, "Call detail intent");
                return intent;
            }
        };
    }
}
