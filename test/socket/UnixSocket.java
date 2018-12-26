package zsh.jl.wxautofriend;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;

import javax.net.ServerSocketFactory;


/**
 * Impersonate TCP-style ServerSocketFactory over UNIX domain sockets.
 */
class UnixSocket extends Socket {
    private String path = "miui_webview_devtools_remote_26683";

    private LocalSocket localSocket;

    @Override
    public void connect(SocketAddress endpoint, int timeout) throws IOException {
        localSocket = new LocalSocket();
        //endpoint.toString()
        System.out.println(path);
        localSocket.connect(new LocalSocketAddress(path, LocalSocketAddress.Namespace.ABSTRACT));
    }

    @Override
    public void bind(SocketAddress bindpoint) throws IOException {
        //socket.bind(bindpoint);
    }

    @Override
    public boolean isConnected() {
        return localSocket.isConnected();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return localSocket.getOutputStream();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return localSocket.getInputStream();
    }
}
