package SocketServer;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.Vector;

public class Server {
    int port;
    ServerSocket serverSocket;
    Socket socket;
    Vector<ServerThread> serverThreads;
    ServerThread serverThread;

    public Server(int port){
        this.port = port;

        try{
            makeServer(port);
            serverThreads = new Vector<>();
            while(true){
                socket = catchSocket();
                serverThread = new ServerThread(this);
                serverThreads.add(serverThread);
                serverThread.start();
            }
        }catch (IOException ioException){
            ioException.printStackTrace();
        }
    }
    public void makeServer(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("서버 생성 완료");
    }
    public Socket catchSocket() throws IOException{
        System.out.println("소켓 연결 대기중");
        socket = serverSocket.accept();

        return socket;
    }

    public static void main(String[] args) throws IOException {
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .build();
        FirebaseApp.initializeApp(options);

        new Server(33333);
    }
}