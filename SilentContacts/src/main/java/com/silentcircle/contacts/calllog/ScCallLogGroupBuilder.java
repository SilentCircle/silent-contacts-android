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

import java.text.SimpleDateFormat;
import java.util.Date;

import android.database.Cursor;
import android.telephony.PhoneNumberUtils;

import com.silentcircle.silentcontacts.ScCallLog.ScCalls;
import com.silentcircle.contacts.utils.GroupingListAdapter;
// import com.google.common.annotations.VisibleForTesting;

/**
 * Groups together calls in the call log.
 * <p>
 * This class is meant to be used in conjunction with {@link GroupingListAdapter}.
 */
public class ScCallLogGroupBuilder {
    
    private static final String TAG = "ScCallLogGroupBuilder";
    
    public interface GroupCreator {
        public void addGroup(int cursorPosition, int size, boolean expanded);
    }

    /** The object on which the groups are created. */
    private final GroupCreator mGroupCreator;

    public ScCallLogGroupBuilder(GroupCreator groupCreator) {
        mGroupCreator = groupCreator;
    }

    /**
     * Finds all groups of adjacent entries in the call log which should be grouped together and
     * calls {@link GroupCreator#addGroup(int, int, boolean)} on {@link #mGroupCreator} for each of
     * them.
     * <p>
     * For entries that are not grouped with others, we do not need to create a group of size one.
     * <p>
     * It assumes that the cursor will not change during its execution.
     *
     * @see GroupingListAdapter#addGroups(Cursor)
     */
    public void addGroups(Cursor cursor) {
        final int count = cursor.getCount();
        if (count == 0) {
            return;
        }
        String relativeDateHeader = null;

        int currentGroupSize = 1;
        cursor.moveToFirst();

        String firstNumber = cursor.getString(ScCallLogQuery.NUMBER);   // The number of the first entry in the group.
        int firstCallType = cursor.getInt(ScCallLogQuery.CALL_TYPE);    // This is the type of the first call in the group.

        String itemRelDate;
        long logDate = cursor.getLong(ScCallLogQuery.DATE);
        long currentDay = System.currentTimeMillis() / (60*60*24*1000);

        long dayDiff = currentDay - (logDate / (60*60*24*1000));

        if (dayDiff == 0)
            itemRelDate = "Today";
        else if (dayDiff == 1) 
            itemRelDate = "Yesterday";
        else {
            itemRelDate = SimpleDateFormat.getDateInstance().format(new Date(cursor.getLong(ScCallLogQuery.DATE)));
        }
        relativeDateHeader = itemRelDate;
        ((ExtendedCursor)cursor).setRelDate(relativeDateHeader);

        while (cursor.moveToNext()) {
            // The number of the current row in the cursor.
            final String currentNumber = cursor.getString(ScCallLogQuery.NUMBER);
            final int callType = cursor.getInt(ScCallLogQuery.CALL_TYPE);
            final boolean sameNumber = equalNumbers(firstNumber, currentNumber);
            final boolean shouldGroup;

            // Check if next entry was at the same day. If not set new relative date and remember this
            // Entries within same days have no relative date string 
            dayDiff = currentDay - (cursor.getLong(ScCallLogQuery.DATE) / (60*60*24*1000));
            if (dayDiff == 0)
                itemRelDate = "Today";
            else if (dayDiff == 1)
                itemRelDate = "Yesterday";
            else {
                itemRelDate = SimpleDateFormat.getDateInstance().format(new Date(cursor.getLong(ScCallLogQuery.DATE)));
//                itemRelDate = DateUtils.getRelativeTimeSpanString(cursor.getLong(ScCallLogQuery.DATE),
//                        System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString();
            }
            if (!itemRelDate.equals(relativeDateHeader)) {
                relativeDateHeader = itemRelDate;
                ((ExtendedCursor)cursor).setRelDate(relativeDateHeader);
            }
            else {
                ((ExtendedCursor)cursor).setRelDate(null);                
            }

            if (ScCallLogQuery.isSectionHeader(cursor)) {
                shouldGroup = false;            // Cannot group headers.
            } else if (!sameNumber) {
                // Should only group with calls from the same number.
                shouldGroup = false;
            } else {
                // Incoming, outgoing, and missed calls group together.
                shouldGroup = (callType == ScCalls.INCOMING_TYPE || callType == ScCalls.OUTGOING_TYPE || callType == ScCalls.MISSED_TYPE);
            }

            if (shouldGroup) {
                // Increment the size of the group to include the current call, but do not create
                // the group until we find a call that does not match.
                currentGroupSize++;
            } else {
                // Create a group for the previous set of calls, excluding the current one, but do
                // not create a group for a single call.
                if (currentGroupSize > 1) {
                    addGroup(cursor.getPosition() - currentGroupSize, currentGroupSize);
                }
                // Start a new group; it will include at least the current call.
                currentGroupSize = 1;
                // The current entry is now the first in the group.
                firstNumber = currentNumber;
                firstCallType = callType;
            }
        }
        // If the last set of calls at the end of the call log was itself a group, create it now.
        if (currentGroupSize > 1) {
            addGroup(count - currentGroupSize, currentGroupSize);
        }
    }

    /**
     * Creates a group of items in the cursor.
     * <p>
     * The group is always unexpanded.
     *
     * @see CallLogAdapter#addGroup(int, int, boolean)
     */
    private void addGroup(int cursorPosition, int size) {
        mGroupCreator.addGroup(cursorPosition, size, false);
    }

//    @VisibleForTesting
    boolean equalNumbers(String number1, String number2) {
//        if (PhoneNumberUtils.isUriNumber(number1) || PhoneNumberUtils.isUriNumber(number2)) {  TODO: tel: sip: sips:
        if (false) {
            return compareSipAddresses(number1, number2);
        } else {
            return PhoneNumberUtils.compare(number1, number2);
        }
    }

//    @VisibleForTesting
    boolean compareSipAddresses(String number1, String number2) {
        if (number1 == null || number2 == null) return number1 == number2;

        int index1 = number1.indexOf('@');
        final String userinfo1;
        final String rest1;
        if (index1 != -1) {
            userinfo1 = number1.substring(0, index1);
            rest1 = number1.substring(index1);
        } else {
            userinfo1 = number1;
            rest1 = "";
        }

        int index2 = number2.indexOf('@');
        final String userinfo2;
        final String rest2;
        if (index2 != -1) {
            userinfo2 = number2.substring(0, index2);
            rest2 = number2.substring(index2);
        } else {
            userinfo2 = number2;
            rest2 = "";
        }

        return userinfo1.equals(userinfo2) && rest1.equalsIgnoreCase(rest2);
    }
}
