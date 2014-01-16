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
 * Copyright (C) 2009 The Android Open Source Project
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

import android.accounts.Account;

import com.silentcircle.silentcontacts.ScContactsContract.CommonDataKinds.Email;
import com.silentcircle.silentcontacts.ScContactsContract.CommonDataKinds.Im;
import com.silentcircle.silentcontacts.ScContactsContract.CommonDataKinds.Organization;
import com.silentcircle.silentcontacts.ScContactsContract.CommonDataKinds.Phone;
import com.silentcircle.silentcontacts.ScContactsContract.CommonDataKinds.StructuredPostal;
import com.silentcircle.contacts.model.RawContactModifier;
import com.silentcircle.contacts.utils.Constants;

/**
 * This class contains utility functions for determining the precedence of
 * different types associated with contact data items.
 *
 * @deprecated use {@link RawContactModifier#getTypePrecedence} instead, since this
 *             list isn't {@link Account} based.
 */
@Deprecated
public final class TypePrecedence {

    /* This utility class has cannot be instantiated.*/
    private TypePrecedence() {}

    //TODO These may need to be tweaked.
    private static final int[] TYPE_PRECEDENCE_PHONES = {
            Phone.TYPE_CUSTOM,
//            Phone.TYPE_MAIN,
            Phone.TYPE_SILENT,
            Phone.TYPE_MOBILE,
            Phone.TYPE_HOME,
            Phone.TYPE_WORK,
//            Phone.TYPE_OTHER,
//            Phone.TYPE_FAX_HOME,
//            Phone.TYPE_FAX_WORK,
//            Phone.TYPE_PAGER
    };

    private static final int[] TYPE_PRECEDENCE_EMAIL = {
            Email.TYPE_CUSTOM,
            Email.TYPE_HOME,
            Email.TYPE_WORK,
            Email.TYPE_OTHER};

    private static final int[] TYPE_PRECEDENCE_POSTAL = {
            StructuredPostal.TYPE_CUSTOM,
            StructuredPostal.TYPE_HOME,
            StructuredPostal.TYPE_WORK,
            StructuredPostal.TYPE_OTHER};

    private static final int[] TYPE_PRECEDENCE_IM = {
            Im.TYPE_CUSTOM,
            Im.TYPE_SILENT,
            Im.TYPE_HOME,
            Im.TYPE_WORK,
            Im.TYPE_OTHER};

    private static final int[] TYPE_PRECEDENCE_ORG = {
            Organization.TYPE_CUSTOM,
            Organization.TYPE_WORK,
            Organization.TYPE_OTHER};

    /**
     * Returns the precedence (1 being the highest) of a type in the context of it's mimetype.
     *
     * @param mimetype The mimetype of the data with which the type is associated.
     * @param type The integer type as defined in {@Link ContactsContract#CommonDataKinds}.
     * @return The integer precedence, where 1 is the highest.
     */
    @Deprecated
    public static int getTypePrecedence(String mimetype, int type) {
        int[] typePrecedence = getTypePrecedenceList(mimetype);
        if (typePrecedence == null) {
            return -1;
        }

        for (int i = 0; i < typePrecedence.length; i++) {
            if (typePrecedence[i] == type) {
                return i;
            }
        }
        return typePrecedence.length;
    }

    @Deprecated
    private static int[] getTypePrecedenceList(String mimetype) {
        if (mimetype.equals(Phone.CONTENT_ITEM_TYPE)) {
            return TYPE_PRECEDENCE_PHONES;
        } else if (mimetype.equals(Email.CONTENT_ITEM_TYPE)) {
            return TYPE_PRECEDENCE_EMAIL;
        } else if (mimetype.equals(StructuredPostal.CONTENT_ITEM_TYPE)) {
            return TYPE_PRECEDENCE_POSTAL;
        } else if (mimetype.equals(Im.CONTENT_ITEM_TYPE)) {
            return TYPE_PRECEDENCE_IM;
        } else if (mimetype.equals(Constants.MIME_TYPE_VIDEO_CHAT)) {
            return TYPE_PRECEDENCE_IM;
        } else if (mimetype.equals(Organization.CONTENT_ITEM_TYPE)) {
            return TYPE_PRECEDENCE_ORG;
        } else {
            return null;
        }
    }


}
