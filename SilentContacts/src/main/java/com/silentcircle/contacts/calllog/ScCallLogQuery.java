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

import com.silentcircle.silentcontacts.ScCallLog.ScCalls;

import android.database.Cursor;


/**
 * The query for the call log table.
 */
public final class ScCallLogQuery {
    // If you alter this, you must also alter the method that inserts a fake row to the headers
    // in the CallLogQueryHandler class called createHeaderCursorFor().
    public static final String[] _PROJECTION = new String[] {
            ScCalls._ID,                       // 0
            ScCalls.NUMBER,                    // 1
            ScCalls.DATE,                      // 2
            ScCalls.DURATION,                  // 3
            ScCalls.TYPE,                      // 4
            ScCalls.COUNTRY_ISO,               // 5
            ScCalls.GEOCODED_LOCATION,         // 6
            ScCalls.CACHED_NAME,               // 7
            ScCalls.CACHED_NUMBER_TYPE,        // 8
            ScCalls.CACHED_NUMBER_LABEL,       // 9
            ScCalls.CACHED_LOOKUP_URI,         // 10
            ScCalls.CACHED_MATCHED_NUMBER,     // 11
            ScCalls.CACHED_NORMALIZED_NUMBER,  // 12
            ScCalls.CACHED_PHOTO_ID,           // 13
            ScCalls.CACHED_FORMATTED_NUMBER,   // 14
            ScCalls.IS_READ,                   // 15
    };

    public static final int ID = 0;
    public static final int NUMBER = 1;
    public static final int DATE = 2;
    public static final int DURATION = 3;
    public static final int CALL_TYPE = 4;
    public static final int COUNTRY_ISO = 5;
    public static final int GEOCODED_LOCATION = 6;
    public static final int CACHED_NAME = 7;
    public static final int CACHED_NUMBER_TYPE = 8;
    public static final int CACHED_NUMBER_LABEL = 9;
    public static final int CACHED_LOOKUP_URI = 10;
    public static final int CACHED_MATCHED_NUMBER = 11;
    public static final int CACHED_NORMALIZED_NUMBER = 12;
    public static final int CACHED_PHOTO_ID = 13;
    public static final int CACHED_FORMATTED_NUMBER = 14;
    public static final int IS_READ = 15;
    /** The indices of the synthetic "section" and relative date columns in the extended projection. */
    public static final int SECTION = 16;
    public static final int RELATIVE_DATE = 17;

    /**
     * The name of the synthetic "section" column.
     * <p>
     * This column identifies whether a row is a header or an actual item, and whether it is
     * part of the new or old calls.
     */
    public static final String SECTION_NAME = "section";

    /**
     * The name of the synthetic "relative date" column.
     * <p>
     * This column identifies whether a row shall display a date, maybe a relative date (today, yesterday)
     */
    public static final String REL_DATE_NAME = "relative_date";
    
    
    /** The value of the "section" column for the header of the new section. */
    public static final int SECTION_NEW_HEADER = 0;
    /** The value of the "section" column for the items of the new section. */
    public static final int SECTION_NEW_ITEM = 1;
    /** The value of the "section" column for the header of the old section. */
    public static final int SECTION_OLD_HEADER = 2;
    /** The value of the "section" column for the items of the old section. */
    public static final int SECTION_OLD_ITEM = 3;

    /** The call log projection including the section name. */
    public static final String[] EXTENDED_PROJECTION;
    static {
        EXTENDED_PROJECTION = new String[_PROJECTION.length + 2];
        System.arraycopy(_PROJECTION, 0, EXTENDED_PROJECTION, 0, _PROJECTION.length);
        EXTENDED_PROJECTION[_PROJECTION.length-1] = SECTION_NAME;
        EXTENDED_PROJECTION[_PROJECTION.length] = REL_DATE_NAME;
    }

    public static boolean isSectionHeader(Cursor cursor) {
        int section = cursor.getInt(ScCallLogQuery.SECTION);
        return section == ScCallLogQuery.SECTION_NEW_HEADER || section == ScCallLogQuery.SECTION_OLD_HEADER;
    }

    public static boolean isNewSection(Cursor cursor) {
        int section = cursor.getInt(ScCallLogQuery.SECTION);
        return section == ScCallLogQuery.SECTION_NEW_ITEM || section == ScCallLogQuery.SECTION_NEW_HEADER;
    }
}