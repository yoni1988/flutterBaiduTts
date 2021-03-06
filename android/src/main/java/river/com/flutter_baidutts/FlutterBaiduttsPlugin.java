package river.com.flutter_baidutts;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.baidu.tts.client.SpeechSynthesizer;
import com.baidu.tts.client.TtsMode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/**
 * FlutterBaiduttsPlugin
 */
public class FlutterBaiduttsPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware, PluginRegistry.RequestPermissionsResultListener, IOfflineResourceConst {
    private SpeechSynthesizer mSpeechSynthesizer;
    private String appId;
    private String appKey;
    private String appSecret;
    private TtsMode ttsMode = DEFAULT_OFFLINE_TTS_MODE;
    private OfflineResource offlineResource;
    private String TAG = "TTS";
    private int requestCode = 777;
    private Context context;
    public Activity activity;
    private MethodChannel channel;

    public FlutterBaiduttsPlugin() {
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        onAttachedToEngine(flutterPluginBinding.getApplicationContext(), flutterPluginBinding.getBinaryMessenger());
    }

    public static void registerWith(Registrar registrar) {
        final FlutterBaiduttsPlugin instance = new FlutterBaiduttsPlugin();

        instance.activity = registrar.activity();
        instance.onAttachedToEngine(registrar.context(), registrar.messenger());
    }

    public void onAttachedToEngine(Context context, BinaryMessenger messenger) {
        this.context = context;

        channel = new MethodChannel(messenger, "flutter_baidutts");

        channel.setMethodCallHandler(this);
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        Log.i(TAG, "onMethodCall: " + call.method);
        if (call.method.equals("init")) {
            init(call, result);
        } else if (call.method.equals("speak")) {
            speak(call, result);
        } else if (call.method.equals("setVolume")) {
            setVolume(call, result);
        } else if (call.method.equals("pause")) {
            pause(call, result);
        } else if (call.method.equals("resume")) {
            resume(call, result);
        } else if (call.method.equals("stop")) {
            stop(call, result);
        } else {
            result.notImplemented();
        }
    }

    private void stop(MethodCall call, Result result) {
        int res = mSpeechSynthesizer.stop();
        result.success(res);
    }

    private void resume(MethodCall call, Result result) {
        int res = mSpeechSynthesizer.resume();
        result.success(res);
    }

    private void pause(MethodCall call, Result result) {
        int res = mSpeechSynthesizer.pause();
        result.success(res);
    }

    private void setVolume(MethodCall call, Result result) {
        Double volume =  call.argument("volume");

        int res = mSpeechSynthesizer.setStereoVolume(volume.floatValue(), volume.floatValue());
        result.success(res);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        activity = null;
        context = null;
        channel.setMethodCallHandler(null);
        channel = null;
    }

    public void init(@NonNull MethodCall call, @NonNull Result result) {
        //??????????????????
        appId = call.argument("appId");
        appKey = call.argument("appKey");
        appSecret = call.argument("appSecret");

        //???????????????
        initPermission();

        //??????????????????????????????
        offlineResource = createOfflineResource(VOICE_FEMALE);

        boolean isMixOrOffline = ttsMode.equals(TtsMode.MIX);

        boolean isSuccess;

        if (isMixOrOffline) {
            // ??????2???????????????????????????
            isSuccess = checkOfflineResources();
            if (!isSuccess) {
                //???tts???????????????????????????
                ttsMode = TtsMode.ONLINE;
                isMixOrOffline = false;
            } else {
                print("??????????????????????????????, ?????????" + FileUtil.createTmpDir(context));
            }
        }

        // 1. ????????????
        mSpeechSynthesizer = SpeechSynthesizer.getInstance();
        mSpeechSynthesizer.setContext(context);

        //??????????????????
        MessageListener listener = new MessageListener();
        mSpeechSynthesizer.setSpeechSynthesizerListener(listener);

        // 2. ??????appId???appKey.secretKey
        mSpeechSynthesizer.setApiKey(appKey, appSecret);
        mSpeechSynthesizer.setAppId(appId);

        // 3. ?????????????????????????????????????????????
        if (isMixOrOffline) {
            // ???????????????????????? (??????????????????)??? ??????TEXT_FILENAME????????????????????????
            mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_TTS_TEXT_MODEL_FILE, offlineResource.getTextFilename());
            // ???????????????????????? (??????????????????)??? ??????TEXT_FILENAME????????????????????????
            mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_TTS_SPEECH_MODEL_FILE, offlineResource.getModelFilename());
        }

        // 4. ??????setParam ??????????????????????????????????????????
        // ??????????????????????????? 0 ???????????????????????? 1 ???????????? 2 ???????????? 3 ????????????<?????????> 4 ???????????????<?????????>
        mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_SPEAKER, "0");
        // ????????????????????????0-15 ????????? 5
        mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_VOLUME, "10");

        // 5. ?????????
        int code = mSpeechSynthesizer.initTts(ttsMode);
        print("???????????????????????????:" + code);

        result.success(code);
    }

    public void speak(@NonNull MethodCall call, @NonNull Result result) {
        String word = call.argument("word");
        int res = mSpeechSynthesizer.speak(word);

        result.success(res);
    }

    /**
     * ?????? TEXT_FILENAME, MODEL_FILENAME ???2?????????????????????????????????????????????assets?????????????????????
     *
     * @return ??????????????????
     */
    private boolean checkOfflineResources() {
        if (offlineResource == null) {
            return false;
        }
        String[] filenames = {offlineResource.getTextFilename(), offlineResource.getModelFilename()};
        for (String path : filenames) {
            File f = new File(path);
            if (!f.canRead()) {
                print("[ERROR] ??????????????????????????????????????????demo???assets??????????????????????????????"
                        + f.getAbsolutePath());
                print("[ERROR] ????????????????????????");
                return false;
            }
        }
        return true;
    }

    private void print(String message) {
        Log.i(TAG, message);
    }

    protected OfflineResource createOfflineResource(String voiceType) {
        OfflineResource offlineResource = null;
        try {
            offlineResource = new OfflineResource(context, voiceType);
        } catch (IOException e) {
            // IO ??????????????????
            e.printStackTrace();
            print("???error???:copy files from assets failed." + e.getMessage());

            offlineResource = null;
        }
        return offlineResource;
    }

    private void initPermission() {
        String[] permissions = {
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.MODIFY_AUDIO_SETTINGS,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE
        };

        ArrayList<String> toApplyList = new ArrayList<>();

        for (String perm : permissions) {
            if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(context, perm)) {
                toApplyList.add(perm);
                Log.i(TAG, "????????????: " + perm);
            }
        }
        String[] tmpList = new String[toApplyList.size()];
        if (!toApplyList.isEmpty()) {
            ActivityCompat.requestPermissions(activity, toApplyList.toArray(tmpList), requestCode);
        }

    }

    @Override
    public void onAttachedToActivity(ActivityPluginBinding activityPluginBinding) {
        this.activity = activityPluginBinding.getActivity();
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {

    }

    @Override
    public void onReattachedToActivityForConfigChanges(ActivityPluginBinding activityPluginBinding) {
        this.activity = activityPluginBinding.getActivity();
    }

    @Override
    public void onDetachedFromActivity() {
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        //???????????????????????????
        if (requestCode == this.requestCode && permissions.length == grantResults.length) {
            Log.i(TAG, "onRequestPermissionsResult: ???????????????????????????");
        } else {
        }
        return false;
    }
}
