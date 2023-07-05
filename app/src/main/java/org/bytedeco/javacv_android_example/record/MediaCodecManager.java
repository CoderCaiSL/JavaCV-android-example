package org.bytedeco.javacv_android_example.record;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author: CaiSongL
 * @date: 2023/6/18 21:35
 */
class MediaCodecManager {
    private static final String MIME_TYPE = "video/avc"; // 视频编码类型，这里使用 H.264
    private static final int FRAME_RATE = 30; // 视频帧率
    private static final int BIT_RATE = 1000000; // 视频比特率
    private static final int I_FRAME_INTERVAL = 1; // 关键帧间隔

    private MediaCodec mediaCodec;
    private MediaMuxer mediaMuxer;
    private int videoTrackIndex;
    private boolean isRecording = false;
    private int width = 540;
    private int height = 460;
    public void startStreaming() {
        try {
            mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
            MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height); // 替换为您的视频宽度和高度
            format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mediaCodec.start();
            isRecording = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void encodeFrame(byte[] input) {
        try {
            ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
            ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();

            int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();
                inputBuffer.put(input);

                mediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, 0, 0);
            }

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
            while (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];

                // 将编码后的数据写入到文件或上传到服务器
                writeEncodedData(outputBuffer, bufferInfo);

                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void writeEncodedData(ByteBuffer buffer, MediaCodec.BufferInfo bufferInfo) {
        if (isRecording) {

//            // 创建 VideoFrame.Buffer 对象
//            VideoFrame.Buffer frameBuffer = new NV12Buffer(width, height, width, height, buffer, null);
//
//            // 获取 SDK 当前的 Monotonic Time
//            long currentMonotonicTimeInMs = engine.getCurrentMonotonicTimeInMs();
//
//            // 创建 VideoFrame，并将 SDK 当前的 Monotonic Time 赋值到 VideoFrame 的时间戳参数
//            VideoFrame videoFrame = new VideoFrame(frameBuffer, 0, currentMonotonicTimeInMs);
//
//            // 通过视频轨道推送视频帧到 SDK
//            int ret = engine.pushExternalVideoFrameEx(videoFrame, videoTrack);
//            if (ret < 0) {
//                Log.w(TAG, "pushExternalVideoFrameEx error code=" + ret);
//            }

            // 释放 VideoFrame
//            videoFrame.release();
//            mediaMuxer.writeSampleData(videoTrackIndex, buffer, bufferInfo);
        }
    }


}
