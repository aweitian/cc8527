import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class Zshui {
	private ServerSocket socket;
    private boolean isEnable;
    private final ExecutorService threadPool;

    public static void main(String[] args) {
        try {
            if (args.length < 1) {
            	System.out.println("java Zshui port");
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
        threadPool = Executors.newCachedThreadPool();
        this.startServerAsync(port);
    }

    private void startServerAsync(final int port) {
        isEnable = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                doProcSync(port);
            }
        }).start();
    }

    /**
     * 关闭server
     */
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
                threadPool.submit(new Runnable() {
                    @Override
                    public void run() {
                        System.out.println("remotePeer..............." + remotePeer.getRemoteSocketAddress().toString());
                        onAcceptRemotePeer(remotePeer);
                    }
                });
            }
        } catch (IOException e) {
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
                    System.out.println("seperator read length " + i);
                    baos.write(buffer, 0, i);
                    readed = readed - i;
                }
                
                strInputstream = new String(baos.toByteArray());
                byte response[] = strInputstream.getBytes();
                System.out.println(strInputstream);
                remotePeer.getOutputStream().write(response, 0, response.length);
                System.out.println("response length " + response.length);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}