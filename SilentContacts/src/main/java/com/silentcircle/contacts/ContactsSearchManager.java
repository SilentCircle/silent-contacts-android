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
 * Copyright (C) 2010 The Android Open Source Project
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

package com.silentcircle.contacts;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.silentcircle.contacts.list.ContactsRequest;
import com.silentcircle.silentcontacts.ScContactsContract;
import com.silentcircle.silentcontacts.ScContactsContract.Intents.UI;

/**
 * A convenience class that helps launch contact search from within the app.
 */
public class ContactsSearchManager {

    /**
     * An extra that provides context for search UI and defines the scope for
     * the search queries.
     */
    public static final String ORIGINAL_REQUEST_KEY = "originalRequest";

    /**
     * Starts the contact list activity in the search mode.
     */
    public static void startSearch(Activity context, String initialQuery) {
        context.startActivity(buildIntent(context, initialQuery, null));
    }

    public static void startSearchForResult(Activity context, String initialQuery, int requestCode, ContactsRequest originalRequest) {
        context.startActivityForResult(
                buildIntent(context, initialQuery, originalRequest), requestCode);
    }

    public static void startSearch(Activity context, String initialQuery, ContactsRequest originalRequest) {
        context.startActivity(buildIntent(context, initialQuery, originalRequest));
    }

    private static Intent buildIntent(Activity context, String initialQuery, ContactsRequest originalRequest) {
        Intent intent = new Intent();
        intent.setData(ScContactsContract.RawContacts.CONTENT_URI);
        intent.setAction(UI.FILTER_CONTACTS_ACTION);

        Intent originalIntent = context.getIntent();
        Bundle originalExtras = originalIntent.getExtras();
        if (originalExtras != null) {
            intent.putExtras(originalExtras);
        }
        intent.putExtra(UI.FILTER_TEXT_EXTRA_KEY, initialQuery);
        if (originalRequest != null) {
            intent.putExtra(ORIGINAL_REQUEST_KEY, originalRequest);
        }
        return intent;
    }
}
