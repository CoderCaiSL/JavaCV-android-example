package org.bytedeco.javacv_android_example.record.ago;


import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Window;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


public abstract class BaseActivity extends AppCompatActivity  {

    ////////////////////////////////////////////////////////////////////
    //////////////////////// Constant Definition ///////////////////////
    ////////////////////////////////////////////////////////////////////
    private static final String TAG = "RTSADEMO/BaseActivity";

    public static final int PERM_REQID_RECORD_AUDIO = 0x1001;
    public static final int PERM_REQID_CAMERA = 0x1002;
    public static final int PERM_REQID_RDSTORAGE = 0x1003;
    public static final int PERM_REQID_WRSTORAGE = 0x1004;
    public static final int PERM_REQID_MGSTORAGE = 0x1005;
    public static final int PERM_REQID_WIFISTATE = 0x1006;
    public static final int PERM_REQID_FINELOCAL = 0x1007;

    class PermissionItem {
        public String permissionName;           ///< 权限名
        public boolean granted = false;         ///< 是否有权限
        public int requestId;                   ///< 请求Id

        PermissionItem(String name, int reqId) {
            permissionName = name;
            requestId = reqId;
        }
    };

    /////////////////////////////////////////////////////////////////////
    ///////////////////// Variable Definition ///////////////////////////
    /////////////////////////////////////////////////////////////////////
    protected PermissionItem[] mPermissionArray;
    protected Activity mActivity;
    private ProgressDialog mProgressDlg;

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Override Activity Methods ////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);

        mActivity = this;

        mProgressDlg = new ProgressDialog(this);

        initializePermList();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mProgressDlg != null) {
            mProgressDlg.dismiss();
        }

        initializePermList();
    }


    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) {
                    return;
                }

                // 检测是否要动态申请相应的权限
                int reqIndex = requestNextPermission();
                if (reqIndex < 0) {
                    onAllPermissionGranted();
                    return;
                }

            }
        }, 200);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,  @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "<onRequestPermissionsResult>" + requestCode
                + ", permissions= " + Arrays.toString(permissions)
                + ", grantResults= " + Arrays.toString(grantResults));


        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setPermGrantedByReqId(requestCode);

        } else { // 拒绝了该权限
            finish();
            return;
        }

        // 检测是否要动态申请相应的权限
        int reqIndex = requestNextPermission();
        if (reqIndex < 0) {
            onAllPermissionGranted();
            return;
        }
    }


    /*
     * @brief 所有权限都允许事件，子类实现该方法，处理获得所有权限后的处理流程
     */
    protected abstract void onAllPermissionGranted();


    protected void progressShow(String msg) {
        if (msg == "") {
            mProgressDlg.show();
            return;
        }
        mProgressDlg.setMessage(msg);
        mProgressDlg.show();
    }

    protected void progressHide() {
        mProgressDlg.hide();
    }


    protected void popupMessage(String message)
    {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    protected void popupMessageLongTime(String message)
    {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    ///////////////////////////////////////////////////////////////////////////
    //////////////////////// Methods of Permission ////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    protected void initializePermList() {

        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Log.d(TAG, "<onCreate> have permission in low Buil.version");
            mPermissionArray = new PermissionItem[5];
            for (PermissionItem item : mPermissionArray) {
                item.granted = true;
            }

        } else if (Build.VERSION.SDK_INT < 28) {
            mPermissionArray = new PermissionItem[6];
            mPermissionArray[0] = new PermissionItem(Manifest.permission.RECORD_AUDIO, PERM_REQID_RECORD_AUDIO);
            mPermissionArray[1] = new PermissionItem(Manifest.permission.CAMERA, PERM_REQID_CAMERA);
            mPermissionArray[2] = new PermissionItem(Manifest.permission.READ_EXTERNAL_STORAGE, PERM_REQID_RDSTORAGE);
            mPermissionArray[3] = new PermissionItem(Manifest.permission.WRITE_EXTERNAL_STORAGE, PERM_REQID_WRSTORAGE);
            mPermissionArray[4] = new PermissionItem(Manifest.permission.ACCESS_WIFI_STATE, PERM_REQID_WIFISTATE);
            mPermissionArray[5] = new PermissionItem(Manifest.permission.ACCESS_FINE_LOCATION, PERM_REQID_FINELOCAL);
            for (PermissionItem item : mPermissionArray) {
                item.granted = (ContextCompat.checkSelfPermission(this, item.permissionName) == PackageManager.PERMISSION_GRANTED);
            }

        } else {
            mPermissionArray = new PermissionItem[6];
            mPermissionArray[0] = new PermissionItem(Manifest.permission.RECORD_AUDIO, PERM_REQID_RECORD_AUDIO);
            mPermissionArray[1] = new PermissionItem(Manifest.permission.CAMERA, PERM_REQID_CAMERA);
            mPermissionArray[2] = new PermissionItem(Manifest.permission.READ_EXTERNAL_STORAGE, PERM_REQID_RDSTORAGE);
            mPermissionArray[3] = new PermissionItem(Manifest.permission.WRITE_EXTERNAL_STORAGE, PERM_REQID_WRSTORAGE);
            //mPermissionArray[4] = new PermissionItem(Manifest.permission.MANAGE_EXTERNAL_STORAGE, PERM_REQID_MGSTORAGE);
            mPermissionArray[4] = new PermissionItem(Manifest.permission.ACCESS_WIFI_STATE, PERM_REQID_WIFISTATE);
            mPermissionArray[5] = new PermissionItem(Manifest.permission.ACCESS_FINE_LOCATION, PERM_REQID_FINELOCAL);
            for (PermissionItem item : mPermissionArray) {
                item.granted = (ContextCompat.checkSelfPermission(this, item.permissionName) == PackageManager.PERMISSION_GRANTED);
            }
        }
    }

    /*
     * @brief 进行下一个需要的权限申请
     * @param None
     * @return 申请权限的索引, -1表示所有权限都有了，不再需要申请
     */
    protected int requestNextPermission() {
        for (int i = 0; i < mPermissionArray.length; i++) {
            if (!mPermissionArray[i].granted) {
                // 请求相应的权限i
                String permission = mPermissionArray[i].permissionName;
                int requestCode = mPermissionArray[i].requestId;
                ActivityCompat.requestPermissions(mActivity, new String[]{permission}, requestCode);
                return i;
            }
        }

        return -1;
    }

    /*
     * @brief 根据requestId 标记相应的 PermissionItem 权限已经获得
     * @param reqId :  request Id
     * @return 相应的索引, -1表示没有找到 request Id 对应的项
     */
    protected int setPermGrantedByReqId(int reqId) {
        for (int i = 0; i < mPermissionArray.length; i++) {
            if (mPermissionArray[i].requestId == reqId) {
                mPermissionArray[i].granted = true;
                return i;
            }
        }

        Log.d(TAG, "<setPermGrantedByReqId> NOT found reqId=" + reqId);
        return -1;
    }

    /*
     * @brief save bitmap to local file
     */
    protected boolean saveBmpToFile(Bitmap bmp, String fileName)
    {
        File f = new File(fileName);
        if (f.exists()) {
            f.delete();
        }
        try {
            FileOutputStream out = new FileOutputStream(f);
            bmp.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
            out.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.e(TAG, "<saveBmpToFile> file not found: " + fileName);
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "<saveBmpToFile> IO exception");
            return false;
        }

        return true;
    }

    /*
     * @brief 判断本应用是否已经位于最前端：已经位于最前端时，返回 true；否则返回 false
     */
    public boolean isRunningForeground(Context context) {
        ActivityManager activityManager = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> appProcessInfoList = activityManager.getRunningAppProcesses();
        for (ActivityManager.RunningAppProcessInfo appProcessInfo : appProcessInfoList) {
            if (appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                    && appProcessInfo.processName == context.getApplicationInfo().processName) {
                return true;
            }
        }
        return false;
    }

    /*
     * @brief 当本应用位于后台时，则将它切换到最前端
     */
    public void setTopApp(Context context) {
        if (isRunningForeground(context)) {
            return;
        }
        //获取ActivityManager
        ActivityManager activityManager = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);

        //获得当前运行的task(任务)
        List<ActivityManager.RunningTaskInfo> taskInfoList = activityManager.getRunningTasks(100);
        for (ActivityManager.RunningTaskInfo taskInfo : taskInfoList) {
            //找到本应用的 task，并将它切换到前台
            if (taskInfo.topActivity.getPackageName() == context.getPackageName()) {
                activityManager.moveTaskToFront(taskInfo.id, 0);
                Log.i(TAG, "<setTopApp> set to front, id=" + taskInfo.id);
                break;
            }
        }
    }


}
