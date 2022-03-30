/**
 * Copyright (C) 2022 The Android Open Source Project
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
package android.view.cts;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;

public class SDRTestActivity extends Activity
        implements SurfaceHolder.Callback, SurfaceTextureListener {
    private SurfaceView mSurfaceView;
    private TextureView mTextureView;

    @Override
    public void surfaceCreated(SurfaceHolder holder) {}

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mTextureView = new TextureView(this);
        mSurfaceView = new SurfaceView(this);
        mTextureView.setSurfaceTextureListener(this);

        FrameLayout content = new FrameLayout(this);
        content.addView(mSurfaceView,
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        content.addView(mTextureView,
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

        mSurfaceView.getHolder().addCallback(this);
        setContentView(content);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onEnterAnimationComplete() {
        super.onEnterAnimationComplete();
    }

    public TextureView getTextureView() {
        return mTextureView;
    }

    public SurfaceView getSurfaceView() {
        return mSurfaceView;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {}

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
}

