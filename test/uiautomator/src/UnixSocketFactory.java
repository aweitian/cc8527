package zsh.jl.wxautofriend;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;

import javax.net.SocketFactory;

import okhttp3.Dns;
import okhttp3.HttpUrl;

public class UnixSocketFactory extends SocketFactory implements Dns {
    private String path;

    public UnixSocketFactory(String path) {
        this.path = path;
    }

//    public HttpUrl urlForUnixSocketPath(String unixSocketPath, String path) {
//        return new HttpUrl.Builder()
//                .scheme("http")
//                .host(unixSocketPath)
//                .addPathSegment(path)
//                .build();
//    }

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        return Collections.singletonList(
                InetAddress.getByAddress(hostname, new byte[]{0, 0, 0, 0}));
    }

    @Override
    public Socket createSocket() {
        return new UnixSocket(path);
    }

    @Override
    public Socket createSocket(String s, int i) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Socket createSocket(String s, int i, InetAddress inetAddress, int i1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Socket createSocket(InetAddress inetAddress, int i) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Socket createSocket(InetAddress inetAddress, int i, InetAddress inetAddress1, int i1) {
        throw new UnsupportedOperationException();
    }
}
