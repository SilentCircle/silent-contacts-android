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

import android.database.AbstractCursor;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;

import com.silentcircle.contacts.utils.Closeables;

/**
 * Wraps a cursor to add an additional column with the same value for all rows.
 * <p>
 * The number of rows in the cursor and the set of columns is determined by the cursor being
 * wrapped.
 */
public class ExtendedCursor extends AbstractCursor {
    /** The cursor to wrap. */
    private final Cursor mCursor;

    /** 
     * The name of the first additional column.
     */
    private final String mColumnName_1;
    /** 
     * The value to be assigned to the first additional column.
     */
    private final Object mValue_1;

    /** 
     * The name of the second additional column.
     */
    private final String mColumnName_2;
    /** 
     * The value to be assigned to the second additional column.
     */
    private final Object mValue_2[];
    /**
     * Creates a new cursor which extends the given cursor by adding a column with a constant value.
     *
     * @param cursor the cursor to extend
     * @param columnName_1 the name of the first additional column
     * @param value_1 the value to be assigned to the first additional column
     * @param columnName_2 the name of the second additional column
     * @param value_2 the value to be assigned to the second additional column
     */
    public ExtendedCursor(Cursor cursor, String columnName_1, Object value_1, String columnName_2) {
        mCursor = cursor;
        mColumnName_1 = columnName_1;
        mValue_1 = value_1;
        mColumnName_2 = columnName_2;
        mValue_2 = new Object[mCursor.getCount()];
    }

    @Override
    public int getCount() {
        return mCursor.getCount();
    }

    @Override
    public String[] getColumnNames() {
        String[] columnNames = mCursor.getColumnNames();
        int length = columnNames.length;
        String[] extendedColumnNames = new String[length + 2];
        System.arraycopy(columnNames, 0, extendedColumnNames, 0, length);
        extendedColumnNames[length-1] = mColumnName_1;
        extendedColumnNames[length] = mColumnName_2;
        return extendedColumnNames;
    }

    public void setRelDate(String relDate) {
        mValue_2[mCursor.getPosition()] = relDate;
    }

    @Override
    public String getString(int column) {
        if (column == mCursor.getColumnCount()) {
            return (String) mValue_1;
        }
        else if (column == mCursor.getColumnCount()+1) {
            return (String) mValue_2[mCursor.getPosition()];
        }
        return mCursor.getString(column);
    }

    @Override
    public short getShort(int column) {
        if (column == mCursor.getColumnCount()) {
            return (Short) mValue_1;
        }
        else if (column == mCursor.getColumnCount()+1) {
            return (Short) mValue_2[mCursor.getPosition()];
        }
        return mCursor.getShort(column);
    }

    @Override
    public int getInt(int column) {
        if (column == mCursor.getColumnCount()) {
            return (Integer) mValue_1;
        }
        else if (column == mCursor.getColumnCount()+1) {
            return (Integer) mValue_2[mCursor.getPosition()];
        }
        return mCursor.getInt(column);
    }

    @Override
    public long getLong(int column) {
        if (column == mCursor.getColumnCount()) {
            return (Long) mValue_1;
        }
        else if (column == mCursor.getColumnCount()+1) {
            return (Long)mValue_2[mCursor.getPosition()];
        }
        return mCursor.getLong(column);
    }

    @Override
    public float getFloat(int column) {
        if (column == mCursor.getColumnCount()) {
            return (Float) mValue_1;
        }
        else if (column == mCursor.getColumnCount()+1) {
            return (Float)mValue_2[mCursor.getPosition()];
        }
        return mCursor.getFloat(column);
    }

    @Override
    public double getDouble(int column) {
        if (column == mCursor.getColumnCount()) {
            return (Double) mValue_1;
        }
        else if (column == mCursor.getColumnCount()+1) {
            return (Double) mValue_2[mCursor.getPosition()];
        }
        return mCursor.getDouble(column);
    }

    @Override
    public boolean isNull(int column) {
        if (column == mCursor.getColumnCount()) {
            return mValue_1 == null;
        }
        else if (column == mCursor.getColumnCount()+1) {
            return mValue_2[mCursor.getPosition()] == null;
        }
        return mCursor.isNull(column);
    }

    @Override
    public boolean onMove(int oldPosition, int newPosition) {
        return mCursor.moveToPosition(newPosition);
    }

    @Override
    public void close() {
        Closeables.closeQuietly(mCursor);
        super.close();
    }

    @Override
    public void registerContentObserver(ContentObserver observer) {
        mCursor.registerContentObserver(observer);
    }

    @Override
    public void unregisterContentObserver(ContentObserver observer) {
        mCursor.unregisterContentObserver(observer);
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        mCursor.registerDataSetObserver(observer);
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        mCursor.unregisterDataSetObserver(observer);
    }
}
