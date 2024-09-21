/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.systemui.biometrics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.android.systemui.R

/**
 * Surface View for providing the Global High-Brightness Mode (GHBM) illumination for UDFPS.
 */

private const val TAG = "UdfpsSurfaceView"
class UdfpsSurfaceView(context: Context, attrs: AttributeSet?) : SurfaceView(context, attrs),
    SurfaceHolder.Callback {
    /**
     * Notifies [UdfpsView] when to enable GHBM illumination.
     */
    interface GhbmIlluminationListener {
        /**
         * @param surface the surface for which GHBM should be enabled.
         * @param onIlluminatedRunnable a runnable that should be run after GHBM is enabled.
         */
        fun enableGhbm(surface: Surface, onIlluminatedRunnable: Runnable?)
    }

    private val mHolder: SurfaceHolder
    private val mSensorPaint: Paint
    private var mGhbmIlluminationListener: GhbmIlluminationListener? = null
    private var mOnIlluminatedRunnable: Runnable? = null
    private var mAwaitingSurfaceToStartIllumination = false
    private var mHasValidSurface = false
    private var mEnrolling = false
    private val mUdfpsIconPressed: Drawable?

    init {

        // Make this SurfaceView draw on top of everything else in this window. This allows us to
        // 1) Always show the HBM circle on top of everything else, and
        // 2) Properly composite this view with any other animations in the same window no matter
        //    what contents are added in which order to this view hierarchy.
        setZOrderOnTop(true)
        mHolder = holder
        mHolder.addCallback(this)
        mHolder.setFormat(PixelFormat.RGBA_8888)
        mSensorPaint = Paint(0 /* flags */)
        mSensorPaint.isAntiAlias = true
        mSensorPaint.setColor(context.getColor(R.color.config_udfpsColor))
        mSensorPaint.style = Paint.Style.FILL
        mUdfpsIconPressed = context.getDrawable(R.drawable.udfps_icon_pressed)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        mHasValidSurface = true
        if (mAwaitingSurfaceToStartIllumination) {
            doIlluminate(mOnIlluminatedRunnable)
            mOnIlluminatedRunnable = null
            mAwaitingSurfaceToStartIllumination = false
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Unused.
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        mHasValidSurface = false
    }

    fun setGhbmIlluminationListener(listener: GhbmIlluminationListener?) {
        mGhbmIlluminationListener = listener
    }

    /**
     * Note: there is no corresponding method to stop GHBM illumination. It is expected that
     * [UdfpsView] will hide this view, which would destroy the surface and remove the
     * illumination dot.
     */
    fun startGhbmIllumination(onIlluminatedRunnable: Runnable?) {
        if (mGhbmIlluminationListener == null) {
            Log.e(TAG, "startIllumination | mGhbmIlluminationListener is null")
            return
        }
        if (mHasValidSurface) {
            doIlluminate(onIlluminatedRunnable)
        } else {
            mAwaitingSurfaceToStartIllumination = true
            mOnIlluminatedRunnable = onIlluminatedRunnable
        }
    }

    private fun doIlluminate(onIlluminatedRunnable: Runnable?) {
        if (mGhbmIlluminationListener == null) {
            Log.e(TAG, "doIlluminate | mGhbmIlluminationListener is null")
            return
        }
        mGhbmIlluminationListener!!.enableGhbm(mHolder.getSurface(), onIlluminatedRunnable)
    }

    /**
     * Immediately draws the illumination dot on this SurfaceView's surface.
     */
    fun drawIlluminationDot(sensorRect: RectF) {
        if (!mHasValidSurface) {
            Log.e(TAG, "drawIlluminationDot | the surface is destroyed or was never created.")
            return
        }
        var canvas: Canvas? = null
        try {
            canvas = mHolder.lockCanvas()
            val addDotSize =
                resources.getDimensionPixelSize(R.dimen.udfps_enroll_dot_additional_size)
            if (addDotSize > 0 && mEnrolling) {
                val newRadius = (sensorRect.right - sensorRect.left) / 2 + addDotSize
                val centerX = sensorRect.centerX()
                val centerY = sensorRect.centerY()
                sensorRect[centerX - newRadius, centerY - newRadius, centerX + newRadius] =
                    centerY + newRadius
            }
            mUdfpsIconPressed!!.setBounds(
                Math.round(sensorRect.left),
                Math.round(sensorRect.top),
                Math.round(sensorRect.right),
                Math.round(sensorRect.bottom)
            )
            mUdfpsIconPressed.draw(canvas)
            canvas.drawOval(sensorRect, mSensorPaint)
        } finally {
            // Make sure the surface is never left in a bad state.
            if (canvas != null) {
                mHolder.unlockCanvasAndPost(canvas)
            }
        }
    }

    fun setEnrolling(enrolling: Boolean) {
        mEnrolling = enrolling
    }
}
