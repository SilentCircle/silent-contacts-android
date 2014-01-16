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
package com.silentcircle.contacts.widget;

import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

/**
 * A container that places a masking view on top of all other views.  The masking view can be
 * faded in and out.  Currently, the masking view is solid color black.
 */
public class TransitionAnimationView extends FrameLayout {
    private View mMaskingView;
    private ObjectAnimator mAnimator;

    public TransitionAnimationView(Context context) {
        this(context, null, 0);
    }

    public TransitionAnimationView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TransitionAnimationView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            return;
        mMaskingView = new View(getContext());
        mMaskingView.setVisibility(View.INVISIBLE);
        mMaskingView.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        mMaskingView.setBackgroundColor(Color.BLACK);
        addView(mMaskingView);
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public void setMaskVisibility(boolean flag) {
        if (flag) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
                mMaskingView.setAlpha(1.0f);
            mMaskingView.setVisibility(View.VISIBLE);
        }
        else {
            mMaskingView.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Starts the transition of showing or hiding the mask.
     * If showMask is true, the mask will be set to be invisible then fade into hide the other
     * views in this container.  If showMask is false, the mask will be set to be hide other views
     * initially.  Then, the other views in this container will be revealed.
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public void startMaskTransition(boolean showMask) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            return;
        // Stop any animation that may still be running.
        if (mAnimator != null && mAnimator.isRunning()) {
            mAnimator.end();
        }

        mMaskingView.setVisibility(View.VISIBLE);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            return;
        if (showMask) {
            mAnimator = ObjectAnimator.ofFloat(mMaskingView, View.ALPHA, 0.0f, 1.0f);
            mAnimator.start();
        }
        else {
            // asked to hide the view
            mAnimator = ObjectAnimator.ofFloat(mMaskingView, View.ALPHA, 1.0f, 0.0f);
            mAnimator.start();
        }
    }
}
