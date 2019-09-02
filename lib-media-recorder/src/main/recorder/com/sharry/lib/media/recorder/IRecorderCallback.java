package com.sharry.lib.media.recorder;

import androidx.annotation.IntDef;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * 视频录制回调
 *
 * @author Sharry <a href="xiaoyu.zhu@1hai.cn">Contact me.</a>
 * @version 1.0
 * @since 12/29/2018 1:52 PM
 */
public interface IRecorderCallback {

    class Adapter implements IRecorderCallback {

        @Override
        public void onStart() {

        }

        @Override
        public void onProgress(long time) {

        }

        @Override
        public void onCancel() {

        }

        @Override
        public void onPause() {

        }

        @Override
        public void onResume() {

        }

        @Override
        public void onComplete(@NonNull File file) {

        }

        @Override
        public void onFailed(int errorCode, @NonNull Throwable e) {

        }
    }

    /**
     * Outer Constants.
     */
    int ERROR_CREATE_FILE_FAILED = 1;        // 创建音频输出文件失败
    int ERROR_ENCODER_PREPARE_FAILED = 2;    // 编码器准备失败
    int ERROR_START_FAILED = 3;              // 录制开始失败
    int ERROR_UNSUPPORTED_TYPE = 4;          // 不支持的类型
    int ERROR_ENCODE_FAILED = 5;             // 编码失败
    int ERROR_MUXER_FAILED = 6;              // 音视频合并失败

    /**
     * Error code.
     */
    @IntDef(flag = true, value = {
            ERROR_CREATE_FILE_FAILED,
            ERROR_ENCODER_PREPARE_FAILED,
            ERROR_START_FAILED,
            ERROR_UNSUPPORTED_TYPE,
            ERROR_ENCODE_FAILED,
            ERROR_MUXER_FAILED
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface ErrorCode {

    }

    @MainThread
    void onStart();

    /**
     * 单位为 mm
     */
    @MainThread
    void onProgress(long time);

    @MainThread
    void onCancel();

    @MainThread
    void onPause();

    @MainThread
    void onResume();

    @MainThread
    void onComplete(@NonNull File file);

    @MainThread
    void onFailed(@ErrorCode int errorCode, @NonNull Throwable e);

}
