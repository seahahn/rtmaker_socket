package SocketServer;

import com.google.firebase.messaging.*;
import com.google.gson.Gson;
import org.json.simple.JSONObject;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.*;

import static java.lang.Integer.parseInt;
import static java.lang.Integer.valueOf;

public class ServerThread extends Thread{
    ServerSocket serverSocket;
    Socket socket;
    DataInputStream inputStream;
    DataOutputStream outputStream;
    Vector<ServerThread> serverThreads;

    Gson gson = new Gson();
    JSONObject json;
    String userData; // 사용자 데이터
    String chatRoomId; // 사용자가 접속한 채팅방 고유 번호
    Integer userId; // 사용자 고유 번호
    String nick; // 사용자 닉네임
//    Integer hostId; // 채팅방 개설자 고유 번호
//    Integer audienceId; // 채팅방이 속한 그룹 고유 번호 혹은 1:1채팅 상대방 고유 번호

//    HashMap<Integer, String> notiUsers; // 사용자가 접속한 채팅방에 들어와 있는 사용자들을 고유 번호, 토큰값 쌍으로 저장한 것
    List<String> notiUsers; // 현재 포그라운드 상태로 채팅방에 들어와 있는 사용자 목록
    List<Integer> inUsers; // 현재 포그라운드 상태로 채팅방에 들어와 있는 사용자 목록

    Connection conn = null;
    Statement stmt;
    String query;
    ResultSet rs;

    Integer id;
    String token;

    String dbpw = "0121"; // aws

    public ServerThread(Server server){
        this.serverSocket = server.serverSocket;
        socket = server.socket;
        serverThreads = server.serverThreads;
        try{
            inputStream = connectInputStream();
            outputStream = conncetOutputStream();

            this.userData = receiveUserData();
            // String to Json
            json = gson.fromJson(this.userData, JSONObject.class);

            this.chatRoomId = json.get("chatRoomId").toString();
            System.out.println("userId : "+json.get("userId"));
            this.userId = Integer.parseInt(String.valueOf(Math.round((double) json.get("userId"))));
            this.nick = json.get("nick").toString();
            this.notiUsers = new ArrayList<>();
            this.inUsers = new ArrayList<>();
            System.out.println("사용자 정보 : "+this.chatRoomId+" "+this.nick);
//            broadCast(msg);
        }catch (Exception e){
            e.printStackTrace();
        }

        System.out.println("사용자 입장");
    }
    public DataInputStream connectInputStream() throws IOException {
        inputStream = new DataInputStream(socket.getInputStream());

        return inputStream;
    }
    public DataOutputStream conncetOutputStream() throws IOException {
        outputStream = new DataOutputStream(socket.getOutputStream());

        return outputStream;
    }
    public String receiveUserData() throws IOException {
        String data = inputStream.readUTF();

        return data;
    }
    public void sendMessageToClient(String msg) throws IOException {
        outputStream.writeUTF(msg);
    }
    public String receiveMessageFromClient() throws IOException {
        String msg = inputStream.readUTF();
        return msg;
    }
    public void broadCast(String msg) throws IOException, FirebaseMessagingException, SQLException {
        for(ServerThread serverThread : serverThreads){
            if(serverThread.chatRoomId.equals(this.chatRoomId)) {
                serverThread.sendMessageToClient(msg);
                this.inUsers.add(serverThread.userId);
                System.out.println("serverThread.id : "+serverThread.userId);
                System.out.println("serverThread.id : "+this.inUsers);
            }
        }

        connDB(); // DB 연결
        stmt = conn.createStatement(); // 채팅방 참여자 목록 가져올 쿼리문 객체 만들기
        query = "SELECT * FROM chat_user WHERE room_id="+this.chatRoomId+" AND is_in=1";
        query(stmt, query); // 쿼리 결과 가져오기
        while(rs.next()) {
            id = rs.getInt("user_id");
            token = rs.getString("token");

            // 사용자 본인 및 현재 채팅방 접속 중인 사용자를 제외한 나머지만 채팅 알림 보냄
            if(!id.equals(this.userId) && !this.inUsers.contains(id)) {
                this.notiUsers.add(token);
                System.out.println("token : "+token);
            }
        }

        if(this.notiUsers.size() > 0) {
            MulticastMessage message = MulticastMessage.builder()
                    .putData("type", "0") // 채팅은 알림 구분 0번으로 지정해둠
                    .putData("title", this.nick)
                    .putData("body", msg)
                    .addAllTokens(this.notiUsers)
                    .build();
            BatchResponse response = FirebaseMessaging.getInstance().sendMulticast(message);

            // See the BatchResponse reference documentation
            // for the contents of response.
            System.out.println(response.getSuccessCount() + " messages were sent successfully");
        }
        this.inUsers.clear();
        this.notiUsers.clear();
    }

    public void connDB() throws SQLException {
        conn = DriverManager.getConnection(
                "jdbc:mariadb://localhost:3306/rtmaker",
                "root",
                dbpw
        );
//        return conn;
    }

    public void query(Statement stmt, String query) throws SQLException {
        rs = stmt.executeQuery(query);
//        return rs;
    }

    @Override
    public void run() {
        try{
            while (true) {
                String msg = receiveMessageFromClient();
                broadCast(msg);
            }
        } catch (EOFException e){
            System.out.println("EOFException occured");
//            e.printStackTrace();
        } catch (Exception e){
            e.printStackTrace();
        } finally {
            serverThreads.remove(this);
            try{
                socket.close();
            }catch (IOException ioException){
                ioException.printStackTrace();
            }
        }
    }
}
