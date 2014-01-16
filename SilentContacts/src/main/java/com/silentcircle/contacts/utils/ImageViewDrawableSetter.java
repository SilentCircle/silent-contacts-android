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
 * Copyright (C) 2012 The Android Open Source Project
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

package com.silentcircle.contacts.utils;

import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.util.Log;
import android.widget.ImageView;

import com.silentcircle.contacts.model.Contact;
import com.silentcircle.contacts.ContactPhotoManager;

import java.util.Arrays;

/**
 * Initialized with a target ImageView. When provided with a compressed image
 * (i.e. a byte[]), it appropriately updates the ImageView's Drawable.
 */
public class ImageViewDrawableSetter {
    private ImageView mTarget;
    private byte[] mCompressed;
    private Drawable mPreviousDrawable;
    private int mDurationInMillis = 0;
    private static final String TAG = "ImageViewDrawableSetter";

    public ImageViewDrawableSetter() {
    }

    public ImageViewDrawableSetter(ImageView target) {
        mTarget = target;
    }

    public void setupContactPhoto(Contact contactData, ImageView photoView) {
        setTarget(photoView);
        setCompressedImage(contactData.getPhotoBinaryData());
    }

    public void setTransitionDuration(int durationInMillis) {
        mDurationInMillis = durationInMillis;
    }

    public ImageView getTarget() {
        return mTarget;
    }

    /**
     * Re-initialize to use new target. As a result, the next time a new image
     * is set, it will immediately be applied to the target (there will be no
     * fade transition).
     */
    protected void setTarget(ImageView target) {
        if (mTarget != target) {
            mTarget = target;
            mCompressed = null;
            mPreviousDrawable = null;
        }
    }

    protected byte[] getCompressedImage() {
        return mCompressed;
    }

    protected Bitmap setCompressedImage(byte[] compressed) {
        if (mPreviousDrawable == null) {
            // If we don't already have a drawable, skip the exit-early test
            // below; otherwise we might not end up setting the default image.
        } else if (mPreviousDrawable != null && Arrays.equals(mCompressed, compressed)) {
            // TODO: the worst case is when the arrays are equal but not
            // identical. This takes about 1ms (more with high-res photos). A
            // possible optimization is to sparsely sample chunks of the arrays
            // to compare.
            return previousBitmap();
        }

        final Drawable newDrawable = (compressed == null)
                ? defaultDrawable()
                : decodedBitmapDrawable(compressed);

        // Remember this for next time, so that we can check if it changed.
        mCompressed = compressed;

        // If we don't have a new Drawable, something went wrong... bail out.
        if (newDrawable == null) return previousBitmap();

        if (mPreviousDrawable == null || mDurationInMillis == 0) {
            // Set the new one immediately.
            mTarget.setImageDrawable(newDrawable);
        } else {
            // Set up a transition from the previous Drawable to the new one.
            final Drawable[] beforeAndAfter = new Drawable[2];
            beforeAndAfter[0] = mPreviousDrawable;
            beforeAndAfter[1] = newDrawable;
            final TransitionDrawable transition = new TransitionDrawable(beforeAndAfter);
            mTarget.setImageDrawable(transition);
            transition.startTransition(mDurationInMillis);
        }

        // Remember this for next time, so that we can transition from it to the
        // new one.
        mPreviousDrawable = newDrawable;

        return previousBitmap();
    }

    private Bitmap previousBitmap() {
        return (mPreviousDrawable == null)
                ? null
                : ((BitmapDrawable) mPreviousDrawable).getBitmap();
    }

    /**
     * Obtain the default drawable for a contact when no photo is available.
     */
    private Drawable defaultDrawable() {
        Resources resources = mTarget.getResources();
        final int resId = ContactPhotoManager.getDefaultAvatarResId(true, true);
        try {
            return resources.getDrawable(resId);
        } catch (NotFoundException e) {
            Log.wtf(TAG, "Cannot load default avatar resource.");
            return null;
        }
    }

    private BitmapDrawable decodedBitmapDrawable(byte[] compressed) {
        Resources rsrc = mTarget.getResources();
        Bitmap bitmap = BitmapFactory.decodeByteArray(compressed, 0, compressed.length);
        return new BitmapDrawable(rsrc, bitmap);
    }

}
