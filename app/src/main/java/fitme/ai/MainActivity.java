package fitme.ai;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SynthesizerListener;
import com.iflytek.cloud.WakeuperListener;
import com.iflytek.cloud.WakeuperResult;

import org.json.JSONException;
import org.json.JSONObject;
import org.sai.commnpkg.DirectorBaseMsg;
import org.sai.commnpkg.saiAPI_wrap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.com.broadlink.sdk.BLLet;
import cn.com.broadlink.sdk.constants.account.BLAccountErrCode;
import cn.com.broadlink.sdk.data.controller.BLDNADevice;
import cn.com.broadlink.sdk.interfaces.controller.BLDeviceScanListener;
import cn.com.broadlink.sdk.param.controller.BLConfigParam;
import cn.com.broadlink.sdk.param.controller.BLStdControlParam;
import cn.com.broadlink.sdk.result.account.BLLoginResult;
import cn.com.broadlink.sdk.result.controller.BLDownloadScriptResult;
import cn.com.broadlink.sdk.result.controller.BLPairResult;
import cn.com.broadlink.sdk.result.controller.BLProfileStringResult;
import fitme.ai.bean.MessageGet;
import fitme.ai.bean.Music;
import fitme.ai.model.BLControl;
import fitme.ai.model.BLControlConstants;
import fitme.ai.service.MusicPlayerService;
import fitme.ai.setting.api.ApiManager;
import fitme.ai.utils.IflytekWakeUp;
import fitme.ai.utils.L;
import fitme.ai.utils.Location;
import fitme.ai.utils.Mac;
import fitme.ai.utils.NetworkStateUtil;
import fitme.ai.utils.SignAndEncrypt;
import fitme.ai.utils.VoiceToWords;
import fitme.ai.utils.WordsToVoice;
import fitme.ai.view.impl.IGetVoiceToWord;
import okhttp3.ResponseBody;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

//声智唤醒
public class MainActivity extends Activity implements View.OnClickListener , IGetVoiceToWord {

    private static final String TAG = "SoundAi";
//    private static final String COMMAND_MIC_RW = "chm od 777 /dev/snd/sound_8ch";
//    private static final String COMMAND_LED = "chmod 777 /dev/i2c_led";

    private Button btn;
    private TextView wakeupTxv;
    private TextView asrTxv;

    public saiAPI_wrap mSaiAPIWrap;
    private JavaCallBack mJavaCallBack;

    private boolean isRunning = false;

    //-------------------------------------
    private TextView tvXunfeiASR;
    private TextView tvResp;
    private Button btn_speak;

    private MyApplication app;

    //讯飞语音识别,文字转语音
    //private VoiceToWords voiceToWords;
    private WordsToVoice wordsToVoice;
    //private IflytekWakeUp iflytekWakeUp;

    //播放短音效
    private SoundPool soundPool;
    private int soundid;

    private AudioManager mAudioManager;
    private int maxVolume;
    private int currentVolume;

    //博联控制类
    private BLControl blControl;


    private Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case 1:
                    blControl.commandRedCodeDevice(BLControlConstants.GARAGE_DOOR, BLControlConstants.RM_PRO_DID);
                    break;
                case 2:
                    blControl = new BLControl(blDNADevicesMap);
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (FileConfig.copyConfigFile(MainActivity.this, FileConfig.PLATFORM_4_1)) {
            initView();
            initSaiSDK();
        } else {
            Log.d(TAG, "onCreate: couldn't find config files");
        }

        //--------------------------

        //初始化短音效
        soundPool = new SoundPool(5, AudioManager.STREAM_MUSIC, 0);
        soundid = soundPool.load(MainActivity.this, R.raw.siri, 1);

        //音量控制,初始化定义
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        //最大音量
        maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        //当前音量
        currentVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

        app = (MyApplication) getApplication();
        //初始化讯飞语音识别
        //voiceToWords = new VoiceToWords(MainActivity.this,this);
        wordsToVoice = new WordsToVoice(MainActivity.this);
        //iflytekWakeUp = new IflytekWakeUp(MainActivity.this,new MyWakeuperListener());

        //初始化设备各项参数（网络连接，gps等）
        initAndroidFunctionState();

        //初始化博联
        initBoardLink();
        TestSpeak();

        if (NetworkStateUtil.isNetworkAvailable(this)){
            wordsToVoice.startSynthesizer("欢迎使用小秘智能语音助理",mTtsListener);
        }else {
            wordsToVoice.startSynthesizer("哎呀，小秘没有连接上网络哦",mTtsListener);
        }

        //绑定设备
        bindDevice();

        Location location = new Location(this,app);
        location.startLocation();

        new Thread(){
            @Override
            public void run() {
                super.run();
                while (true){
                    //iflytekWakeUp.startWakeuper();
                    try {
                        mSaiAPIWrap.start_service();
                        sleep(40000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater mMenuInflater = getMenuInflater();
        mMenuInflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.menu_version:
                String version = "";
                PackageManager mPackageManager = getPackageManager();
                try {
                    PackageInfo pi = mPackageManager.getPackageInfo(getPackageName(), 0);
                    version = pi.versionName;
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }
                Toast.makeText(this, version, Toast.LENGTH_LONG).show();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initSaiSDK() {
        mSaiAPIWrap = new saiAPI_wrap();
        mJavaCallBack = new JavaCallBack();
        int root = mSaiAPIWrap.root_system_signal(3);
        if (root == 0) {
            int initResult = mSaiAPIWrap.init_system(0.65, "/sdcard/sai_config", mJavaCallBack);
//            Toast.makeText(this, mSaiAPIWrap.get_version(), Toast.LENGTH_SHORT).show();
            Log.d(TAG, "initSaiSDK: init = " + initResult);
        } else {
            Log.d(TAG, "initSaiSDK: 未取得权限");
            Toast.makeText(MainActivity.this,"未取得权限",Toast.LENGTH_LONG).show();
            btn.setEnabled(false);
        }
    }

    private void initView() {
        //初始化
        wakeupTxv = (TextView) findViewById(R.id.wakeup);
        asrTxv = (TextView) findViewById(R.id.asr);
        btn = (Button) findViewById(R.id.btn_wake);

        tvResp = (TextView) findViewById(R.id.tv_resp);
        tvXunfeiASR = (TextView) findViewById(R.id.xunfei_asr);
        btn_speak = (Button) findViewById(R.id.btn_speak);
    }

    //点击事件
    @Override
    public void onClick(View view) {
        int id = view.getId();
        switch (id) {
            case R.id.btn_wake:
                if (!isRunning) {
                    isRunning = true;
                    btn.setText("Stop");
                    mSaiAPIWrap.start_service();
                } else {
                    isRunning = false;
                    btn.setText("Run");
                    wakeupTxv.setText("");
                    asrTxv.setText("");
                    mSaiAPIWrap.stop_service();
                }
                break;
        }
    }

    public class JavaCallBack extends DirectorBaseMsg {

        @Override
        public void outter_get_asr(final String asr_rslt) {
            Log.d(TAG, "outter_get_asr: " + asr_rslt);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    asrTxv.setText(asr_rslt);
                    //接收声智语音识别
                    try {
                        JSONObject jsonObject = new JSONObject(asr_rslt);
                        String asr = jsonObject.getString("asr");
                        smartVoiceASR(asr);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
//            mSaiAPIWrap.set_unwakeup_status();
        }

        //声智唤醒！！
        @Override
        public void outter_wakeup_now(int channel, final float angle) {
            Log.d(TAG, "outter_wakeup_now: channel = " + channel + ", angle = " + angle);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    asrTxv.setText("");
                    wakeupTxv.setText("角度为：" + angle);

                    //--------------------------唤醒成功-----------------------------
                    if (wordsToVoice.isTtsSpeaking()){
                        wordsToVoice.mTtsStop();
                    }
                    playingmusic(MusicPlayerService.REDUCE_MUSIC_VOLUME,"");    //音乐声变小
                    isPlayingMusic = false;
                    //播放唤醒声
                    soundPool.play(soundid, 1.0f, 1.0f, 0, 0, 1.0f);
                    /*if (iflytekWakeUp.isIvwListening()){
                        iflytekWakeUp.stopWakeuper();
                    }*/
                    //voiceToWords.startRecognizer();
                }
            });

        }
    }

    private void smartVoiceASR(String sequence){
        L.i("声智ASR----------------------"+sequence.toString());
        //playingmusic(MusicPlayerService.RECOVER_MUSIC_VOLUME,"");

        String sendMsg = sequence.toString();
        if (scene(sendMsg,"马上到家")||scene(sendMsg,"快到家了")||scene(sendMsg,"我要到家了")){
            L.i("1");
            blControl.commandRedCodeDevice(BLControlConstants.MAIN_LIGHT_OPEN, BLControlConstants.RM_PRO_DID);   //开灯
            blControl.commandRedCodeDevice(BLControlConstants.GARAGE_DOOR, BLControlConstants.RM_PRO_DID);   //车库门
            blControl.commandRedCodeDevice(BLControlConstants.CURTAIN, BLControlConstants.RM_PRO_DID);
            blControl.commandRedCodeDevice(BLControlConstants.AIR, BLControlConstants.RM_PRO_DID);
            handler.sendEmptyMessageDelayed(1,10000);
            wordsToVoice.startSynthesizer("欢迎回家，已为你打开回家模式",mTtsListener);

        }
        /*else if (scene(sendMsg,"我要出门")||scene(sendMsg,"准备出门")){
            L.i("2");
            blControl.commandRedCodeDevice(BLControlConstants.MAIN_LIGHT_CLOSE, BLControlConstants.RM_PRO_DID);   //关灯
            blControl.commandRedCodeDevice(BLControlConstants.GARAGE_DOOR, BLControlConstants.RM_PRO_DID);   //车库门
            handler.sendEmptyMessageDelayed(1,10000);
            blControl.commandRedCodeDevice(BLControlConstants.CURTAIN, BLControlConstants.RM_PRO_DID);
            wordsToVoice.startSynthesizer("已为你打开离家模式，祝你今天过得愉快哦",mTtsListener);
        }else if (scene(sendMsg,"要睡觉")||scene(sendMsg,"我想睡觉")){
            L.i("3");
            blControl.commandRedCodeDevice(BLControlConstants.MAIN_LIGHT_CLOSE, BLControlConstants.RM_PRO_DID);
            //blControl.commandRedCodeDevice(BLControlConstants.TV_ON_OFF, BLControlConstants.RM_PRO_DID);
            blControl.commandRedCodeDevice(BLControlConstants.CURTAIN, BLControlConstants.RM_PRO_DID);
            wordsToVoice.startSynthesizer("已为你打开睡眠模式，晚安，好梦",mTtsListener);
        }else if (scene(sendMsg,"要起床")){
            L.i("4");
            blControl.commandRedCodeDevice(BLControlConstants.CURTAIN, BLControlConstants.RM_PRO_DID);
            blControl.commandRedCodeDevice(BLControlConstants.MAIN_LIGHT_OPEN, BLControlConstants.RM_PRO_DID);
            //wordsToVoice.startSynthesizer("早安，已为你打开起床模式",mTtsListener);
            messageCreat(NetworkStateUtil.getLocalMacAddressFromWifiInfo(MainActivity.this)
                    ,String.valueOf(app.getLongitude()),String.valueOf(app.getLatitude()),"device_text","我要起床了","13145");
        }else if (scene(sendMsg,"开派对")){
            L.i("5");
            blControl.commandRedCodeDevice(BLControlConstants.MAIN_LIGHT_FLASH, BLControlConstants.RM_PRO_DID);
            //blControl.commandRedCodeDevice(BLControlConstants.TV_ON_OFF, BLControlConstants.RM_PRO_DID);
            wordsToVoice.startSynthesizer("已为你打开派对模式，玩得开心点哦",mTtsListener);
        }else if (scene(sendMsg,"小朋友要过来")||scene(sendMsg,"有小朋友")){
            L.i("6");
            blControl.commandRedCodeDevice(BLControlConstants.MAIN_LIGHT_ORENGE, BLControlConstants.RM_PRO_DID);
            wordsToVoice.startSynthesizer("已为你打开亲子模式，祝你玩得愉快哦",mTtsListener);
        }else if (scene(sendMsg,"室内温度")||scene(sendMsg,"室内空气质量")||scene(sendMsg,"环境噪音")||scene(sendMsg,"室内空气湿度")){
            String strResult = blControl.dnaPassthrough();     //获取空气检测仪数据
            wordsToVoice.startSynthesizer(strResult,mTtsListener);
        }else if (scene(sendMsg,"热门")&&scene(sendMsg,"电视剧")){
            messageCreat(NetworkStateUtil.getLocalMacAddressFromWifiInfo(MainActivity.this)
                    ,String.valueOf(app.getLongitude()),String.valueOf(app.getLatitude()),"device_text","我要看楚乔传","13145");
            L.i("10");
        }*/
        else {
            //发送消息请求
            if (!"。".equals(sendMsg)){
                messageCreat(NetworkStateUtil.getLocalMacAddressFromWifiInfo(MainActivity.this)
                        ,String.valueOf(app.getLongitude()),String.valueOf(app.getLatitude()),"device_text",sendMsg,"13145");
            }
        }


    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSaiAPIWrap.terminate_system();
        //voiceToWords.mIatDestroy();
        wordsToVoice.mTtsDestroy();
        //iflytekWakeUp.destroyWakeuper();
        app.appFinish();
    }

    //--------------------------------------------

    //长按说话
    private void TestSpeak(){
        btn_speak.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_UP){
                    L.i("开始讲话");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            //--------------------------唤醒成功-----------------------------
                            if (wordsToVoice.isTtsSpeaking()){
                                wordsToVoice.mTtsStop();
                            }
                            playingmusic(MusicPlayerService.REDUCE_MUSIC_VOLUME,"");    //音乐声变小
                            isPlayingMusic = false;
                            //播放唤醒声
                            soundPool.play(soundid, 1.0f, 1.0f, 0, 0, 1.0f);
                            /*if (iflytekWakeUp.isIvwListening()){
                                iflytekWakeUp.stopWakeuper();
                            }*/
                            //voiceToWords.startRecognizer();
                        }
                    });
                }
                return false;
            }
        });
    }


    //初始化android设备各项功能
    boolean isWifiClose = true;
    private void initAndroidFunctionState(){
        //判断网络状态
        if (!NetworkStateUtil.isNetworkAvailable(this)){
            L.i("当前没网络");
            @SuppressLint("WifiManagerLeak") final WifiManager wifiManager = (WifiManager)getSystemService(Context.WIFI_SERVICE);
            if(wifiManager != null && wifiManager.getWifiState()==wifiManager.WIFI_STATE_DISABLED){
                L.i("开启wifi");
                wifiManager.setWifiEnabled(true);

                new Thread(){
                    @Override
                    public void run() {
                        super.run();
                        while (isWifiClose){
                            try {
                                sleep(1000);
                                if (wifiManager.getWifiState()==wifiManager.WIFI_STATE_ENABLED){
                                    isWifiClose = false;
                                }
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        /*L.i("连接指定的网络");
                        WifiAutoConnectManager wifiAutoConnectManager = new WifiAutoConnectManager(wifiManager);
                        wifiAutoConnectManager.connect("FITME-GUEST","28230152", WifiAutoConnectManager.WifiCipherType.WIFICIPHER_WPA);*/
                    }
                }.start();
            }else if (wifiManager != null && wifiManager.getWifiState()==wifiManager.WIFI_STATE_ENABLED){
                /*L.i("连接指定的网络");
                WifiAutoConnectManager wifiAutoConnectManager = new WifiAutoConnectManager(wifiManager);
                wifiAutoConnectManager.connect("FITME-GUEST","28230152", WifiAutoConnectManager.WifiCipherType.WIFICIPHER_WPA);*/
            }
        }else {
            //有网络,初始化其他组件

        }
    }


    /**
     * 讯飞唤醒词监听类
     *
     * @author Administrator
     */
    public class MyWakeuperListener implements WakeuperListener {
        //开始说话
        @Override
        public void onBeginOfSpeech() {
            L.i("讯飞唤醒开始说话");
        }

        //错误码返回
        @Override
        public void onError(SpeechError arg0) {
            L.i("讯飞唤醒错误码返回:"+arg0.toString());
        }

        @Override
        public void onEvent(int arg0, int arg1, int arg2, Bundle arg3) {
        }

        @Override
        public void onVolumeChanged(int i) {
        }

        @Override
        public void onResult(WakeuperResult result) {

            if (!"1".equalsIgnoreCase("1")) {
                //setRadioEnable(true);
            }
            try {
                String text = result.getResultString();
                JSONObject object;
                object = new JSONObject(text);
                StringBuffer buffer = new StringBuffer();
                buffer.append("【RAW】 " + text);
                buffer.append("\n");
                buffer.append("【操作类型】" + object.optString("sst"));
                buffer.append("\n");
                buffer.append("【唤醒词id】" + object.optString("id"));
                buffer.append("\n");
                buffer.append("【得分】" + object.optString("score"));
                buffer.append("\n");
                buffer.append("【前端点】" + object.optString("bos"));
                buffer.append("\n");
                buffer.append("【尾端点】" + object.optString("eos"));
                String resultString = buffer.toString();
                L.i("resultString:"+resultString);
                //讯飞唤醒！！！！！！！！！！！！！！！！！！！！
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //--------------------------唤醒成功-----------------------------
                        if (wordsToVoice.isTtsSpeaking()){
                            wordsToVoice.mTtsStop();
                        }
                        playingmusic(MusicPlayerService.REDUCE_MUSIC_VOLUME,"");    //音乐声变小
                        isPlayingMusic = false;
                        //播放唤醒声
                        soundPool.play(soundid, 1.0f, 1.0f, 0, 0, 1.0f);
                        /*if (iflytekWakeUp.isIvwListening()){
                            iflytekWakeUp.stopWakeuper();
                        }*/
                        //voiceToWords.startRecognizer();
                    }
                });
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }
    }

    /*
    * 讯飞语音识别回调
    * */
    @Override
    public void getResult(String result) {
        L.i("讯飞语音识别回调getResult----------------------"+result);
        tvXunfeiASR.setText(result);
    }

    @Override
    public void showLowVoice(String result) {
        L.i("讯飞语音识别回调showLowVoice----------------------"+result);
        wordsToVoice.startSynthesizer(result,mTtsListener);
        /*if (!iflytekWakeUp.isIvwListening()){
            iflytekWakeUp.startWakeuper();
        }*/
    }

    @Override
    public void appendResult(CharSequence sequence) {
        L.i("讯飞语音识别回调appendResult----------------------"+sequence.toString());
        /*if (!iflytekWakeUp.isIvwListening()){
            iflytekWakeUp.startWakeuper();
        }*/
        //playingmusic(MusicPlayerService.RECOVER_MUSIC_VOLUME,"");

        String sendMsg = sequence.toString();
        if (scene(sendMsg,"马上到家")||scene(sendMsg,"快到家了")||scene(sendMsg,"我要到家了")){
            L.i("1");
            blControl.commandRedCodeDevice(BLControlConstants.MAIN_LIGHT_OPEN, BLControlConstants.RM_PRO_DID);   //开灯
            blControl.commandRedCodeDevice(BLControlConstants.GARAGE_DOOR, BLControlConstants.RM_PRO_DID);   //车库门
            blControl.commandRedCodeDevice(BLControlConstants.CURTAIN, BLControlConstants.RM_PRO_DID);
            blControl.commandRedCodeDevice(BLControlConstants.AIR, BLControlConstants.RM_PRO_DID);
            handler.sendEmptyMessageDelayed(1,10000);
            wordsToVoice.startSynthesizer("欢迎回家，已为你打开回家模式",mTtsListener);

        }else if (scene(sendMsg,"我要出门")||scene(sendMsg,"准备出门")){
            L.i("2");
            blControl.commandRedCodeDevice(BLControlConstants.MAIN_LIGHT_CLOSE, BLControlConstants.RM_PRO_DID);   //关灯
            blControl.commandRedCodeDevice(BLControlConstants.GARAGE_DOOR, BLControlConstants.RM_PRO_DID);   //车库门
            handler.sendEmptyMessageDelayed(1,10000);
            blControl.commandRedCodeDevice(BLControlConstants.CURTAIN, BLControlConstants.RM_PRO_DID);
            wordsToVoice.startSynthesizer("已为你打开离家模式，祝你今天过得愉快哦",mTtsListener);
        }else if (scene(sendMsg,"要睡觉")||scene(sendMsg,"我想睡觉")){
            L.i("3");
            blControl.commandRedCodeDevice(BLControlConstants.MAIN_LIGHT_CLOSE, BLControlConstants.RM_PRO_DID);
            //blControl.commandRedCodeDevice(BLControlConstants.TV_ON_OFF, BLControlConstants.RM_PRO_DID);
            blControl.commandRedCodeDevice(BLControlConstants.CURTAIN, BLControlConstants.RM_PRO_DID);
            wordsToVoice.startSynthesizer("已为你打开睡眠模式，晚安，好梦",mTtsListener);
        }else if (scene(sendMsg,"要起床")){
            L.i("4");
            blControl.commandRedCodeDevice(BLControlConstants.CURTAIN, BLControlConstants.RM_PRO_DID);
            blControl.commandRedCodeDevice(BLControlConstants.MAIN_LIGHT_OPEN, BLControlConstants.RM_PRO_DID);
            //wordsToVoice.startSynthesizer("早安，已为你打开起床模式",mTtsListener);
            messageCreat(NetworkStateUtil.getLocalMacAddressFromWifiInfo(MainActivity.this)
                    ,String.valueOf(app.getLongitude()),String.valueOf(app.getLatitude()),"device_text","我要起床了","13145");
        }else if (scene(sendMsg,"开派对")){
            L.i("5");
            blControl.commandRedCodeDevice(BLControlConstants.MAIN_LIGHT_FLASH, BLControlConstants.RM_PRO_DID);
            //blControl.commandRedCodeDevice(BLControlConstants.TV_ON_OFF, BLControlConstants.RM_PRO_DID);
            wordsToVoice.startSynthesizer("已为你打开派对模式，玩得开心点哦",mTtsListener);
        }else if (scene(sendMsg,"小朋友要过来")||scene(sendMsg,"有小朋友")){
            L.i("6");
            blControl.commandRedCodeDevice(BLControlConstants.MAIN_LIGHT_ORENGE, BLControlConstants.RM_PRO_DID);
            wordsToVoice.startSynthesizer("已为你打开亲子模式，祝你玩得愉快哦",mTtsListener);
        }else if (scene(sendMsg,"室内温度")||scene(sendMsg,"室内空气质量")||scene(sendMsg,"环境噪音")||scene(sendMsg,"室内空气湿度")){
            String strResult = blControl.dnaPassthrough();     //获取空气检测仪数据
            wordsToVoice.startSynthesizer(strResult,mTtsListener);
        }else if (scene(sendMsg,"热门")&&scene(sendMsg,"电视剧")){
            messageCreat(NetworkStateUtil.getLocalMacAddressFromWifiInfo(MainActivity.this)
                    ,String.valueOf(app.getLongitude()),String.valueOf(app.getLatitude()),"device_text","我要看楚乔传","13145");
            L.i("10");
        }else {
            //发送消息请求
            if (!"。".equals(sendMsg)){
                messageCreat(NetworkStateUtil.getLocalMacAddressFromWifiInfo(MainActivity.this)
                        ,String.valueOf(app.getLongitude()),String.valueOf(app.getLatitude()),"device_text",sendMsg,"13145");
            }
        }


    }

    //正则判断场景
    private boolean scene(String sendMsg,String regEx) {
        Pattern pattern = Pattern.compile(regEx);
        Matcher matcher = pattern.matcher(sendMsg);
        // 查找字符串中是否有匹配正则表达式的字符/字符串
        boolean rs = matcher.find();
        L.i("是否找到该字符："+rs);
        return rs;
    }

    //初始化博联,获取设备信息
    private String did;   //设备id
    private String mac;   //设备mac地址
    private String pid;   //设备产品id
    private String name;   //设备名称
    private int type;      //设备类型
    private boolean lock;    //是否锁定
    private boolean newconfig;   //是否新配置的设备
    private int password;    //一代设备控制密码
    private int controlId;    //设备控制id
    private String controlKey;   //设备控制密匙
    private int state;    //设备网络状态
    private String lanaddr;      //设备局域网id地址
    private String pDid;    //子设备网管设备的did

    private BLStdControlParam blStdControlParam;      //控制设备参数
    private BLConfigParam blConfigParam;        //控制超时时间，脚本路径配置

    private Map blDNADevicesMap = new HashMap();
    private void initBoardLink(){
        //直接登陆
        new LoginWithoutNameTask().execute();
        //开启设备扫描
        BLLet.Controller.startProbe();
        //回调获取设备信息
        BLLet.Controller.setOnDeviceScanListener(new BLDeviceScanListener() {
            @Override
            public void onDeviceUpdate(BLDNADevice bldnaDevice, boolean b) {

                did = bldnaDevice.getDid();
                mac = bldnaDevice.getMac();
                pid = bldnaDevice.getPid();
                name = bldnaDevice.getName();
                type = bldnaDevice.getType();
                lock = bldnaDevice.isLock();
                newconfig = bldnaDevice.isNewconfig();
                password = (int) bldnaDevice.getPassword();
                controlId = bldnaDevice.getId();
                controlKey = bldnaDevice.getKey();
                state = bldnaDevice.getState();
                lanaddr = bldnaDevice.getLanaddr();
                pDid = bldnaDevice.getpDid();
                L.i("设备name:"+name+"---------type:"+type);
                if (type==10039){
                    blDNADevicesMap.put("rm",bldnaDevice);
                }else if (type==30014){
                    blDNADevicesMap.put("sp",bldnaDevice);
                }else if (type==10004){
                    blDNADevicesMap.put("a1",bldnaDevice);
                }else if (type==10018){
                    blDNADevicesMap.put("s1",bldnaDevice);
                }else if (type==10026){
                    blDNADevicesMap.put("rmpro",bldnaDevice);
                }else if (type==20149){
                    blDNADevicesMap.put("mp1",bldnaDevice);
                }else if (type==20045){
                    blDNADevicesMap.put("curtain",bldnaDevice);
                    BLLet.Controller.bindWithServer(bldnaDevice);
                }

                String state = querydeviceState(did);
                L.i("设备信息："+new Gson().toJson(bldnaDevice));
                L.i("设备did："+did+"----对应的设备状态:"+state+"---是否新设备："+b);
                //扫描得到设备后，添加到SDK
                BLLet.Controller.addDevice(bldnaDevice);

                //设备配对，用于获取设备控制密匙
                BLPairResult blPairResult = BLLet.Controller.pair(bldnaDevice);
                L.i("设备控制密匙:"+blPairResult.getKey()+"---设备控制id"+blPairResult.getId());


                //下载脚本
                BLDownloadScriptResult blDownloadScriptResult = BLLet.Controller.downloadScript(pid);
                String scriptSavePath = blDownloadScriptResult.getSavePath();

                L.i("设备分类--pid:"+pid+"-------下载的脚本地址："+scriptSavePath);

                //查询设备profile
                BLProfileStringResult blProfileStringResult = BLLet.Controller.queryProfile(did);
                L.i("查询设备profile:"+blProfileStringResult.getProfile());



                //关闭设备扫描
                new Thread(){
                    @Override
                    public void run() {
                        super.run();
                        try {
                            sleep(15000);
                            //BLLet.Controller.stopProbe();
                            L.i("停止扫描");
                            handler.sendEmptyMessage(2);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }.start();

            }
        });
    }



    //查询设备的网络状态
    private String querydeviceState(String did){
        int state = BLLet.Controller.queryDeviceState(did);
        if (state==0){
            return "还未获取到设备状态";
        }else if (state==1){
            return "设备和手机在同一局域网";
        }else if (state==2){
            return "设备连接到服务器，和手机不在同一局域网";
        }else {
            return "设备未连接服务器，不在线";
        }

    }


    //登录
    private class LoginWithoutNameTask extends AsyncTask<String, Void, BLLoginResult> {
        ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog = new ProgressDialog(MainActivity.this);
            progressDialog.setMessage("登录中...");
            progressDialog.show();
        }

        @Override
        protected BLLoginResult doInBackground(String... params) {
            String thirdId = "2048549";
            return BLLet.Account.thirdAuth(thirdId);
        }

        @Override
        protected void onPostExecute(BLLoginResult loginResult) {
            super.onPostExecute(loginResult);
            progressDialog.dismiss();
            if(loginResult != null && loginResult.getError() == BLAccountErrCode.SUCCESS){
                //保存登录信息
                L.i("登录信息:"+loginResult.getUserid());
            }
        }
    }

    //发送客户消息请求
    private void messageCreat(String userId, String x, String y, final String messageType, String content, String password){
        String timeStamp = SignAndEncrypt.getTimeStamp();

        Gson gson = new Gson();
        HashMap<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("x", "1200239912");  //302840378801,1200239912204
        params.put("y", "302840378");
        params.put("message_type", messageType);
        HashMap<String, Object> map = new HashMap<>();
        map.put("content", content);
        params.put("message_body", map);
        params.put("password",password);
        L.i("---------发出的json-"+gson.toJson(params));

        LinkedHashMap<String, Object> par = new LinkedHashMap<>();
        par.put("method", "message/from_customer/create");
        par.put("api_key", ApiManager.api_key);
        par.put("timestamp", timeStamp);
        par.put("http_body", gson.toJson(params));
        String sign = SignAndEncrypt.signRequest(par, ApiManager.api_secret);
        ApiManager.fitmeApiService.messageCreateVB(ApiManager.api_key, timeStamp, sign,params)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<MessageGet>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        L.i("错误信息："+e.toString());
                        wordsToVoice.startSynthesizer("小秘正在开小差",mTtsListener);
                    }

                    @Override
                    public void onNext(MessageGet messageGet) {
                        /*try {
                            L.logE("json:"+messageGet.string());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }*/

                        L.logE("收到回复的消息:"+new Gson().toJson(messageGet));
                        tvResp.setText(new Gson().toJson(messageGet));
                        //成功收到回复的消息
                        if (null!=messageGet.getStatus()&&"success".equals(messageGet.getStatus())){
                            L.logE("成功收到回复的消息");
                            if ("text".equals(messageGet.getMessages()[0].getMessage_type())){        //单句回复
                                L.logE("单句回复");
                                wordsToVoice.startSynthesizer(messageGet.getMessages()[0].getMessage_body().getContent(),mTtsListener);
                            } else if ("multiline_text".equals(messageGet.getMessages()[0].getMessage_type())){         //多句回复
                                L.logE("多句回复");
                                doMultilineText(messageGet.getMessages()[0].getMessage_body().getContents());
                            }else if ("task_result".equals(messageGet.getMessages()[0].getMessage_type())){        //控制命令或音乐
                                L.logE("控制命令或音乐");
                                if ("query_music".equals(messageGet.getMessages()[0].getMessage_body().getTask_type())){     //查询音乐
                                    L.logE("查询音乐");
                                    String speechText = messageGet.getMessages()[0].getMessage_body().getTask_result_speech_text();
                                    int musicsNum = messageGet.getMessages()[0].getMessage_body().getTask_result_body().getMusics().size();
                                    musicList = new LinkedList<Music>();
                                    for (int i=0;i<musicsNum;i++){
                                        musicList.add(messageGet.getMessages()[0].getMessage_body().getTask_result_body().getMusics().get(i));
                                    }
                                    //初始化歌单
                                    initMusicList(musicList);
                                    playingmusic(MusicPlayerService.NEXT_MUSIC,musicUrl.get(currentPlaySongIndex));
                                    isPlayingMusic = true;
                                }else if ("command".equals(messageGet.getMessages()[0].getMessage_body().getTask_type())){
                                    //控制
                                    //控制
                                    L.logE("控制");
                                    playingmusic(MusicPlayerService.RECOVER_MUSIC_VOLUME,"");   //恢复音乐音量

                                    int devicesLength = messageGet.getMessages()[0].getMessage_body().getTask_result_body().getDevices().size();
                                    for (int i=0;i<devicesLength;i++){
                                        int deviceType = messageGet.getMessages()[0].getMessage_body().getTask_result_body().getDevices().get(i).getDevice_type();
                                        if (20045==deviceType){  //杜亚窗帘
                                            L.logE("杜亚窗帘");
                                            blControl.dnaControlSet("curtain",messageGet.getMessages()[0].getMessage_body().getTask_result_body().getDevices().get(i).getCommand_code(),"curtain_work");
                                            //blControl.curtainControl(Integer.parseInt(messageGet.getMessages()[0].getMessage_body().getTask_result_body().getCommand_code()));
                                        }else if (10026==deviceType || 10039==deviceType){     //RM红外遥控
                                            L.logE("RM红外遥控");
                                            blControl.commandRedCodeDevice(messageGet.getMessages()[0].getMessage_body().getTask_result_body().getDevices().get(i).getCommand_code(),
                                                    messageGet.getMessages()[0].getMessage_body().getTask_result_body().getDevices().get(i).getDid());
                                        }else if (30014==deviceType){     //SP系列wifi开关
                                            blControl.dnaControlSet("sp","1","val");
                                        }else if (20149==deviceType){        //四位排插

                                        }
                                    }

                                }else if ("music_command".equals(messageGet.getMessages()[0].getMessage_body().getTask_type())){
                                    commandMusicPlayer(messageGet.getMessages()[0].getMessage_body().getTask_result_body().getCommand());
                                }else if ("tv_command".equals(messageGet.getMessages()[0].getMessage_body().getTask_type())){
                                    //控制电视播放
                                    wordsToVoice.startSynthesizer("正在电视设备上为您播放："+messageGet.getMessages()[0].getMessage_body().getTask_result_body().getFilm_name(),mTtsListener);
                                }else if ("box_command".equals(messageGet.getMessages()[0].getMessage_body().getTask_type())){
                                    if ("next_page".equals(messageGet.getMessages()[0].getMessage_body().getTask_result_body().getCommand())){
                                        //下一页
                                        L.i("电视盒子下一页");
                                        blControl.commandRedCodeDevice(BLControlConstants.TV_BOX_NEXT_PAGE, BLControlConstants.RM_MINI_DID);
                                    }else if ("prev_page".equals(messageGet.getMessages()[0].getMessage_body().getTask_result_body().getCommand())){
                                        //上一页
                                        L.i("电视盒子上一页");
                                        blControl.commandRedCodeDevice(BLControlConstants.TV_BOX_PRE_PAGE, BLControlConstants.RM_MINI_DID);
                                    }
                                }
                            }
                        }


                    }
                });
    }


    //处理多个回复
    private void doMultilineText(String[] multiline_text){
        wordsToVoice.startSynthesizer(multiline_text[0],mTtsListener);
        //后续还要处理
    }


    //初始化音乐列表
    private List<String> musicUrl = null;
    private int musicListSize = 0;
    private int currentPlaySongIndex = 0;
    private void initMusicList(List<Music> musicList){
        musicUrl = new LinkedList<>();
        musicListSize = musicList.size();
        currentPlaySongIndex = 0;     //当前播放的歌曲在歌单中的位置
        for (int i=0;i<musicListSize;i++){
            musicUrl.add(musicList.get(i).getSong_url());
        }
    }





    //控制音乐播放器
    private void commandMusicPlayer(String command){
        switch (command){
            case "next":      //下一曲
                if (currentPlaySongIndex==(musicListSize-1)){
                    currentPlaySongIndex = 0;
                }else {
                    currentPlaySongIndex++;
                }
                Toast.makeText(this, "哪一首："+currentPlaySongIndex, Toast.LENGTH_SHORT).show();
                playingmusic(MusicPlayerService.NEXT_MUSIC,musicUrl.get(currentPlaySongIndex));
                break;
            case "prev":      //上一曲
                if (currentPlaySongIndex==0){
                    currentPlaySongIndex = musicListSize-1;
                }else {
                    currentPlaySongIndex--;
                }
                Toast.makeText(this, "哪一首："+currentPlaySongIndex, Toast.LENGTH_SHORT).show();
                playingmusic(MusicPlayerService.NEXT_MUSIC,musicUrl.get(currentPlaySongIndex));
                break;
            case "down":
                L.i("音量减");
                mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_LOWER,
                        AudioManager.FX_FOCUS_NAVIGATION_UP);
                mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_LOWER,
                        AudioManager.FX_FOCUS_NAVIGATION_UP);
                mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_LOWER,
                        AudioManager.FX_FOCUS_NAVIGATION_UP);
                currentVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                Toast.makeText(this, "当前音量"+currentVolume, Toast.LENGTH_SHORT).show();
                break;
            case "up":
                L.i("音量加");
                mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_RAISE,
                        AudioManager.FX_FOCUS_NAVIGATION_UP);
                mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_RAISE,
                        AudioManager.FX_FOCUS_NAVIGATION_UP);
                mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_RAISE,
                        AudioManager.FX_FOCUS_NAVIGATION_UP);
                currentVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                Toast.makeText(this, "当前音量"+currentVolume, Toast.LENGTH_SHORT).show();
                break;
            case "pause":
                playingmusic(MusicPlayerService.PAUSE_MUSIC,"");
                break;
            case "stop":
                playingmusic(MusicPlayerService.STOP_MUSIC,"");
                break;
            case "play":
                playingmusic(MusicPlayerService.RESUME_MUSIC,"");
                break;
            case "max":
                currentVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, AudioManager.FLAG_PLAY_SOUND);
                break;
            case "mini":
                currentVolume = 0;
                mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, AudioManager.FLAG_PLAY_SOUND);
                break;
        }
    }


    //发送指令到音乐播放的service
    private List<Music> musicList;
    private boolean isPlayingMusic;
    private void playingmusic(int type,String songUrl) {
        //判断是否放新一曲
        if (type== MusicPlayerService.PLAT_MUSIC||type== MusicPlayerService.NEXT_MUSIC){
            String strMusicInfo = musicList.get(currentPlaySongIndex).getSinger()+","+musicList.get(currentPlaySongIndex).getName();
            wordsToVoice.startSynthesizer("正在为您播放："+strMusicInfo,mTtsListener);
        }
        //启动服务，播放音乐
        Intent intent = new Intent(this,MusicPlayerService.class);
        intent.putExtra("type",type);
        intent.putExtra("songUrl",songUrl);
        startService(intent);
    }

    /**
     * 合成回调监听。
     */
    private SynthesizerListener mTtsListener = new SynthesizerListener() {

        @Override
        public void onSpeakBegin() {
            //showTip("开始播放");
            L.i("语音合成回调监听-----------"+"开始播放");
            playingmusic(MusicPlayerService.REDUCE_MUSIC_VOLUME,"");
        }

        @Override
        public void onSpeakPaused() {
            // showTip("暂停播放");
            L.i("语音合成回调监听-----------"+"暂停播放");
        }

        @Override
        public void onSpeakResumed() {
            //showTip("继续播放");
            L.i("语音合成回调监听-----------"+"继续播放");
        }

        @Override
        public void onBufferProgress(int percent, int beginPos, int endPos,
                                     String info) {
            // 合成进度
        }

        @Override
        public void onSpeakProgress(int percent, int beginPos, int endPos) {
            // 播放进度
        }

        @Override
        public void onCompleted(SpeechError error) {
            if (error == null) {
                L.i("语音合成回调监听-----------"+"播放完成");
                //恢复音乐音量
                playingmusic(MusicPlayerService.RECOVER_MUSIC_VOLUME,"");
            } else if (error != null) {
                //showTip(error.getPlainDescription(true));
                L.i("语音合成回调监听-------错误----"+error.getPlainDescription(true));
            }
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
            // 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
            // 若使用本地能力，会话id为null
            //	if (SpeechEvent.EVENT_SESSION_ID == eventType) {
            //		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
            //		Log.d(TAG, "session id =" + sid);
            //	}
        }
    };


    //绑定设备
    private void bindDevice(){
        String mac = Mac.getMac();
        L.i("mac地址为："+mac);
        //暂时把mac地址写死
        mac = "ac:83:f3:3f:7f:ae";
        //mac = "ac:83:f3:3f:7f:a3";
        String timeStamp = SignAndEncrypt.getTimeStamp();
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("method", "account/device/create");
        params.put("api_key", ApiManager.api_key);
        params.put("timestamp", timeStamp);

        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        List<LinkedHashMap> devices = new ArrayList<>();

        LinkedHashMap<String, Object> mapDevices = new LinkedHashMap<>();
        mapDevices.put("identifier",mac);
        mapDevices.put("did","");
        mapDevices.put("nickname","fitme小音箱");
        mapDevices.put("pid","");
        mapDevices.put("mac",mac);
        mapDevices.put("device_name","fitmeVoiceBox");
        mapDevices.put("device_lock","");
        mapDevices.put("device_type","");
        mapDevices.put("category","");
        mapDevices.put("command","");
        mapDevices.put("command_code","");
        mapDevices.put("user_group","客厅");
        devices.add(mapDevices);

        map.put("user_id", "124");   //1067
        map.put("devices", devices);

        Gson gson = new Gson();
        params.put("http_body", gson.toJson(map));
        L.i("http_body:"+gson.toJson(map));
        String sign = SignAndEncrypt.signRequest(params, ApiManager.api_secret);
        ApiManager.fitmeApiService.deviceBind(ApiManager.api_key, timeStamp, sign, map)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<ResponseBody>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {
                    }

                    @Override
                    public void onNext(ResponseBody responseBody) {
                        try {
                            L.i("绑定服务器回复："+responseBody.string());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode==KeyEvent.KEYCODE_BACK){
            System.exit(0);
        }
        return super.onKeyDown(keyCode, event);
    }

}
