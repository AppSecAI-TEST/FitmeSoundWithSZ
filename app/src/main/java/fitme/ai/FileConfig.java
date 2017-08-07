package fitme.ai;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by Lixiaojie on 2016/10/31.
 */

public class FileConfig {

    public static final int PLATFORM_G102 = 1;
    public static final int PLATFORM_R16 = 2;
    public static final int PLATFORM_4_1 = 3;
    public static final int PLATFORM_LINEAR_4 = 4;
    public static final int PLATFORM_L_6 = 5;

    private static File sai_config;    //存放配置文件和资源文件

    public static boolean copyConfigFile(Context context, int platform){
        sai_config = new File(Environment.getExternalStorageDirectory().toString() + "/sai_config");
        sai_config.delete();
        sai_config.mkdirs();

        if (platform == PLATFORM_G102) {
            copyAsset(context, "G102/sai_config.txt","/sdcard/sai_config/sai_config.txt");
            copyAsset(context, "G102/wopt_4mic_sai.bin","/sdcard/sai_config/wopt_4mic_sai.bin");
            copyAsset(context, "G102/saires.q", "/sdcard/sai_config/saires.q");
        } else if (platform == PLATFORM_4_1) {
            copyAsset(context, "Normal4_1/sai_config.txt","/storage/sdcard0/sai_config/sai_config.txt");
            copyAsset(context, "Normal4_1/wopt_5mic_sai.bin","/storage/sdcard0/sai_config/wopt_5mic_sai.bin");
            copyAsset(context, "Normal4_1/saires.q", "/storage/sdcard0/sai_config/saires.q");
        } else {

        }

        if (new File("/sdcard/sai_config/sai_api.q").exists()) {
            return true;
        } else {
            return false;
        }
    }

    private static void copyAsset(Context context, String oldPath, String newPath){
        InputStream is;
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(newPath);
            is = context.getAssets().open(oldPath);
            byte[] buffer = new byte[1024];
            int length = 0;
            while ((length = is.read(buffer)) != -1){
                fos.write(buffer,0,length);
            }
            fos.flush();
            fos.close();
            is.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
