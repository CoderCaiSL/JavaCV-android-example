package org.bytedeco.javacv_android_example.record.ago;

import android.content.Context;
import android.util.Log;

import org.bytedeco.javacv_android_example.R;

import io.agora.rtc.AgoraRtcService;
import io.agora.rtc.AgoraRtcService.VideoDataType;
import io.agora.rtc.AgoraRtcService.VideoFrameInfo;
import io.agora.rtc.AgoraRtcService.VideoFrameRate;
import io.agora.rtc.AgoraRtcService.VideoFrameType;

public class VideoSendThread  extends Thread {
    private final String TAG = "RTSADEMO/VideoSendThread";
    private final static int[] FRAME_SIZE_ARR = {
        9654, 1617, 1884, 2003, 2362, 1887, 1773, 1943, 2081, 2000, 1975, 2093, 2247, 2469, 2578,
        2554, 2548, 2116, 2327, 2254, 2270, 1565, 2498, 2409, 2783, 2394, 2248, 1337, 1318, 1186,
        12217, 1366, 1570, 1970, 2066, 2091, 1856, 2477, 1941, 1956, 1329, 1944, 2054, 1706, 1714,
        1607, 1757, 2381, 2240, 2555, 2224, 1929, 1622, 1785, 2320, 2511, 1961, 2051, 2340, 1958,
        12223, 1605, 1690, 1950, 1848, 2130, 2177, 2539, 1868, 2043, 1942, 2188, 1974, 2272, 1716,
        2150, 1837, 2386, 2720, 2282, 2561, 2237, 1848, 1895, 2511, 2366, 2228, 1966, 1829, 2097,
        11302, 2034, 2552, 2679, 3223, 2408, 1921, 1721, 1899, 1630, 1689, 1602, 1798, 1456, 1914,
        1625, 1586, 1002, 1538, 1637, 1582, 1386, 1752, 1527, 1739, 1448, 1641, 1279, 1501, 1523,
        11903, 1057, 1504, 1495, 1917, 2051, 2237, 2169, 2437, 2315, 2162, 1870, 1962, 2034, 2141,
        1676, 1874, 2068, 2468, 2429, 2458, 2583, 2626, 1967, 2558, 2301, 2473, 2138, 2152, 1712,
        10497, 1528, 2165, 2941, 3253, 2867, 3679, 3621, 3564, 1723, 2013, 1921, 1757, 1517, 1899,
        1407, 1480, 1403, 1604, 1836, 2442, 2680, 3154, 3329, 3219, 2612, 2759, 2783, 2622, 2855,
        10619, 2145, 2259, 2513, 2779, 2757, 3199, 3081, 2684, 2977, 2884, 3170, 3346, 3164, 3102,
        3486, 3190, 2414, 2614, 2425, 2705, 3173, 3114, 2769, 2650, 2604, 2355, 2283, 2251, 2288,
        11091, 2930, 3032, 2907, 2853, 3308, 2904, 3742, 3324, 4308, 4067, 2709, 2927, 1909, 2109,
        2210, 2875, 2119, 2772, 4059, 4111, 2840, 2528, 1920, 3217, 1615, 2640, 2209, 3503, 2085,
        19570, 1705, 2747, 2420, 2553, 2435, 3508, 2084, 2188, 1994, 5324, 1771, 1397, 2608, 3201,
        2728, 2675, 3498, 1783, 1308, 2611, 2799, 3630, 2243, 1898, 2052, 3272, 2271, 2976, 3679,
        22520, 712, 1650, 1846, 2142, 1464, 1903, 1828, 2564, 1365, 1359, 1262, 3015, 2596, 2472,
        2639, 3447, 2879, 2546, 2643, 3240, 1759, 2123, 1277, 2336, 1664, 2104, 1881, 1267, 516,
    };
    private final static int FRAME_COUNT = 300;


    private final Object mExitEvent = new Object();
    private Context mContext;
    private AgoraRtcService mRtcService;
    private String mChannelName;
    private int mConnectionId;
    private volatile boolean mRunning = false;


    //////////////////////////////////////////////////////////////////
    ///////////////////// Public Methods ////////////////////////////
    ////////////////////////////////////////////////////////////////

    public VideoSendThread(Context ctx, AgoraRtcService rtcService, String chnlName, int connectionId) {
        mContext = ctx;
        mRtcService = rtcService;
        mChannelName = chnlName;
        mConnectionId = connectionId;
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
        Log.d(TAG, "<run> ==>Enter");
        StreamFile videoStream = new StreamFile();
        videoStream.open(mContext, R.raw.send_video);

        int frameIndex = 0;
        while (mRunning && (videoStream.isOpened())) {
            //
            // read video frame
            //
            if (frameIndex >= FRAME_COUNT) {
                Log.d(TAG, "<run> read video frame EOF, reset to start");
                frameIndex = 0;
                videoStream.reset();
            }
            int frameSize = FRAME_SIZE_ARR[frameIndex];
            byte[] videoBuffer = new byte[frameSize];
            int readSize = videoStream.readData(videoBuffer);
            if (readSize <= 0) {
                Log.e(TAG, "<run> read video frame error, readSize=" + readSize);
            }

            //
            // Send video frame
            //
            int streamId = 0;
            VideoFrameInfo videoFrameInfo = new VideoFrameInfo();
            videoFrameInfo.dataType = VideoDataType.VIDEO_DATA_TYPE_H264;
            videoFrameInfo.frameType = VideoFrameType.VIDEO_FRAME_KEY;
            videoFrameInfo.frameRate = VideoFrameRate.VIDEO_FRAME_RATE_FPS_15;
            int ret = mRtcService.sendVideoData(mConnectionId, videoBuffer, videoFrameInfo);
            if (ret < 0) {
                Log.e(TAG, "<VideoSendThread.run> sendVideoData() failure, ret=" + ret
                        + ", dataSize=" + videoBuffer.length);
            } else {
                // Log.d(TAG, "<VideoSendThread.run> sendVideoData() successful, ret=" + ret
                // + ", dataSize=" + videoBuffer.length);
            }

            frameIndex++;
            videoBuffer = null;

            sleepCurrThread(66);
        }
        videoStream.close();
        Log.d(TAG, "<run> <==Exit");

        // Notify: exit video thread
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