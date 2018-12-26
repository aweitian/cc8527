//package zsh.jl.zshui;
package zsh.jl.wxautofriend;


import android.app.ActivityManagerNative;
import android.app.IActivityManager;
//import android.app.SystemServiceRegistry;
import android.app.UiAutomation;
import android.hardware.input.InputManager;
import android.inputmethodservice.InputMethodService;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.view.inputmethod.InputMethod;
import android.view.inputmethod.InputMethodManager;

import com.android.internal.view.IInputMethodManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;


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

    public static void main(String[] args) {
        try {
            if (args.length < 1) {
                showUsage();
                return;
            }
            new Zshui(Integer.parseInt(args[0]));
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private Zshui(int port) {
        mAm = ActivityManagerNative.getDefault();
//        InputManager inputManager = InputManager.getInstance();

//        InputMethodService.

//        IBinder binder = ServiceManager.getService("input_method");
//        System.out.println(binder);
//        IInputMethodManager inputMethodManager = IInputMethodManager.Stub.asInterface(binder);

//        if (inputMethodService == null)
//        {
//            System.out.println("null");
//        }
//        else
//        {
//            System.out.println("not null");
//        }
        this.startServerAsync(port);
//        try {
//
//        } catch (IOException e) {
//            e.printStackTrace();
//            Log.e("Garri", e.getMessage());
//            return;
//        }
    }

    private void startServerAsync(final int port) {
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
                        onAcceptRemotePeer(remotePeer);
                    }
                }).start();
            }
        } catch (IOException e) {
            Log.e("Garri", e.getMessage());
            e.printStackTrace();
        }
    }

    private void onAcceptRemotePeer(Socket remotePeer) {
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
    private void procedureCmd(Socket remotePeer, String cmd) {

        try {
            JSONObject j_cmd = new JSONObject(cmd);
            String action = j_cmd.getString("action");
            switch (action) {
                case "exit":
                    this.closeClient(remotePeer);
                    break;
                case "top":
                    this.topActivity(remotePeer);
                    break;
                case "input":
                    this.input(remotePeer, j_cmd.getString("data"));
                    break;
                case "inputIME":
                    this.inputIME(remotePeer, j_cmd.getString("data"));
                    break;
                case "test":
                    this.test();
                    break;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void closeClient(Socket remotePeer) {
        JSONObject cmd = new JSONObject();
        try {
            cmd.put("code", 0);
            cmd.put("message", "OK");

            byte response[] = cmd.toString().getBytes();
            remotePeer.getOutputStream().write(response, 0, response.length);
            remotePeer.close();
            System.out.println("closed by cmd");
//            Thread.currentThread().interrupt();
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        }
    }

    private void topActivity(Socket remotePeer) {
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
            byte response[] = cmd.toString().getBytes();
            remotePeer.getOutputStream().write(response, 0, response.length);
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        }
    }

    private void input(Socket remotePeer, String text) {
        JSONObject cmd = new JSONObject();
        try {
            run("input text \"" + text + "\"");
            cmd.put("code", 0);
            cmd.put("message", "OK");
            byte response[] = cmd.toString().getBytes();
            remotePeer.getOutputStream().write(response, 0, response.length);
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        }
    }

    private void inputIME(Socket remotePeer, String text) {
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
            byte response[] = cmd.toString().getBytes();
            remotePeer.getOutputStream().write(response, 0, response.length);
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }

    private void test()
    {
        UnixSocketFactory socketFactory = new UnixSocketFactory();
        OkHttpClient client = new OkHttpClient.Builder()
                .socketFactory(socketFactory)
                .dns(socketFactory)
                .build();
        HttpUrl url = socketFactory.urlForUnixSocketPath("127.0.0.1", "json");
        Request request = new Request.Builder()
                .url(url)
                .build();
        Response response = null;
        try {
            response = client
                    .newCall(request)
                    .execute();
            ResponseBody body = response.body();
            System.out.println(body.string());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void showUsage() {
        System.err.println(
                "usage: zshui port\n" +
                        "\n"
        );
    }

    public String getTopActivityName() {
        if (mAm == null) {
            lastErr = "ActivityManagerNative.getDefault() return null";
            Log.e("Garri", lastErr);
            return "";
        }
        try {
            return mAm.getTasks(1, 0).get(0).topActivity.getClassName();
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
