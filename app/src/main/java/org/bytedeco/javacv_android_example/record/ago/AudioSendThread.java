package org.bytedeco.javacv_android_example.record.ago;

import android.content.Context;
import android.util.Log;

import org.bytedeco.javacv_android_example.R;

import io.agora.rtc.AgoraRtcService;
import io.agora.rtc.AgoraRtcService.AudioDataType;
import io.agora.rtc.AgoraRtcService.AudioFrameInfo;

public class AudioSendThread  extends Thread {
    private final String TAG = "RTSADEMO/AudioSendThread";

    private final Object mExitEvent = new Object();
    private AgoraRtcService mRtcService;
    private String mChannelName;
    private int mConnectionId;
    private volatile boolean mRunning = false;

    private Context mContext;
    private int mSampleRate;
    private int mChannelNumber;
    private int mSampleBytes;


    //////////////////////////////////////////////////////////////////
    ///////////////////// Public Methods ////////////////////////////
    ////////////////////////////////////////////////////////////////
    public AudioSendThread(Context ctx, AgoraRtcService rtcService, String chnlName, int connectionId,
                           int sampleRate, int channelNumber, int sampleBytes) {
        mContext = ctx;
        mRtcService = rtcService;
        mChannelName = chnlName;
        mConnectionId = connectionId;
        mSampleRate = sampleRate;
        mChannelNumber = channelNumber;
        mSampleBytes = sampleBytes;
    }

    public boolean sendStart() {
        try {
            mRunning = true;
            this.start();
        } catch (IllegalThreadStateException exp) {
            exp.printStackTrace();
            mRunning = false;
            return false;
        }
        return true;
    }

    public void sendStop() {
        mRunning = false;
        synchronized (mExitEvent) {
            try {
                mExitEvent.wait(200);
            } catch (InterruptedException interruptExp) {
                interruptExp.printStackTrace();
            }
        }
    }

    @Override
    public void run() {
        Log.d(TAG, "<AudioSendThread.run> ==>Enter");
        StreamFile  audioStream = new StreamFile();
        audioStream.open(mContext, R.raw.send_audio_16k_1ch);

        int bytesPerSec = mSampleRate*mChannelNumber*mSampleBytes;
        int bufferSize = bytesPerSec / 50;      // 20ms buffer
        byte[] readBuffer = new byte[bufferSize];
        byte[] sendBuffer = new byte[bufferSize];

        while (mRunning && (audioStream.isOpened())) {

            //
            // read audio frame
            //
            int readSize = audioStream.readData(readBuffer);
            if (readSize <= 0) {
                Log.d(TAG, "<AudioSendThread.run> read audio frame EOF, reset to start");
                audioStream.reset();
                continue;
            }

            //
            // Copy data to send buffer
            //
            if (readSize != sendBuffer.length) {
                sendBuffer = new byte[readSize];
            }
            System.arraycopy(readBuffer, 0, sendBuffer, 0, readSize);


            //
            // Send audio frame, data size is 20ms
            //
            AudioFrameInfo audioFrameInfo = new AudioFrameInfo();
            audioFrameInfo.dataType = AudioDataType.AUDIO_DATA_TYPE_PCM;
            int ret = mRtcService.sendAudioData(mConnectionId, sendBuffer, audioFrameInfo);
            if (ret < 0) {
                Log.e(TAG, "<run> sendAudioData() failure, ret=" + ret);
            }

            sleepCurrThread(20); // sleep 20ms
        }
        audioStream.close();
        Log.d(TAG, "<run> <==Exit");


        // Notify: exit audio thread
        synchronized(mExitEvent) {
            mExitEvent.notify();
        }
    }


    boolean sleepCurrThread(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException inerruptExp) {
            inerruptExp.printStackTrace();
            return false;
        }
        return true;
    }

}