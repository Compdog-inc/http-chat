package com.compdog.httpchat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Server {

    private final int port;
    private ServerSocket server;

    private Thread acceptThread;

    private final List<Client> clients = new ArrayList<>();

    public Server(int port) {
        this.port = port;
        try {
            this.server = new ServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        acceptThread = new Thread(this::acceptThread);
        acceptThread.start();
    }

    public void stop() {
        try {
            acceptThread.join(100);
        } catch (InterruptedException ignored) {
        }

        for (var client : clients) {
            client.close();
        }

        clients.clear();
    }

    public void broadcast(String user, String message){
        for (var client : clients) {
            client.sendMessage(user, message);
        }
    }

    public int getPort() {
        return port;
    }

    private void acceptThread() {
        while (true) {
            try {
                Socket client = server.accept();
                clients.add(new Client(this, client));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
