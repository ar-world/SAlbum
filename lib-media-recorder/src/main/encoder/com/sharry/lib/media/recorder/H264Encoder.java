package com.sharry.lib.media.recorder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.sharry.lib.opengles.EglCore;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * H.264 编码类
 *
 * @author Sharry <a href="xiaoyu.zhu@1hai.cn">Contact me.</a>
 * @version 1.0
 * @since 1/28/2019 3:05 PM
 */
public class H264Encoder implements IVideoEncoder {

    private static final String TAG = H264Encoder.class.getSimpleName();

    /**
     * H.264 encode type.
     */
    private static final String MIME_TYPE = "video/avc";

    private final Object mPauseLock = new Object();
    private final MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();

    /**
     * 以下的属性在 prepare 中初始化
     */
    private MediaCodec mImpl;
    private long mVideoPts = 0;
    private Context mContext;
    private Surface mInputSurface;
    private RendererThread mRenderThread;
    private EncodeThread mEncodeThread;

    private volatile boolean mIsEncoding;
    private volatile boolean mIsPausing;

    @Override
    public void prepare(@NonNull Context context) throws IOException {
        mContext = context;
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, mContext.frameWidth, mContext.frameHeight);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mContext.frameRate);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mContext.frameWidth * mContext.frameHeight * 4);
        // 创建编码器
        mImpl = MediaCodec.createEncoderByType(MIME_TYPE);
        mImpl.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        // 将 Camera 中的数据拷贝到这个 Surface 上, 当做输入流
        mInputSurface = mImpl.createInputSurface();
        // 创建线程
        mRenderThread = new RendererThread();
        mEncodeThread = new EncodeThread();
    }

    @Override
    public void start() {
        mIsEncoding = true;
        mRenderThread.start();
        mEncodeThread.start();
    }

    @Override
    public void pause() {
        mIsPausing = true;
    }

    @Override
    public void resume() {
        mIsPausing = false;
        synchronized (mPauseLock) {
            mPauseLock.notify();
        }
    }

    @Override
    public void stop() {
        mIsEncoding = false;
        mIsPausing = false;
        synchronized (mPauseLock) {
            mPauseLock.notify();
        }
        try {
            mRenderThread.join();
        } catch (Throwable e) {
            Log.w(TAG, e.getMessage(), e);
        } finally {
            mRenderThread = null;
        }
        try {
            mEncodeThread.join();
        } catch (Throwable e) {
            Log.w(TAG, e.getMessage(), e);
        } finally {
            mEncodeThread = null;
        }
    }

    /**
     * 录制的渲染线程
     */
    private final class RendererThread extends Thread {

        private boolean mIsContextCreated = true;
        private boolean mIsSizeChanged = true;
        private final EglCore mEglCore;
        private final H264Render mRenderer;

        RendererThread() {
            mEglCore = new EglCore();
            mRenderer = new H264Render(mContext.textureId);
        }

        @Override
        public void run() {
            while (mIsEncoding) {
                if (mIsPausing) {
                    synchronized (mPauseLock) {
                        try {
                            mPauseLock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    continue;
                }
                if (mIsContextCreated) {
                    // 初始化创建 EGL 环境，然后回调 Renderer
                    mEglCore.initialize(mInputSurface, mContext.eglContext);
                    mRenderer.onEGLContextCreated();
                    mIsContextCreated = false;
                }
                // 说明手机横竖屏切换, 导致尺寸变更了
                if (mIsSizeChanged) {
                    mRenderer.onSurfaceChanged(mContext.frameWidth, mContext.frameHeight);
                    mIsSizeChanged = true;
                }
                // 不停的绘制
                mRenderer.onDrawFrame();
                // 将绘制的数据交换到 mInputSurface 中
                mEglCore.swapBuffers();
            }
            onDestroy();
        }

        private void onDestroy() {
            mEglCore.release();
        }
    }

    /**
     * 录制编码的线程
     */
    public final class EncodeThread extends Thread {

        @Override
        public void run() {
            mImpl.start();
            while (mIsEncoding) {
                if (mIsPausing) {
                    synchronized (mPauseLock) {
                        try {
                            mPauseLock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    continue;
                }
                int indexOfOutputBuffer = mImpl.dequeueOutputBuffer(mBufferInfo, 0);
                if (indexOfOutputBuffer == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    mContext.callback.onVideoFormatChanged(mImpl.getOutputFormat());
                } else {
                    while (indexOfOutputBuffer >= 0) {
                        // 3.1 处理编码后的输出数据
                        ByteBuffer outputBuffer = null;
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                            outputBuffer = mImpl.getOutputBuffer(indexOfOutputBuffer);
                        } else {
                            outputBuffer = mImpl.getOutputBuffers()[indexOfOutputBuffer];
                        }
                        if (null == outputBuffer) {
                            continue;
                        }
                        outputBuffer.position(mBufferInfo.offset);
                        outputBuffer.limit(mBufferInfo.offset + mBufferInfo.size);
                        // 记录时间戳, 用于 Muxer 时和音频同步
                        if (mVideoPts == 0) {
                            mVideoPts = mBufferInfo.presentationTimeUs;
                        }
                        mBufferInfo.presentationTimeUs -= mVideoPts;
                        // 回调 onAudioEncoded
                        mContext.callback.onVideoEncoded(outputBuffer, mBufferInfo);
                        // 3.2 释放指定位置的输出缓冲流
                        mImpl.releaseOutputBuffer(indexOfOutputBuffer, false);
                        indexOfOutputBuffer = mImpl.dequeueOutputBuffer(mBufferInfo, 0);
                    }
                }
            }
            try {
                mImpl.flush();
            } catch (Exception e) {
                Log.w(TAG, e.getMessage(), e);
            }
            try {
                mImpl.stop();
            } catch (Throwable e) {
                Log.w(TAG, e.getMessage(), e);
            }
            try {
                mImpl.release();
            } catch (Throwable e) {
                Log.w(TAG, e.getMessage(), e);
            }
        }
    }

}
