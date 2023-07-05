package org.bytedeco.javacv_android_example.record.ago;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


import org.bytedeco.javacv_android_example.R;

import io.agora.rtc.AgoraRtcEvents;
import io.agora.rtc.AgoraRtcService;
import io.agora.rtc.AgoraRtcService.AreaCode;
import io.agora.rtc.AgoraRtcService.AudioCodecType;
import io.agora.rtc.AgoraRtcService.ChannelOptions;
import io.agora.rtc.AgoraRtcService.ErrorCode;
import io.agora.rtc.AgoraRtcService.RtcServiceOptions;

public class RtActivity extends BaseActivity implements AgoraRtcEvents {
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


    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Variable Definition /////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    private static Handler mMsgHandler = null;      ///< 主线程中的消息处理

    private AgoraRtcService mRtcService = null;
    public int mConnectionId = AgoraRtcService.ConnectionIdSpecial.CONNECTION_ID_INVALID;
    private int mRtcState = RTC_STATE_INVALID;
    private AudioSendThread mAudioThread = null;
    private VideoSendThread mVideoThread = null;

    private NetworkChangeReceiver networkChangeReceiver;
    private Button btnStartStop ,btnStart;
    private TextView tvStatus;

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Override Acitivyt Methods ////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "<onCreate>");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ago);
        tvStatus = findViewById(R.id.tv_status);
        btnStartStop = findViewById(R.id.btn_start_stop);
        tvStatus = findViewById(R.id.tv_status);

        //创建IntentFilter实例，并添加action
        IntentFilter intentFilter = new IntentFilter();
        //当网络发生变化时，系统发出的广播为android.net.conn.CONNECTIVITY_CHANGE
        intentFilter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        //创建NetworkChangeReceiver实例
        networkChangeReceiver = new NetworkChangeReceiver();
        //调用registerReceiver()方法进行注册
        registerReceiver(networkChangeReceiver, intentFilter);

        mMsgHandler = new Handler(this.getMainLooper())
        {
            @SuppressLint("HandlerLeak")
            public void handleMessage(Message msg)
            {
                switch (msg.what)
                {
                    case MSGID_RTC_JOINCHNL:
                        onMsgRtcJoinChannelDone(msg.arg1);
                        break;
                    case MSGID_RTC_CONN_LOST:
                        onMsgRtcLostConnectionDone(msg.arg1);
                        break;
                    case MSGID_RTM_LOGIN:
                        onMsgRtmLoginDone(msg.arg1);
                        break;

                    case MSGID_RTM_LOGOUT:
                        onMsgRtmLogoutDone(msg.arg1);
                        break;
                }
            }
        };


        btnStartStop.setText("Start");
        btnStartStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBtnStartStop();
            }
        });

    }


    @Override
    protected void onStart() {
        Log.d(TAG, "<onStart>");
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "<onStop>");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "<onDestory>");
        super.onDestroy();

        try {
            mRtcService.destroyConnection(mConnectionId);
            mRtcService.fini();

            if (mMsgHandler != null) {  // remove all messages
                mMsgHandler.removeMessages(MSGID_RTC_JOINCHNL);
                mMsgHandler.removeMessages(MSGID_RTM_LOGIN);
                mMsgHandler.removeMessages(MSGID_RTM_LOGOUT);
                mMsgHandler = null;
            }
        }catch (Exception e){

        }
    }


    @Override
    protected void onAllPermissionGranted()
    {
        tvStatus.setText("RTC sdk not ready");

        // Create Agora service and initialize it
        mRtcService = new AgoraRtcService();
        if (null == mRtcService) {
            Log.e(TAG, "<main> fail to create AgoraRtcService()!");
            popupMessage("Fail to create RTC sdk");
            return;
        }
        String versionStr = "";//mRtcService.getVersion();
        Log.d(TAG, "<main> versionStr=" + versionStr);

        // Initialize RTC SDK service
        RtcServiceOptions options = new RtcServiceOptions();
        options.areaCode = AreaCode.AREA_CODE_GLOB;
        options.productId = "MyDev01";
        options.licenseValue = LICENSE_VALUE;
        options.logCfg.logPath = getExternalFilesDir(null).getPath() + "/rtsalog";
        options.logCfg.logLevel = AgoraRtcService.LogLevel.RTC_LOG_NOTICE;
        int ret = mRtcService.init(APPID, this, options);
        if (ret != ErrorCode.ERR_OKAY) {
            Log.e(TAG, "<main> fail to init(), ret=" + ret);
            popupMessage("Fail to initialize RTC sdk");
            mRtcService = null;
            return;
        }

        // Create Connection
        mConnectionId = mRtcService.createConnection();
        if (mConnectionId == AgoraRtcService.ConnectionIdSpecial.CONNECTION_ID_INVALID) {
            Log.e(TAG, "<main> fail to createConnection");
            popupMessage("Fail to createConnection");
            return;
        }

        mRtcState = RTC_STATE_IDLE;
        tvStatus.setText("RTC sdk is ready, version=" + versionStr);
    }

    void onBtnStartStop() {

        boolean bRet;
        if (mRtcState == RTC_STATE_IDLE) {  // 空闲状态时可以加入频道
            ChannelOptions chnlOption = new ChannelOptions();
            chnlOption.autoSubscribeAudio = true;
            chnlOption.autoSubscribeVideo = true;
            chnlOption.audioCodecOpt.audioCodecType = AudioCodecType.AUDIO_CODEC_TYPE_OPUS;
            chnlOption.audioCodecOpt.pcmSampleRate = PCM_SAMPLE_RATE;
            chnlOption.audioCodecOpt.pcmChannelNum = PCM_CHNL_NUMBER;
            int ret = mRtcService.joinChannel(mConnectionId, CHANNEL_NAME,0, null, chnlOption);
            if (ret != ErrorCode.ERR_OKAY) {
                Log.e(TAG, "<onBtnStartStop> fail to joinChannel(), ret=" + ret);
                popupMessage("Join channel failure!");
                return;
            }
            tvStatus.setText("Join channel ongoing...");
            mRtcState = RTC_STATE_JOINING;
            btnStartStop.setText("Stop");


        } else if (mRtcState == RTC_STATE_TALKING) {  // 正常通话时可以离开频道
            mRtcState = RTC_STATE_LEAVING;

            if (mVideoThread != null) {
                mVideoThread.sendStop();
                mVideoThread = null;
            }
            if (mAudioThread != null) {
                mAudioThread.sendStop();
                mAudioThread = null;
            }

            int ret = mRtcService.leaveChannel(mConnectionId);
            if (ret != ErrorCode.ERR_OKAY) {
                Log.e(TAG, "<leaveRtcChannel> fail to joinChannel(), ret=" + ret);
            }

            tvStatus.setText("RTC SDK is ready");
            mRtcState = RTC_STATE_IDLE;
            btnStartStop.setText("Start");
        }
    }


   void onMsgRtcJoinChannelDone(int errCode) {
       Log.d(TAG, "<onMsgRtcJoinChannelDone> ");

       if (mVideoThread != null) {
           mVideoThread.sendStop();
           mVideoThread = null;
       }
       if (mAudioThread != null) {
           mAudioThread.sendStop();
           mAudioThread = null;
       }


       mVideoThread = new VideoSendThread(this, mRtcService, CHANNEL_NAME, mConnectionId);
       mAudioThread = new AudioSendThread(this, mRtcService, CHANNEL_NAME, mConnectionId,
               PCM_SAMPLE_RATE, PCM_CHNL_NUMBER, PCM_SMPL_BYTES);

       mVideoThread.sendStart();
       mAudioThread.sendStart();
       mRtcState = RTC_STATE_TALKING;
       tvStatus.setText("RTC SDK is talking");
   }

   void onMsgRtcLostConnectionDone(int errCode) {
       Log.d(TAG, "<onMsgRtcLostConnectionDone> ");
       if (mVideoThread != null) {
           mVideoThread.sendStop();
           mVideoThread = null;
       }
       if (mAudioThread != null) {
           mAudioThread.sendStop();
           mAudioThread = null;
       }
   }

   void onMsgRtmLoginDone(int errCode) {

   }

   void onMsgRtmLogoutDone(int errCode) {

   }



    ////////////////////////////////////////////////////////////////////////
    /////////////////// Override Methods of AgoraRtcEvents /////////////////
    ////////////////////////////////////////////////////////////////////////

    @Override
    public void onJoinChannelSuccess(int connId, int uid, int elapsed_ms) {
        Log.d(TAG, "<onJoinChannelSuccess> uid=" + uid);

        if (mMsgHandler != null) {
            Message msg = new Message();
            msg.what = MSGID_RTC_JOINCHNL;
            msg.arg1 = 0;
            mMsgHandler.removeMessages(MSGID_RTC_JOINCHNL);
            mMsgHandler.sendMessage(msg);
        }
    }


    @Override
    public void onConnectionLost(int connId) {
        Log.d(TAG, "<onConnectionLost>");
        if (mMsgHandler != null) {
            Message msg = new Message();
            msg.what = MSGID_RTC_CONN_LOST;
            msg.arg1 = 0;
            mMsgHandler.removeMessages(MSGID_RTC_CONN_LOST);
            mMsgHandler.sendMessage(msg);
        }
    }

    @Override
    public void onRejoinChannelSuccess(int connId, int uid, int elapsed_ms) {
        Log.d(TAG, "<onRejoinChannelSuccess> uid=" + uid);

        if (mMsgHandler != null) {
            Message msg = new Message();
            msg.what = MSGID_RTC_JOINCHNL;
            msg.arg1 = 0;
            mMsgHandler.removeMessages(MSGID_RTC_JOINCHNL);
            mMsgHandler.sendMessage(msg);
        }
    }

    @Override
    public void onLicenseValidationFailure(int connId, int error) {
        Log.e(TAG,  "error=" + error + mRtcService.errToStr(error));
    }

    @Override
    public void onError(int connId, int code, String msg) {
        Log.d(TAG, "<onError>" + "code=" + code  + ", msg=" + msg);
    }

    @Override
    public void onUserJoined(int connId, int uid, int elapsed_ms) {
        Log.d(TAG, "<onUserJoined>"  + "uid=" + uid);
    }

    @Override
    public void onUserOffline(int connId, int uid, int reason) {
        Log.d(TAG, "<onUserOffline>" + "uid=" + uid + ", reason=" + reason);
    }

    @Override
    public void onUserMuteAudio(int connId, int uid, boolean muted) {
        Log.d(TAG, "<onUserMuteAudio>" + "uid=" + uid + ", muted=" + muted);
    }

    @Override
    public void onUserMuteVideo(int connId, int uid, boolean muted) {
        Log.d(TAG, "<onUserMuteVideo>" + "uid=" + uid + ", muted=" + muted);
    }

    @Override
    public void onKeyFrameGenReq(int connId, int requested_uid, int stream_type) {
        Log.d(TAG, "<onKeyFrameGenReq>" + "req_uid=" + requested_uid + ", stream_type=" + stream_type);

    }

    @Override
    public void onAudioData(int connId, int uid, int sent_ts, byte[] data, AgoraRtcService.AudioFrameInfo info) {
         Log.d(TAG, "<onAudioData>" + ", uid=" + uid
                 + " codec=" + info.dataType + ", data.length=" + data.length);

        // TODO: decode audio frame at here
    }

    @Override
    public void onMixedAudioData(int connId, byte[] data, AgoraRtcService.AudioFrameInfo info) {
        //Log.d(TAG, "<onMixedAudioData>" + " codec=" + info.dataType + ", data.length=" + data.length);
    }

    @Override
    public void onVideoData(int connId, int uid, int sent_ts, byte[] data, AgoraRtcService.VideoFrameInfo info) {
//         Log.d(TAG, "<onVideoData>" + ", uid=" + uid
//             + ", codec=" + info.dataType + ", stream_type=" + info.streamType);

        // TODO: decode video frame at here
    }

    @Override
    public void onTargetBitrateChanged(int connId, int target_bps) {
        // Log.d(TAG, "<onTargetBitrateChanged> channel=" + channel
        //         + ", target_bps=" + target_bps);
    }

    @Override
    public void onTokenPrivilegeWillExpire(int connId, String token) {
        Log.d(TAG, "<onTokenPrivilegeWillExpire> token=" + token);
    }


    @Override
    public void onMediaCtrlReceive(int connId, int uid,  byte[] payload) {
        Log.d(TAG, "<onMediaCtrlReceive>" + ", uid=" + uid);
    }

    //创建一个类，让它继承自BroadcastReceiver，并重写父类的onReceive()方法即可。
    //具体的处理逻辑是在onReceive()中执行（不支持多线程，所以不要处理太复杂的信息）
    class NetworkChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager connectionManager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = connectionManager.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isAvailable()) {
                if (mRtcService != null)
                    mRtcService.notifyNetworkEvent(AgoraRtcService.NetworkEventType.NETWORK_EVENT_UP);
                Toast.makeText(context, "network is available", Toast.LENGTH_SHORT).show();
            } else {
                if (mRtcService != null)
                    mRtcService.notifyNetworkEvent(AgoraRtcService.NetworkEventType.NETWORK_EVENT_DOWN);
                Toast.makeText(context, "network is unavailable", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
