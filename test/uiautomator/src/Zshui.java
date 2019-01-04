//package zsh.jl.zshui;
package zsh.jl.wxautofriend;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.app.UiAutomation;
import android.app.UiAutomationConnection;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.IClipboard;
import android.hardware.input.InputManager;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import android.os.Build;

import static android.hardware.input.InputManager.INJECT_INPUT_EVENT_MODE_ASYNC;

//import android.app.SystemServiceRegistry;


public class Zshui {
    private static final String HANDLER_THREAD_NAME = "UiAutomatorHandlerThread";
    private final HandlerThread mHandlerThread = new HandlerThread(HANDLER_THREAD_NAME);
    private UiAutomation mUiAutomation;
    private IActivityManager mAm;
    private ServerSocket socket;
    private boolean isEnable;
    private String lastErr = "";

    private static final String name = "com.android.adbkeyboard";
    private LocalSocket Client = null;
    private BufferedReader is = null;
    private int timeout = 30000;
    private InputManager inputManager;

    public static void main(String[] args) {
        try {
            if (args.length == 2 && args[1].equals("tcp")) {
                new Zshui(Integer.parseInt(args[0]), true);
            } else if (args.length == 1) {
                new Zshui(Integer.parseInt(args[0]), false);
            } else {
                showUsage();
            }
        } catch (IllegalArgumentException e) {

            System.err.println("Error: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace(System.err);
            //System.exit(1);
        }
    }

    private Zshui(int port, boolean tcp) {
        mAm = ActivityManagerNative.getDefault();
        inputManager = InputManager.getInstance();
        if (!mHandlerThread.isAlive()) {
//            throw new IllegalStateException("Already connected!");
            mHandlerThread.start();
        }

        mUiAutomation = new UiAutomation(mHandlerThread.getLooper(),
                new UiAutomationConnection());
        mUiAutomation.connect();
        if (tcp) {
            this.startServerAsync(port);
        } else {
            this.startHttp(port);
        }
    }

    public void performClick(AccessibilityNodeInfo node) {
        while (node != null && !node.isClickable()) {
            node = node.getParent();
        }
        if (node != null) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
    }
//
//    private Zshui(int port) {
//        Zshui(port,true);
//
////        InputManager inputManager = InputManager.getInstance();
//
////        InputMethodService.
//
////        IBinder binder = ServiceManager.getService("input_method");
////        System.out.println(binder);
////        IInputMethodManager inputMethodManager = IInputMethodManager.Stub.asInterface(binder);
//
////        if (inputMethodService == null)
////        {
////            System.out.println("null");
////        }
////        else
////        {
////            System.out.println("not null");
////        }
////        this.startServerAsync(port);
////        try {
////
////        } catch (IOException e) {
////            e.printStackTrace();
////            Log.e("Garri", e.getMessage());
////            return;
////        }
//    }

    private void startHttp(int port) {
        System.out.println("Started http server on port:" + port);
        MockWebServer server = new MockWebServer();
        final Dispatcher dispatcher = new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (request.getPath().equals("/invoke")) {
                    String json_string = request.getBody().readUtf8();
                    Log.d("Garri", "receive:" + json_string);
                    MockResponse response = new MockResponse();
                    try {
                        procedureCmd(response, json_string);
                    } catch (Exception e) {
                        JSONObject cmd = new JSONObject();
                        try {
                            cmd.put("code", 1);
                            cmd.put("message", e.toString());
                        } catch (JSONException e1) {
                            e1.printStackTrace();
                        }
                        response(response, cmd);
                    }

                    return response;
                }
                return new MockResponse().setResponseCode(404).setBody("{\"code\":404,\"data\":{}}");
            }
        };
        server.setDispatcher(dispatcher);
        try {
            InetAddress wildCard = new InetSocketAddress(0).getAddress();
            server.start(wildCard, port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startServerAsync(final int port) {
        System.out.println("Started tcp server on port:" + port);
        isEnable = true;
        doProcSync(port);
    }

    private void stopServerAsync() throws IOException {
        if (!isEnable) {
            return;
        }
        isEnable = false;
        socket.close();
        socket = null;
    }

    private void doProcSync(int port) {
        try {
            InetSocketAddress socketAddress = new InetSocketAddress(port);
            socket = new ServerSocket();
            socket.bind(socketAddress);
            while (isEnable) {
                final Socket remotePeer = socket.accept();
//                onAcceptRemotePeer(remotePeer);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Log.e("Garri", "remotePeer..............." + remotePeer.getRemoteSocketAddress().toString());
                        try {
                            onAcceptRemotePeer(remotePeer);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            }
        } catch (IOException e) {
            Log.e("Garri", e.getMessage());
            e.printStackTrace();
        }
    }

    private void onAcceptRemotePeer(Socket remotePeer) throws RemoteException {
        try {
            InputStream inputStream = remotePeer.getInputStream();
            int len;
            while (!remotePeer.isClosed()) {
                int readed = 8;
                String strInputstream;
                byte buffer[];
                int i;
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                while (readed > 0) {
                    buffer = new byte[readed];
                    i = inputStream.read(buffer, 0, readed);
                    if (i == -1) {
                        System.out.println("client closed.");
                        remotePeer.close();
                        return;
                    }
                    baos.write(buffer, 0, i);
                    readed = readed - i;
                    System.out.println("reading length ." + readed);
                }
                strInputstream = new String(baos.toByteArray());
                len = Integer.parseInt(strInputstream, 16);
//                int c = Integer.parseInt("0xFFFFFFFF");
//                len = inputStream.read();
                if (len <= 0) {
                    System.out.println("invalid package:len " + len);
                    remotePeer.close();
                    return;
                } else {
                    System.out.println("package:len " + len);
                }
                readed = len;
                baos = new ByteArrayOutputStream();
                while (readed > 0) {
                    buffer = new byte[readed];
                    i = inputStream.read(buffer, 0, readed);
                    if (i == -1) {
                        System.out.println("client closed.");
                        remotePeer.close();
                        return;
                    }
                    baos.write(buffer, 0, i);
                    readed = readed - i;
                }
                strInputstream = new String(baos.toByteArray());
                this.procedureCmd(remotePeer, strInputstream);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * action
     * data
     *
     * @param remotePeer
     * @param cmd
     */
    private void procedureCmd(Object remotePeer, String cmd) throws RemoteException {
        try {
            JSONObject j_cmd = new JSONObject(cmd);
            String action = j_cmd.getString("action");

            switch (action) {
                case "next":
                    this.next(remotePeer, j_cmd.getString("data"));
                    break;
                case "back":
                    this.back(remotePeer);
                    break;
                case "exit":
                    this.closeClient(remotePeer);
                    break;
                case "top":
                    this.topActivity(remotePeer);
                    break;
                case "package":
                    this.topPackage(remotePeer);
                    break;
                case "input":
                    this.input(remotePeer, j_cmd.getString("data"));
                    break;
                case "inputIME":
                    this.inputIME(remotePeer, j_cmd.getString("data"));
                    break;
                case "listBrowserUds":
                    this.listBrowserUds(remotePeer);
                    break;
                case "connectQQ":
//                    this.connectQQ();
                    break;
                case "runCmd":
                    this.runCmd(remotePeer, j_cmd.getString("data"));
                    break;
                case "injectJS":
                    this.injectJS(remotePeer, j_cmd.getString("data"));
                    break;
                case "hasNodeById":
                    this.hasNodeById(remotePeer, j_cmd.getJSONObject("data"));
                    break;
                case "test":
                    this.test(remotePeer);
                    break;
                default:
                    this.unknowCmd(remotePeer, action);
                    break;
            }
        } catch (JSONException e) {
            e.printStackTrace();
            this.error(remotePeer, e.getMessage());
        }
    }

    private void response(Object remotePeer, JSONObject cmd) {
        if (remotePeer instanceof Socket) {
            Socket socket = (Socket) remotePeer;
            byte response[] = cmd.toString().getBytes();
            try {
                socket.getOutputStream().write(response, 0, response.length);
                socket.getOutputStream().flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (remotePeer instanceof MockResponse) {
            MockResponse r = (MockResponse) remotePeer;
            Log.d("Garri", cmd.toString());
            r.setBody(cmd.toString());
        }
    }

    private void closeClient(Object remotePeer) {
        JSONObject cmd = new JSONObject();
        try {
            cmd.put("code", 0);
            cmd.put("message", "OK");
            if (remotePeer instanceof Socket) {
                Socket s = (Socket) remotePeer;
                s.close();
            }
            response(remotePeer, cmd);
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        }
    }

    private void topActivity(Object remotePeer) {
        JSONObject cmd = new JSONObject();
        String ret;
        try {
            ret = this.getTopActivityName();
            if (!ret.equals("")) {
                cmd.put("code", 0);
                cmd.put("message", "OK");
                cmd.put("data", ret);
            } else {
                cmd.put("code", 1);
                cmd.put("message", lastErr);
            }
            response(remotePeer, cmd);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void topPackage(Object remotePeer) {
        JSONObject cmd = new JSONObject();
        String ret;
        try {
            ret = this.getTopPackageName();
            if (!ret.equals("")) {
                cmd.put("code", 0);
                cmd.put("message", "OK");
                cmd.put("data", ret);
            } else {
                cmd.put("code", 1);
                cmd.put("message", lastErr);
            }
            response(remotePeer, cmd);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void input(Object remotePeer, String text) {
        JSONObject cmd = new JSONObject();
        try {
            run("input text \"" + text + "\"");
            cmd.put("code", 0);
            cmd.put("message", "OK");
            response(remotePeer, cmd);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void unknowCmd(Object remotePeer, String text) {
        JSONObject cmd = new JSONObject();
        try {
            cmd.put("code", 1);
            cmd.put("message", "unknown cmd:" + text);
            response(remotePeer, cmd);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void error(Object remotePeer, String text) {
        JSONObject cmd = new JSONObject();
        try {
            cmd.put("code", 1);
            cmd.put("message", text);
            response(remotePeer, cmd);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void inputIME(Object remotePeer, String text) {
        JSONObject cmd = new JSONObject();

        Log.d("Garri", text);
        System.out.println(text);
        try {
            Client = new LocalSocket();
            Client.connect(new LocalSocketAddress(name));
            Client.setSoTimeout(timeout);
            byte buf[] = text.getBytes();
            Client.getOutputStream().write(buf, 0, buf.length);
            Client.close();
            cmd.put("code", 0);
            cmd.put("message", "OK");
            response(remotePeer, cmd);
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }

    private void listBrowserUds(Object remotePeer) {
        //adb shell grep -a devtools_remote /proc/net/unix
        //https://github.com/auchenberg/adb-devtools-devices/blob/master/index.js
        //https://github.com/auchenberg/chrome-devtools-app/issues/37
        JSONObject cmd = new JSONObject();
        String uds = run("grep -a devtools_remote /proc/net/unix");
        System.out.println(uds);
        String[] strarray = uds.split("\n");
        String[] uds_item;
        String path;
        for (int i = 0; i < strarray.length; i++) {
            if (strarray[i].contains("@")) {
                uds_item = strarray[i].split("@");
                path = uds_item[1];
                System.out.println(path);
                UnixSocketFactory socketFactory = new UnixSocketFactory(path);
                OkHttpClient client = new OkHttpClient.Builder()
                        .socketFactory(socketFactory)
                        .dns(socketFactory)
                        .build();

                //json

                HttpUrl url = new HttpUrl.Builder()
                        .scheme("http")
                        .host("127.0.0.1")
                        .addPathSegment("json")
//                    .addPathSegment("version")
                        .build();

                Request request = new Request.Builder()
                        .url(url)
                        .build();
                Response response;
                try {
                    response = client
                            .newCall(request)
                            .execute();
                    ResponseBody body = response.body();
                    //System.out.println(body.string());
                    String response_txt = body.string();
                    JSONArray j = new JSONArray(response_txt);
                    cmd.put(path + "#json", j);

                } catch (IOException e) {
                    //e.printStackTrace();
                    System.out.println(e.getMessage());
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                //version
                url = new HttpUrl.Builder()
                        .scheme("http")
                        .host("127.0.0.1")
                        .addPathSegment("json")
                        .addPathSegment("version")
                        .build();

                request = new Request.Builder()
                        .url(url)
                        .build();
                try {
                    response = client
                            .newCall(request)
                            .execute();
                    ResponseBody body = response.body();
                    //System.out.println(body.string());
                    String response_txt = body.string();
                    JSONObject j = new JSONObject(response_txt);
                    cmd.put(path + "#version", j);

                } catch (IOException e) {
                    //e.printStackTrace();
                    System.out.println(e.getMessage());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        JSONObject rj = new JSONObject();
        try {
            rj.put("code", 0);
            rj.put("data", cmd);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        response(remotePeer, rj);
    }

    private void runCmd(Object remotePeer, String text) {
        String uds = run(text);
        JSONObject cmd = new JSONObject();
        try {
            cmd.put("code", 0);
            cmd.put("message", "OK");
            cmd.put("data", uds);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        response(remotePeer, cmd);
    }

    private void hasNodeById(Object remotePeer, JSONObject text) {
        JSONObject cmd = new JSONObject();
        if (text.has("id") && text.has("index")) {
            try {
                cmd.put("code", 0);
                cmd.put("message", "OK");
                AccessibilityNodeInfo root = mUiAutomation.getRootInActiveWindow();
                List<AccessibilityNodeInfo> nodeInfos = root.findAccessibilityNodeInfosByViewId(text.getString("id"));
                int index = text.getInt("index");
                if (index < nodeInfos.size()) {
                    cmd.put("data", nodeInfos.get(index).getClassName());
                } else {
                    cmd.put("data", 0);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            try {
                cmd.put("code", 1);
                cmd.put("message", "invalid parameters");
                cmd.put("data", "{'id':'.view_id','index':1}");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        response(remotePeer, cmd);
    }

    private void next(Object remotePeer, String phone_no) throws RemoteException, JSONException {
        AccessibilityNodeInfo node;

        ComponentName componentName = mAm.getTasks(1, 0,null).get(0).topActivity;
        //主页面
        if (componentName.getShortClassName().equals(UiViewIdConst.ACTIVITY_NAME_MAIN)) {
            node = getSearchBtn();
            if (node != null) {
                performClick(node);
                node.recycle();
                responseWxResult(remotePeer, 0, "next entry search", UiViewIdConst.RESPONSE_RESULT_WX_NEXT);
                return;
            }
            node = getFirstMainBtn();
            if (node != null) {
                performClick(node);
                node.recycle();
                responseWxResult(remotePeer, 0, "next entry first main page", UiViewIdConst.RESPONSE_RESULT_WX_NEXT);
                return;
            }
            responseWxResult(remotePeer, 1, "在HOME首页找不到搜索按钮", UiViewIdConst.RESPONSE_RESULT_WX_STOP);
            return;
        }
        //搜索页面
        if (componentName.getShortClassName().equals(UiViewIdConst.ACTIVITY_NAME_SEARCH)) {
            node = getNotFoundTextView();
            if (node != null) {
                node.recycle();
                responseWxResult(remotePeer, 0, "not found,back to main page", UiViewIdConst.RESPONSE_RESULT_WX_BACK);
                return;
            }

            node = getSearchResultBtn();
            if (node != null) {
                performClick(node);
                node.recycle();
                responseWxResult(remotePeer, 0, "next appear result", UiViewIdConst.RESPONSE_RESULT_WX_NEXT);
                return;
            }

            node = getSearchInput();
            if (node != null) {
                //node.performAction(AccessibilityNodeInfo.A);
                pasteText(node, phone_no);
                node.recycle();
                responseWxResult(remotePeer, 0, "next entry search", UiViewIdConst.RESPONSE_RESULT_WX_NEXT);
                return;
            }
            responseWxResult(remotePeer, 1, "在SEARCH首页找不到搜索按钮", UiViewIdConst.RESPONSE_RESULT_WX_STOP);
            return;
        }

        //添加联系人页面
        if (componentName.getShortClassName().equals(UiViewIdConst.ACTIVITY_NAME_CONTACT)) {
            node = getAddContactBtn();
            if (node != null) {
                performClick(node);
                node.recycle();
                responseWxResult(remotePeer, 0, "next entry search", UiViewIdConst.RESPONSE_RESULT_WX_NEXT);
                return;
            }
            responseWxResult(remotePeer, 1, "在联系人页面找不到添加按钮", UiViewIdConst.RESPONSE_RESULT_WX_STOP);
            return;
        }

        //发送验证页面
        if (componentName.getShortClassName().equals(UiViewIdConst.ACTIVITY_NAME_VERIFY)) {
            node = getPermissSendBtn();
            if (node != null) {
                performClick(node);
                node.recycle();
                responseWxResult(remotePeer, 0, "next entry search", UiViewIdConst.RESPONSE_RESULT_WX_DONE);
                return;
            }
            responseWxResult(remotePeer, 1, "在发送验证页面找不到发送按钮", UiViewIdConst.RESPONSE_RESULT_WX_STOP);
            return;
        }
        if (componentName.getPackageName().equals("com.tencent.mm")) {
            responseWxResult(remotePeer, 1, "未知 ACTIVITY", UiViewIdConst.RESPONSE_RESULT_WX_STOP);
        } else {
            responseWxResult(remotePeer, 0, "wx is not start", UiViewIdConst.RESPONSE_RESULT_WX_START);
        }
    }

    private void back(Object remotePeer) throws RemoteException, JSONException {
        ComponentName componentName = mAm.getTasks(1, 0,null).get(0).topActivity;
        //主页面
        if (componentName.getClassName().equals("com.tencent.mm.ui.LauncherUI")) {
            responseWxResult(remotePeer, 0, "next entry search", UiViewIdConst.RESPONSE_RESULT_WX_DONE);
        } else {
            if (componentName.getPackageName().equals("com.tencent.mm")) {
                run("input  keyevent 4");
//                inputManager.injectInputEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK), INJECT_INPUT_EVENT_MODE_ASYNC);
//                inputManager.injectInputEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK), INJECT_INPUT_EVENT_MODE_ASYNC);
//                //mUiAutomation.getRootInActiveWindow().performAction(AccessibilityService.GLOBAL_ACTION_BACK);
                responseWxResult(remotePeer, 0, "to be continue", UiViewIdConst.RESPONSE_RESULT_WX_BACK);
            } else {
                responseWxResult(remotePeer, 0, "wx is not start", UiViewIdConst.RESPONSE_RESULT_WX_START);
            }
        }
    }

    private void pasteText(AccessibilityNodeInfo info, String text) throws RemoteException {
        IClipboard clipboardManager;
        IBinder b = ServiceManager.getService("clipboard");
        clipboardManager = IClipboard.Stub.asInterface(b);

        ClipData clip = ClipData.newPlainText("text", text);
        //clipboard.setPrimaryClip(clip);
        clipboardManager.setPrimaryClip(clip, text);
        //焦点（n是AccessibilityNodeInfo对象）
        info.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        ////粘贴进入内容
        info.performAction(AccessibilityNodeInfo.ACTION_PASTE);
    }

    private AccessibilityNodeInfo getSearchBtn() {
        if (mUiAutomation.getRootInActiveWindow() == null) {
            mUiAutomation.disconnect();
            mUiAutomation.connect();
        }
        mUiAutomation.getRootInActiveWindow().refresh();
        AccessibilityNodeInfo node, root = mUiAutomation.getRootInActiveWindow();
        List<AccessibilityNodeInfo> ns = root.findAccessibilityNodeInfosByViewId(UiViewIdConst.HOME_SEARCH_BTN_ID);
        try {
            node = ns.get(0);
            if (node.getClassName().equals("android.widget.ImageView")) {
//                node.recycle();
                return node;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private AccessibilityNodeInfo getFirstMainBtn() {
        if (mUiAutomation.getRootInActiveWindow() == null) {
            mUiAutomation.disconnect();
            mUiAutomation.connect();
        }
        mUiAutomation.getRootInActiveWindow().refresh();
        AccessibilityNodeInfo node, root = mUiAutomation.getRootInActiveWindow();
        List<AccessibilityNodeInfo> ns = root.findAccessibilityNodeInfosByViewId(UiViewIdConst.HOME_FIRST_BTN_ID);
        try {
            node = ns.get(0);
            if (node.getClassName().equals("android.widget.ImageView")) {
//                node.recycle();
                return node;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private AccessibilityNodeInfo getSearchInput() {
        if (mUiAutomation.getRootInActiveWindow() == null) {
            mUiAutomation.disconnect();
            mUiAutomation.connect();
        }
        mUiAutomation.getRootInActiveWindow().refresh();
        AccessibilityNodeInfo node, root = mUiAutomation.getRootInActiveWindow();
        List<AccessibilityNodeInfo> ns = root.findAccessibilityNodeInfosByViewId(UiViewIdConst.SEARCH_TOP_EDIT_INPUT_ID);
        try {
            node = ns.get(0);
            if (node.getClassName().equals("android.widget.EditText")) {
//                node.recycle();
                return node;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private AccessibilityNodeInfo getNotFoundTextView() {
        if (mUiAutomation.getRootInActiveWindow() == null) {
            mUiAutomation.disconnect();
            mUiAutomation.connect();
        }
        mUiAutomation.getRootInActiveWindow().refresh();
        AccessibilityNodeInfo node, root = mUiAutomation.getRootInActiveWindow();
        List<AccessibilityNodeInfo> ns = root.findAccessibilityNodeInfosByViewId(UiViewIdConst.SEARCH_NOT_FOUND_TEXTVIEW_ID);
        try {
            node = ns.get(0);
            if (node.getClassName().equals("android.widget.TextView")) {
//                node.recycle();
                return node;
            }
        } catch (Exception ignored) {
        }
        return null;
    }


    private AccessibilityNodeInfo getSearchResultBtn() {
        if (mUiAutomation.getRootInActiveWindow() == null) {
            mUiAutomation.disconnect();
            mUiAutomation.connect();
        }
        mUiAutomation.getRootInActiveWindow().refresh();
        AccessibilityNodeInfo node, root = mUiAutomation.getRootInActiveWindow();
        List<AccessibilityNodeInfo> ns = root.findAccessibilityNodeInfosByViewId(UiViewIdConst.SEARCH_RESULT_BTN_ID);
        try {
            node = ns.get(0);
            if (node.getClassName().equals("android.widget.TextView")) {
//                node.recycle();
                return node;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private AccessibilityNodeInfo getAddContactBtn() {
        if (mUiAutomation.getRootInActiveWindow() == null) {
            mUiAutomation.disconnect();
            mUiAutomation.connect();
        }
        mUiAutomation.getRootInActiveWindow().refresh();
        AccessibilityNodeInfo node, root = mUiAutomation.getRootInActiveWindow();
        List<AccessibilityNodeInfo> ns = root.findAccessibilityNodeInfosByViewId(UiViewIdConst.SEARCH_CONTACTINFOUI_BTN_ID);
        try {
            node = ns.get(0);
            if (node.getClassName().equals("android.widget.Button")) {
//                node.recycle();
                return node;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private AccessibilityNodeInfo getPermissSendBtn() {
        if (mUiAutomation.getRootInActiveWindow() == null) {
            mUiAutomation.disconnect();
            mUiAutomation.connect();
        }
        mUiAutomation.getRootInActiveWindow().refresh();
        AccessibilityNodeInfo node, root = mUiAutomation.getRootInActiveWindow();
        List<AccessibilityNodeInfo> ns = root.findAccessibilityNodeInfosByViewId(UiViewIdConst.SAY_HI_WITH_SNS_PERMISSIONUI_BTN_ID);
        try {
            node = ns.get(0);
            if (node.getClassName().equals("android.widget.TextView")) {
//                node.recycle();
                return node;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void responseWxResult(Object remotePeer, int code, String message, String next) throws JSONException {
        JSONObject cmd = new JSONObject();
        cmd.put("code", code);
        cmd.put("message", message);
        cmd.put("next", next);
        response(remotePeer, cmd);
    }

    private void injectJS(Object remotePeer, String text) {
        JSONObject cmd = new JSONObject();


        //获取当前 ACTIVITY


        response(remotePeer, cmd);
    }

    private void test(Object remotePeer) throws JSONException, RemoteException {
        mAm = ActivityManagerNative.getDefault();
        if (mAm == null) {
            Log.d("Garri", "null");
        } else {
            Log.d("Garri", "ok");
        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("code", 0);
        jsonObject.put("message", "进程已启动.");
        response(remotePeer, jsonObject);

//        final long uptimeMillis = SystemClock.uptimeMillis();
//        int inputSource = 257;
//        int code;
//        boolean b = false;
//        KeyEvent event = new KeyEvent(uptimeMillis, uptimeMillis, 0, KeyEvent.KEYCODE_BACK, 0, (b ? 1 : 0), -1, 0, 0, inputSource);
//        inputManager.injectInputEvent(event, INJECT_INPUT_EVENT_MODE_ASYNC);
//        inputManager.injectInputEvent(event, INJECT_INPUT_EVENT_MODE_ASYNC);

//        AccessibilityNodeInfo node = getSearchResultBtn();
//        if (node == null) {
//            System.out.println("not found");
//        } else {
//            System.out.println("target found");
//        }
//        AccessibilityNodeInfo node, root = mUiAutomation.getRootInActiveWindow();
//        Rect rect = root.getBoundsInScreen();
//        JSONObject cmd = new JSONObject();
//        if (root.isDismissable()) {
//            cmd.put("result", "Dismiss");
//            root.performAction(AccessibilityService.GLOBAL_ACTION_BACK);
//        } else {
//
//            cmd.put("result", rect.top + "/" + rect.left + "/" + rect.bottom + "/" + rect.right);
//        }

//        response(remotePeer, cmd);
//        List<AccessibilityNodeInfo> ns = root.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/ji");
//        pasteText(ns.get(0), "zsh is a dd");
//        JSONObject cmd = new JSONObject();
//        AccessibilityNodeInfo node, root = mUiAutomation.getRootInActiveWindow();
//        List<AccessibilityNodeInfo> ns = root.findAccessibilityNodeInfosByViewId(UiViewIdConst.HOME_TOP_BAR_ID);
//        node = ns.get(0).getChild(1).getChild(0);
//        if (node.getContentDescription().equals("搜索")) {
//            cmd.put("code", 0);
//            CharSequence desc = node.getContentDescription();
//            cmd.put("data", desc);
//        } else {
//            cmd.put("code", 1);
//            CharSequence desc = node.getContentDescription();
//            cmd.put("data", desc);
//        }
//        response(remotePeer, cmd);
    }

    private static void showUsage() {
        System.err.println(
                "usage: zshui port tcp/http\n" +
                        "\n"
        );
    }

    public String getTopPackageName() {
        // String t = run("dumpsys window windows | grep 'mCurrentFocus'");
        // String activityArr[] = t.split("/");
        // String packageArr[] = activityArr[0].split(" ");
        // String packageName = packageArr[packageArr.length - 1];
        // String activityName = activityArr[1].replace("{","");
        // return packageName;
        if (mAm == null) {
            lastErr = "ActivityManagerNative.getDefault() return null";
            Log.e("Garri", lastErr);
            return "";
        }
        try {
            ComponentName componentName = mAm.getTasks(1, 0,null).get(0).topActivity;
            return componentName.getPackageName();
        } catch (RemoteException e) {
            e.printStackTrace();
            lastErr = e.getMessage();
            Log.e("Garri", lastErr);
            return "";
        }
    }

    public String getTopActivityName() {
        // String t = run("dumpsys window windows | grep 'mCurrentFocus'");
        // String activityArr[] = t.split("/");
        // String packageArr[] = activityArr[0].split(" ");
        // String packageName = packageArr[packageArr.length - 1];
        // String activityName = activityArr[1].replace("{","");
        // return activityName;

        if (mAm == null) {
            lastErr = "ActivityManagerNative.getDefault() return null";
            Log.e("Garri", lastErr);
            return "";
        }
        try {
            ComponentName componentName;
             componentName = mAm.getTasks(1,0,null).get(0).topActivity;
            // if (Build.VERSION.SDK_INT > 20) {
            //     componentName = mAm.getRunningAppProcesses().get(0).topActivity;
            // } else {
            //     componentName = mAm.getRunningTasks(1).get(0).topActivity;
            // }
            // ComponentName componentName = mAm.getTasks(1, 0,null).get(0).topActivity;
            return componentName.getShortClassName();
        } catch (RemoteException e) {
            e.printStackTrace();
            lastErr = e.getMessage();
            Log.e("Garri", lastErr);
            return "";
        }
    }

    static String run(String cmd) {
        Process process;
        try {
            process = Runtime.getRuntime().exec("sh");
            DataOutputStream dataOutputStream = new DataOutputStream(process.getOutputStream());
            DataInputStream dataInputStream = new DataInputStream(process.getInputStream());
            dataOutputStream.writeBytes(cmd + "\n");
            dataOutputStream.writeBytes("exit\n");
            dataOutputStream.flush();
            InputStreamReader inputStreamReader = new InputStreamReader(
                    dataInputStream, "UTF-8");
            BufferedReader bufferedReader = new BufferedReader(
                    inputStreamReader);
            final StringBuilder out = new StringBuilder();
            final int bufferSize = 1024;
            final char[] buffer = new char[bufferSize];
            for (; ; ) {
                int rsz = inputStreamReader.read(buffer, 0, buffer.length);
                if (rsz < 0)
                    break;
                out.append(buffer, 0, rsz);
            }

            bufferedReader.close();
            inputStreamReader.close();
            process.waitFor();
            return out.toString();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return "";
    }
}

