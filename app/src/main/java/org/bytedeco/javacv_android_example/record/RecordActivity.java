package org.bytedeco.javacv_android_example.record;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.Process;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FFmpegLogCallback;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv_android_example.R;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import androidx.annotation.RequiresApi;
import io.agora.rtc.AgoraRtcEvents;
import io.agora.rtc.AgoraRtcService;

public class RecordActivity extends Activity implements OnClickListener, NV21EncoderH264.EncoderListener, AgoraRtcEvents {


    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Constant Definition /////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    private final String TAG = "RTSADEMO/MainActivity";
    private final static String APPID = "a6f44d0da441441ca8949598195499c2";
    private final static String CHANNEL_NAME = "666";
    private final static String LICENSE_VALUE = ""; // Your LICENSE VALUE

    private final static int PCM_SAMPLE_RATE = 16000;   // sample rate of sending PCM data
    private final static int PCM_CHNL_NUMBER = 1;       // channel number of sending PCM data
    private final static int PCM_SMPL_BYTES = 2;        // bytes of per PCM sample

    //
    // message Id
    //
    public static final int MSGID_RTC_JOINCHNL = 0x1001;    ///< 进入RTC频道完成
    public static final int MSGID_RTC_CONN_LOST = 0x1002;   ///< 连接断开，重新尝试连接中
    public static final int MSGID_RTM_LOGIN = 0x2001;       ///< 登录RTM完成
    public static final int MSGID_RTM_LOGOUT = 0x2002;       ///< 登出RTM完成

    //
    // RTC state machine
    //
    public static final int RTC_STATE_INVALID = 0x0000;       ///< RTC 未就绪
    public static final int RTC_STATE_IDLE = 0x0001;          ///< RTC当前空闲
    public static final int RTC_STATE_JOINING = 0x0002;       ///< 正在加入频道
    public static final int RTC_STATE_LEAVING = 0x0003;       ///< 正在离开频道
    public static final int RTC_STATE_TALKING = 0x0004;       ///< 正常通话中

    private final static String CLASS_LABEL = "RecordActivity";
    private final static String LOG_TAG = CLASS_LABEL;
    /* The number of seconds in the continuous record loop (or 0 to disable loop). */
    final int RECORD_LENGTH = 0;
    /* layout setting */
    private final int bg_screen_bx = 232;
    private final int bg_screen_by = 128;
    private final int bg_screen_width = 700;
    private final int bg_screen_height = 500;
    private final int bg_width = 1123;
    private final int bg_height = 715;
    private final int live_width = 640;
    private final int live_height = 480;
    long startTime = 0;
    boolean recording = false;
    volatile boolean runAudioThread = true;
    Frame[] images;
    long[] timestamps;
    ShortBuffer[] samples;
    int imagesIndex, samplesIndex;
    private PowerManager.WakeLock mWakeLock;
    private File ffmpeg_link;
    private FFmpegFrameRecorder recorder;
    private FFmpegFrameGrabber fmpegFrameGrabber;
    private boolean isPreviewOn = false;
    private int sampleAudioRateInHz = 44100;
    private int imageWidth = 320;
    private int imageHeight = 240;
    private int frameRate = 30;
    /* audio data getting thread */
    private AudioRecord audioRecord;
    private AudioRecordRunnable audioRecordRunnable;
    private Thread audioThread;
    /* video data getting thread */
    private Camera cameraDevice;
    private CameraView cameraView;
    private Frame yuvImage = null;
    private int screenWidth, screenHeight;
    private Button btnRecorderControl;
    private byte[] imgData;
    Timer timer=new Timer();
    private boolean isRecordingAudio = false;
    private Long time = 0L;
    private NV21EncoderH264 nv21EncoderH264 = null;
    private AgoraRtcService mRtcService = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FFmpegLogCallback.set();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        setContentView(R.layout.activity_record);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, CLASS_LABEL);
        mWakeLock.acquire();

        initLayout();
        initAgo();
    }

    private void initAgo() {
        Log.e("声网：","RTC sdk not ready");

        // Create Agora service and initialize it
        mRtcService = new AgoraRtcService();
        if (null == mRtcService) {
            Log.e(TAG, "<main> fail to create AgoraRtcService()!");
            Log.e("声网：","Fail to create RTC sdk");
            return;
        }
        String versionStr = "";//mRtcService.getVersion();
        Log.d(TAG, "<main> versionStr=" + versionStr);

        // Initialize RTC SDK service
        AgoraRtcService.RtcServiceOptions options = new AgoraRtcService.RtcServiceOptions();
        options.areaCode = AgoraRtcService.AreaCode.AREA_CODE_GLOB;
        options.productId = "MyDev01";
        options.licenseValue = LICENSE_VALUE;
        options.logCfg.logPath = getExternalFilesDir(null).getPath() + "/rtsalog";
        options.logCfg.logLevel = AgoraRtcService.LogLevel.RTC_LOG_NOTICE;
        int ret = mRtcService.init(APPID, this, options);
        if (ret != AgoraRtcService.ErrorCode.ERR_OKAY) {
            Log.e(TAG, "<main> fail to init(), ret=" + ret);
            Log.e("声网：","Fail to initialize RTC sdk");
            mRtcService = null;
            return;
        }

        // Create Connection
        mConnectionId = mRtcService.createConnection();
        if (mConnectionId == AgoraRtcService.ConnectionIdSpecial.CONNECTION_ID_INVALID) {
            Log.e(TAG, "<main> fail to createConnection");
            return;
        }

        mRtcState = RTC_STATE_IDLE;
        Log.e(TAG, "RTC sdk is ready, version=");
    }

    @Override
    protected void onResume() {
        super.onResume();

        if(mWakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, CLASS_LABEL);
            mWakeLock.acquire();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if(mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        recording = false;

        if(cameraView != null) {
            cameraView.stopPreview();
        }

        if(cameraDevice != null) {
            cameraDevice.stopPreview();
            cameraDevice.release();
            cameraDevice = null;
        }

        if(mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }
        mRtcService.destroyConnection(mConnectionId);
        mRtcService.fini();
    }
    public int mConnectionId = AgoraRtcService.ConnectionIdSpecial.CONNECTION_ID_INVALID;

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if(keyCode == KeyEvent.KEYCODE_BACK) {
            if(recording) {
                stopRecording();
            }

            finish();

            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    private void initLayout() {

        /* get size of screen */
        Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        screenWidth = display.getWidth();
        screenHeight = display.getHeight();
        RelativeLayout.LayoutParams layoutParam = null;
        LayoutInflater myInflate = null;
        myInflate = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        RelativeLayout topLayout = new RelativeLayout(this);
        setContentView(topLayout);
        LinearLayout preViewLayout = (LinearLayout) myInflate.inflate(R.layout.activity_record, null);
        layoutParam = new RelativeLayout.LayoutParams(screenWidth, screenHeight);
        topLayout.addView(preViewLayout, layoutParam);

        /* add control button: start and stop */
        btnRecorderControl = (Button) findViewById(R.id.recorder_control);
        btnRecorderControl.setText("Start");
        btnRecorderControl.setOnClickListener(this);

        /* add camera view */
        int display_width_d = (int) (1.0 * bg_screen_width * screenWidth / bg_width);
        int display_height_d = (int) (1.0 * bg_screen_height * screenHeight / bg_height);
        int prev_rw, prev_rh;
        if(1.0 * display_width_d / display_height_d > 1.0 * live_width / live_height) {
            prev_rh = display_height_d;
            prev_rw = (int) (1.0 * display_height_d * live_width / live_height);
        } else {
            prev_rw = display_width_d;
            prev_rh = (int) (1.0 * display_width_d * live_height / live_width);
        }
        layoutParam = new RelativeLayout.LayoutParams(prev_rw, prev_rh);
        layoutParam.topMargin = (int) (1.0 * bg_screen_by * screenHeight / bg_height);
        layoutParam.leftMargin = (int) (1.0 * bg_screen_bx * screenWidth / bg_width);

        cameraDevice = Camera.open();
        Log.i(LOG_TAG, "cameara open");
        cameraView = new CameraView(this, cameraDevice);
        topLayout.addView(cameraView, layoutParam);
        Log.i(LOG_TAG, "cameara preview start: OK");
        findViewById(R.id.audio).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openAudio = !openAudio;
                if (!openAudio){
                    try {
                        if (recording){
                            if(audioRecord != null) {
                                audioRecord.stop();
                                audioRecord.release();
                                audioRecord = null;
                                Log.v(LOG_TAG, "audioRecord released");
                            }
                        }
                    }catch (Exception e){

                    }
                }else {
                    if (recording){
                        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleAudioRateInHz,
                                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
                        audioRecord.startRecording();
                    }
                }
            }
        });
        nv21EncoderH264 = new NV21EncoderH264(imageWidth, imageHeight);
        nv21EncoderH264.setEncoderListener(this);
    }

    private volatile boolean openAudio = false;

    //---------------------------------------
    // initialize ffmpeg_recorder
    //---------------------------------------
    private void initRecorder() {
        ffmpeg_link = new File(getBaseContext().getCacheDir(), "stream.mp4");
        Log.w(LOG_TAG, "init recorder");

        if(RECORD_LENGTH > 0) {
            imagesIndex = 0;
            images = new Frame[RECORD_LENGTH * frameRate];
            timestamps = new long[images.length];
            for(int i = 0; i < images.length; i++) {
                images[i] = new Frame(imageWidth, imageHeight, Frame.DEPTH_UBYTE, 2);
                timestamps[i] = -1;
            }
        } else if(yuvImage == null) {
            yuvImage = new Frame(imageWidth, imageHeight, Frame.DEPTH_UBYTE, 2);
            Log.i(LOG_TAG, "create yuvImage");
        }

        Log.i(LOG_TAG, "ffmpeg_url: " + ffmpeg_link.getAbsolutePath());
        recorder = new FFmpegFrameRecorder(ffmpeg_link, imageWidth, imageHeight, 1);
        recorder.setFormat("mp4");
        recorder.setSampleRate(sampleAudioRateInHz);
        recorder.setVideoOption("threads", "1");
        recorder.setAudioOption("threads", "1");
        // Set in the surface changed method
        recorder.setFrameRate(frameRate);

        Log.i(LOG_TAG, "recorder initialize success");

        audioRecordRunnable = new AudioRecordRunnable();
        audioThread = new Thread(audioRecordRunnable);
        runAudioThread = true;
        Log.i(LOG_TAG, "recorder initialize success");
    }

    public void startRecording() {
        startRecordTime = System.currentTimeMillis();
        initRecorder();
        try {
            recorder.start();
            startTime = System.currentTimeMillis();
            time = System.currentTimeMillis();
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleAudioRateInHz,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
            if (openAudio){
                audioRecord.startRecording();
            }
            audioThread.start();
            recording = true;
//            timer.schedule(timerTask,0,1000L/frameRate);

        } catch(FFmpegFrameRecorder.Exception e) {
            e.printStackTrace();
        }
    }

    public void stopRecording() {
        Log.e("测试","采集时间长度"+(System.currentTimeMillis() - startRecordTime)/1000f);
        runAudioThread = false;
        try {
            audioThread.join();
        } catch(InterruptedException e) {
            e.printStackTrace();
        }
        audioRecordRunnable = null;
        audioThread = null;
        timer.cancel();
        if(recorder != null && recording) {
            if(RECORD_LENGTH > 0) {
                Log.v(LOG_TAG, "Writing frames");
                try {
                    int firstIndex = imagesIndex % samples.length;
                    int lastIndex = (imagesIndex - 1) % images.length;
                    if(imagesIndex <= images.length) {
                        firstIndex = 0;
                        lastIndex = imagesIndex - 1;
                    }
                    if((startTime = timestamps[lastIndex] - RECORD_LENGTH * 1000000L) < 0) {
                        startTime = 0;
                    }
                    if(lastIndex < firstIndex) {
                        lastIndex += images.length;
                    }
                    for(int i = firstIndex; i <= lastIndex; i++) {
                        long t = timestamps[i % timestamps.length] - startTime;
                        if(t >= 0) {
                            if(t > recorder.getTimestamp()) {
                                recorder.setTimestamp(t);
                            }
                            recorder.record(images[i % images.length]);
                        }
                    }

                    firstIndex = samplesIndex % samples.length;
                    lastIndex = (samplesIndex - 1) % samples.length;
                    if(samplesIndex <= samples.length) {
                        firstIndex = 0;
                        lastIndex = samplesIndex - 1;
                    }
                    if(lastIndex < firstIndex) {
                        lastIndex += samples.length;
                    }
                    for(int i = firstIndex; i <= lastIndex; i++) {
                        recorder.recordSamples(samples[i % samples.length]);
                    }
                } catch(FFmpegFrameRecorder.Exception e) {
                    Log.v(LOG_TAG, e.getMessage());
                    e.printStackTrace();
                }
            }

            recording = false;
            Log.v(LOG_TAG, "Finishing recording, calling stop and release on recorder");
            try {
                recorder.stop();
                recorder.release();
            } catch(FFmpegFrameRecorder.Exception e) {
                e.printStackTrace();
            }
            recorder = null;
        }
    }

    @Override
    public void onClick(View v) {
        if(!recording) {
            startRecording();
            Log.w(LOG_TAG, "Start Button Pushed");
            btnRecorderControl.setText("Stop");
        } else {
            // This will trigger the audio recording loop to stop and then set isRecorderStart = false;
            stopRecording();
            Log.w(LOG_TAG, "Stop Button Pushed");
            btnRecorderControl.setText("Start");
        }
        onBtnStartStop();
    }
    int bufferSize = AudioRecord.getMinBufferSize(sampleAudioRateInHz,
    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

    @Override
    public void h264(byte[] data) {
        //h264采集
        if (mRtcState != RTC_STATE_IDLE){
            int streamId = 0;
            AgoraRtcService.VideoFrameInfo videoFrameInfo = new AgoraRtcService.VideoFrameInfo();
            videoFrameInfo.dataType = AgoraRtcService.VideoDataType.VIDEO_DATA_TYPE_H264;
            videoFrameInfo.frameType = AgoraRtcService.VideoFrameType.VIDEO_FRAME_KEY;
            videoFrameInfo.frameRate = AgoraRtcService.VideoFrameRate.VIDEO_FRAME_RATE_FPS_15;
            int ret = mRtcService.sendVideoData(mConnectionId, data, videoFrameInfo);
            if (ret < 0) {
                Log.e(TAG, "<VideoSendThread.run> sendVideoData() failure, ret=" + ret
                        + ", dataSize=" + data.length);
            } else {
                Log.d(TAG, "<VideoSendThread.run> sendVideoData() successful, ret=" + ret
                        + ", dataSize=" + data.length);
            }
        }
    }

    @Override
    public void onJoinChannelSuccess(int connId, int uid, int elapsed_ms) {

    }

    @Override
    public void onConnectionLost(int connId) {

    }

    @Override
    public void onRejoinChannelSuccess(int connId, int uid, int elapsed_ms) {

    }

    @Override
    public void onLicenseValidationFailure(int connId, int error) {

    }

    @Override
    public void onError(int connId, int code, String msg) {

    }

    @Override
    public void onUserJoined(int connId, int uid, int elapsed_ms) {

    }

    @Override
    public void onUserOffline(int connId, int uid, int reason) {

    }

    @Override
    public void onUserMuteAudio(int connId, int uid, boolean muted) {

    }

    @Override
    public void onUserMuteVideo(int connId, int uid, boolean muted) {

    }

    @Override
    public void onKeyFrameGenReq(int connId, int requested_uid, int stream_type) {

    }

    @Override
    public void onAudioData(int connId, int uid, int sent_ts, byte[] data, AgoraRtcService.AudioFrameInfo info) {

    }

    @Override
    public void onMixedAudioData(int connId, byte[] data, AgoraRtcService.AudioFrameInfo info) {

    }

    @Override
    public void onVideoData(int connId, int uid, int sent_ts, byte[] data, AgoraRtcService.VideoFrameInfo info) {

    }

    @Override
    public void onTargetBitrateChanged(int connId, int target_bps) {

    }

    @Override
    public void onTokenPrivilegeWillExpire(int connId, String token) {

    }

    @Override
    public void onMediaCtrlReceive(int connId, int uid, byte[] payload) {

    }

    //---------------------------------------------
    // audio thread, gets and encodes audio data
    // recorder 是     FFmpegFrameRecorder
    //---------------------------------------------
    class AudioRecordRunnable implements Runnable {

        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

            // Audio
            ShortBuffer audioData;
            int bufferReadResult = 0;
            ShortBuffer selData;


            audioData = ShortBuffer.allocate(bufferSize/2);
            selData = ShortBuffer.allocate(bufferSize/2);

            Log.d(LOG_TAG, "audioRecord.startRecording()");
            /* ffmpeg_audio encoding loop */
            while(runAudioThread) {
                //Log.v(LOG_TAG,"recording? " + recording);
                if (openAudio && audioRecord != null){
                    long audioTime =System.currentTimeMillis();
                    audioRecord.getAudioFormat();
                    bufferReadResult = audioRecord.read(audioData.array(), 0, audioData.capacity());
                    if(bufferReadResult > 0) {
                        audioData.limit(bufferReadResult);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            Log.v(LOG_TAG, "bufferReadResult: " + bufferReadResult+"///"+audioRecord.getBufferSizeInFrames());
                        }
                        // If "recording" isn't true when start this thread, it never get's set according to this if statement...!!!
                        // Why?  Good question...
                        if(recording) {
                            try {
                                Log.v(LOG_TAG, "采集音频0000: "+audioData.get(0));
                                Log.v(LOG_TAG, "采集音频时间: " + recorder.getTimestamp() / 1000L / 1000L+"//"+(System.currentTimeMillis()-audioTime));
                                recorder.recordSamples(audioData);
                                Log.v(LOG_TAG, "采集音频2: " + recorder.getTimestamp() / 1000L / 1000L);
                                //Log.v(LOG_TAG,"recording " + 1024*i + " to " + 1024*i+1024);
                            } catch(FFmpegFrameRecorder.Exception e) {
                                Log.v(LOG_TAG, e.getMessage());
                                e.printStackTrace();
                            }
                        }
                    }
                }else {
                    try {
                        long audioTime =System.currentTimeMillis();
                        bufferReadResult = audioRecord.read(selData.array(), 0, selData.capacity());
                        Log.v(LOG_TAG, "采集音频0000: "+selData.get(0));
                        for (int i = 0 ;i < selData.capacity();i++){
                            selData.put(i, (short) (int)(1+Math.random()*(10-1+1)));
                        }
                        recorder.recordSamples(selData);
                        try {
                            Thread.sleep(40);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } catch (FFmpegFrameRecorder.Exception e) {
                        e.printStackTrace();
                    }
                    Log.e("ces2","////"+bufferReadResult+"///"+recorder.getTimestamp()/1000000L+"//"+audioRecord.getBufferSizeInFrames());
                }
                RecordActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView tvTime = findViewById(R.id.tvTime);
                        tvTime.setText((System.currentTimeMillis() - time) / 1000L +"");
                    }
                });
            }
            Log.v(LOG_TAG, "AudioThread Finished, release audioRecord");


        }
    }

    //---------------------------------------------
    // camera thread, gets and encodes video data
    //---------------------------------------------
    class CameraView extends SurfaceView implements SurfaceHolder.Callback, PreviewCallback {

        private SurfaceHolder mHolder;
        private Camera mCamera;

        public CameraView(Context context, Camera camera) {
            super(context);
            Log.w("camera", "camera view");
            mCamera = camera;
            mHolder = getHolder();
            mHolder.addCallback(CameraView.this);
            mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
            mCamera.setPreviewCallback(CameraView.this);
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            try {
                stopPreview();
                mCamera.setPreviewDisplay(holder);
            } catch(IOException exception) {
                mCamera.release();
                mCamera = null;
            }
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Camera.Parameters camParams = mCamera.getParameters();
            List<Camera.Size> sizes = camParams.getSupportedPreviewSizes();
            // Sort the list in ascending order
            Collections.sort(sizes, new Comparator<Camera.Size>() {

                public int compare(final Camera.Size a, final Camera.Size b) {
                    return a.width * a.height - b.width * b.height;
                }
            });

            // Pick the first preview size that is equal or bigger, or pick the last (biggest) option if we cannot
            // reach the initial settings of imageWidth/imageHeight.
            for(int i = 0; i < sizes.size(); i++) {
                if((sizes.get(i).width >= imageWidth && sizes.get(i).height >= imageHeight) || i == sizes.size() - 1) {
                    imageWidth = sizes.get(i).width;
                    imageHeight = sizes.get(i).height;
                    Log.v(LOG_TAG, "Changed to supported resolution: " + imageWidth + "x" + imageHeight);
                    break;
                }
            }
            camParams.setPreviewSize(imageWidth, imageHeight);

            Log.v(LOG_TAG, "Setting imageWidth: " + imageWidth + " imageHeight: " + imageHeight + " frameRate: " + frameRate);

            camParams.setPreviewFrameRate(frameRate);
            Log.v(LOG_TAG, "Preview Framerate: " + camParams.getPreviewFrameRate());

            mCamera.setParameters(camParams);
            startPreview();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            try {
                mHolder.addCallback(null);
                mCamera.setPreviewCallback(null);
            } catch(RuntimeException e) {
                // The camera has probably just been released, ignore.
            }
        }

        public void startPreview() {
            if(!isPreviewOn && mCamera != null) {
                isPreviewOn = true;
                mCamera.startPreview();
            }
        }

        public void stopPreview() {
            if(isPreviewOn && mCamera != null) {
                isPreviewOn = false;
                mCamera.stopPreview();
            }
        }

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            imgData = data;
            if (nv21EncoderH264 != null){
                nv21EncoderH264.encoderH264(data);
            }
//            if(audioRecord == null || audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
//                startTime = System.currentTimeMillis();
//                return;
//            }
            /* get video data */

//            if(yuvImage != null && recording) {
//                ((ByteBuffer) yuvImage.image[0].position(0)).put(data);
//
//                try {
//                    Log.v(LOG_TAG, "Writing Frame");
//                    long t = 1000 * (System.currentTimeMillis() - startTime);
//                    if(t > recorder.getTimestamp()) {
//                        recorder.setTimestamp(t);
//                    }
////                    if (!openAudio){
////                        recorder.recordSamples(ShortBuffer.allocate(bufferSize));
////                        Log.e("ces2",""+recorder.getTimestamp()/1000L);
////                    }
//                    recorder.record(yuvImage);
//                    Toast.makeText(RecordActivity.this, "测试", Toast.LENGTH_SHORT).show();
//                    Log.v(LOG_TAG, "采集画面: "+openAudio+"//" + recorder.getTimestamp() / 1000L / 1000L);
////                        Thread.sleep(1000L/frameRate);
//                } catch(FFmpegFrameRecorder.Exception e) {
//                    Log.v(LOG_TAG, e.getMessage());
//                    e.printStackTrace();
//                }
//            }
        }
    }

    Long startRecordTime = 0L;
    TimerTask timerTask = new TimerTask() {
        @Override
        public void run() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
//                    if(yuvImage != null && recording && imgData!=null) {
//                        ((ByteBuffer) yuvImage.image[0].position(0)).put(imgData);
//                        try {
//                            Log.v(LOG_TAG, "Writing Frame");
//                            long t = 1000 * (System.currentTimeMillis() - startTime);
//                            if(t > recorder.getTimestamp()) {
//                                recorder.setTimestamp(t);
//                            }
//                            recorder.record(yuvImage);
//                            Log.e("ces1",""+recorder.getTimestamp()/1000L);
//                            if (!openAudio){
//                                recorder.recordSamples(ShortBuffer.allocate(bufferSize));
//                                Log.e("ces2",""+recorder.getTimestamp()/1000L);
//                            }
//                        } catch(FFmpegFrameRecorder.Exception e) {
//                            Log.v(LOG_TAG, e.getMessage());
//                            e.printStackTrace();
//                        }
//                    }
                }
            });
        }
    };

    private int mRtcState = RTC_STATE_INVALID;
    void onBtnStartStop() {

        boolean bRet;
        if (mRtcState == RTC_STATE_IDLE) {  // 空闲状态时可以加入频道
            AgoraRtcService.ChannelOptions chnlOption = new AgoraRtcService.ChannelOptions();
            chnlOption.autoSubscribeAudio = true;
            chnlOption.autoSubscribeVideo = true;
            chnlOption.audioCodecOpt.audioCodecType = AgoraRtcService.AudioCodecType.AUDIO_CODEC_TYPE_OPUS;
            chnlOption.audioCodecOpt.pcmSampleRate = PCM_SAMPLE_RATE;
            chnlOption.audioCodecOpt.pcmChannelNum = PCM_CHNL_NUMBER;
            int ret = mRtcService.joinChannel(mConnectionId, CHANNEL_NAME,0, null, chnlOption);
            if (ret != AgoraRtcService.ErrorCode.ERR_OKAY) {
                Log.e(TAG, "<onBtnStartStop> fail to joinChannel(), ret=" + ret);
                return;
            }
            Log.e("声网推流","Join channel ongoing...");
            mRtcState = RTC_STATE_JOINING;


        } else if (mRtcState == RTC_STATE_TALKING) {  // 正常通话时可以离开频道
            mRtcState = RTC_STATE_LEAVING;
            int ret = mRtcService.leaveChannel(mConnectionId);
            if (ret != AgoraRtcService.ErrorCode.ERR_OKAY) {
                Log.e(TAG, "<leaveRtcChannel> fail to joinChannel(), ret=" + ret);
            }
            Log.e("声网推流","RTC SDK is ready");
            mRtcState = RTC_STATE_IDLE;
        }
    }
}
