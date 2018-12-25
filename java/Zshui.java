//package zsh.jl.zshui;
package zsh.jl.wxautofriend;


import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.app.UiAutomation;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;


public class Zshui {
    private static final String HANDLER_THREAD_NAME = "UiAutomatorHandlerThread";
    private final HandlerThread mHandlerThread = new HandlerThread(HANDLER_THREAD_NAME);
    private UiAutomation mUiAutomation;
    private IActivityManager mAm;
    private ServerSocket socket;
    private boolean isEnable;

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
            while (true) {
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
            JSONObject data = j_cmd.getJSONObject("data");
            switch (action) {
                case "exit":
                    this.closeClient(remotePeer);
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
            remotePeer.close();
            byte response[] = cmd.toString().getBytes();
            remotePeer.getOutputStream().write(response, 0, response.length);
        } catch (JSONException | IOException e) {
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
            Log.e("Garri", "ActivityManagerNative.getDefault() return null");
            return "";
        }
        try {
            return mAm.getTasks(1, 0).get(0).topActivity.getClassName();
        } catch (RemoteException e) {
            e.printStackTrace();
            Log.e("Garri", e.getMessage());
            return "";
        }
    }
}
