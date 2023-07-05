package org.bytedeco.javacv_android_example.record.ago;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;


public class StreamFile {
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Constant Definition /////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    private final String TAG = "RTSADEMO/StreamFile";



    ///////////////////////////////////////////////////////////////////////////
    ///////////////////// Variable Definition /////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    private InputStream mInStream = null;


    //////////////////////////////////////////////////////////////////
    ///////////////////// Public Methods ////////////////////////////
    ////////////////////////////////////////////////////////////////
    public boolean open(Context ctx, int resId) {
        try {
            Resources resources = ctx.getResources();
            mInStream = resources.openRawResource(resId);
            
        } catch (Resources.NotFoundException notFoundExp)          {
            notFoundExp.printStackTrace();
            Log.e(TAG, "<StreamFile.open> file not found");
            return false;

        } catch (SecurityException fileSecExp) {
            fileSecExp.printStackTrace();
            Log.e(TAG, "<StreamFile.open> security exception");
            return false;
        }

        return true;
    }

    public void close() {
        if (mInStream != null) {
            try {
                mInStream.close();
            } catch (IOException fileCloseExp)  {
                fileCloseExp.printStackTrace();
            }
            mInStream = null;
        }
    }

    public boolean isOpened() {
        return (mInStream != null);
    }

    public int readData(byte[] readBuffer) {
        if (mInStream == null) {
            return -2;
        }

        try {
            int readSize = mInStream.read(readBuffer);
            return readSize;

        } catch (IOException ioExp) {
            return -3;
        }
    }

    public int reset() {
        if (mInStream == null) {
            return -2;
        }

        try {
            mInStream.reset();
            return 0;
        } catch (IOException ioExp) {
            return -3;
        }
    }

}