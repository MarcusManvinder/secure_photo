/*
 * Copyright 2014 Chris Banes
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sckftr.android.app.view;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import com.sckftr.android.securephoto.R;

/**
 * A layout that draws something in the insets passed to {@link #fitSystemWindows(android.graphics.Rect)}, i.e. the
 * area above UI chrome (status and navigation bars, overlay action bars).
 */
public class InsetFrameLayout extends FrameLayout {

    private Drawable mDefaultInsetBackground;
    private Drawable mInsetBackground;

    // animation properties
    private int mStartColor, mEndColor;
    private float mAnimationProgress;
    private int mAnimationDuration = 500;

    private ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(this, "animationProgress", 0f, (float) mAnimationDuration).setDuration(mAnimationDuration);

    private Rect mInsets;
    private Rect mTempRect = new Rect();
    private OnInsetsCallback mOnInsetsCallback;

    private int mTopAlpha = 255;

    public InsetFrameLayout(Context context) {
        super(context);

        init(context, null, 0);
    }

    public InsetFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        init(context, attrs, 0);
    }

    public InsetFrameLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        init(context, attrs, defStyle);
    }

    private void init(Context context, AttributeSet attrs, int defStyle) {

        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.DrawInsetsFrameLayout, defStyle, 0);

        if (a == null) return;

        mDefaultInsetBackground = mInsetBackground = a.getDrawable(R.styleable.DrawInsetsFrameLayout_insetBackground);

        a.recycle();

        setWillNotDraw(true);
    }

    @Override
    protected boolean fitSystemWindows(Rect insets) {
        mInsets = new Rect(insets);

        setWillNotDraw(mInsetBackground == null);

        ViewCompat.postInvalidateOnAnimation(this);

        if (mOnInsetsCallback != null) mOnInsetsCallback.onInsetsChanged(insets);

        return true; // consume insets
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        if (mInsets != null && mInsetBackground != null) {

            // Top
            mTempRect.set(0, 0, getWidth(), mInsets.top);

            mInsetBackground.setBounds(mTempRect);

            mInsetBackground.setAlpha(mTopAlpha);

            mInsetBackground.draw(canvas);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mInsetBackground != null) {
            mInsetBackground.setCallback(this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (mInsetBackground != null) mInsetBackground.setCallback(null);

        if (objectAnimator.isRunning()) objectAnimator.cancel();
    }


    public void resetInsetBackground() {
        if (mInsetBackground != mDefaultInsetBackground) {
            setInsetBackground(mDefaultInsetBackground);
        }
    }

    /**
     * Allows the calling container to specify a callback for custom processing when insets change
     * (i.e. when {@link #fitSystemWindows(android.graphics.Rect)} is called. This is useful for setting padding on
     * UI elements based on UI chrome insets (e.g. a Google Map or a ListView). When using with
     * ListView or GridView, remember to set clipToPadding to false.
     */
    public void setOnInsetsCallback(OnInsetsCallback onInsetsCallback) {
        mOnInsetsCallback = onInsetsCallback;
    }

    public void setTopInsetAlpha(int alpha) {
        mTopAlpha = alpha;
        ViewCompat.postInvalidateOnAnimation(this);
    }

    public void setInsetBackgroundColor(int color) {
        Resources r = getResources();

        setInsetBackgroundColorRaw(
                Color.argb(
                        Color.alpha(r.getColor(R.color.chrome_custom_background_alpha)),
                        Color.red(color),
                        Color.green(color),
                        Color.blue(color)
                )
        );
    }

    void setInsetBackgroundColorRaw(int color) {
        setInsetBackground(new ColorDrawable(color));
    }

    private void setInsetBackground(Drawable background) {
        if (mInsetBackground != null) mInsetBackground.setCallback(null);

        mInsetBackground = background;

        if (mInsetBackground != null && getWindowToken() != null)
            mInsetBackground.setCallback(this);

        ViewCompat.postInvalidateOnAnimation(this);
    }

    public void setAnimationProgress(float progress) {
        this.mAnimationProgress = progress;

        float fraction = progress / (float) mAnimationDuration;

        setInsetBackgroundColor(Color.rgb(
                evaluate(fraction, Color.red(mStartColor), Color.red(mEndColor)),     // red
                evaluate(fraction, Color.green(mStartColor), Color.green(mEndColor)), // green
                evaluate(fraction, Color.blue(mStartColor), Color.blue(mEndColor)))); // blue
    }

    public float getAnimationProgress() {
        return mAnimationProgress;
    }

    private static int evaluate(float fraction, int startValue, int endValue) {
        return (int) (startValue + fraction * (endValue - startValue));
    }

    public void setBackgroundDrawableWithAnimation(int colorResId) {

        Resources resources = getResources();

        if (mInsetBackground == null)
            mInsetBackground = new ColorDrawable(resources.getColor(android.R.color.transparent));

        if (!(mInsetBackground instanceof ColorDrawable))
            throw new IllegalArgumentException("Can't animate background changing from non-ColorDrawable!");

        mStartColor = ((ColorDrawable) mInsetBackground).getColor();
        mEndColor = resources.getColor(colorResId);

        if (objectAnimator.isRunning()) objectAnimator.cancel();

        objectAnimator.start();
    }

    public void setBackgroundDrawableWithAnimation(int startColorResID, int endColorResID) {

        Resources resources = getResources();

        if (mInsetBackground == null)
            mInsetBackground = new ColorDrawable(resources.getColor(startColorResID));

        mStartColor = resources.getColor(startColorResID);
        mEndColor = resources.getColor(endColorResID);

        if (objectAnimator.isRunning()) objectAnimator.cancel();

        objectAnimator.start();
    }

    public static interface OnInsetsCallback {
        public void onInsetsChanged(Rect insets);
    }
}