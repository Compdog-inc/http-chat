package com.compdog.httpchat;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class Server {

    private final int port;
    private ServerSocket server;

    private Thread acceptThread;

    private final List<Message> history = new ArrayList<>();

    private final List<Client> clients = new ArrayList<>();
    private final Map<InetAddress, ClientSettings> clientSettings = new HashMap<>();

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

    public Predicate<Client> clientFilter(String name) {
        return client -> {
            if (client.getName() == null)
                return false;
            return client.getName().equals(name);
        };
    }

    public Predicate<Client> notClientFilter(String name) {
        return client -> {
            if (client.getName() == null)
                return true;
            return !client.getName().equals(name);
        };
    }

    public Stream<Client> getClients(String name) {
        return clients.stream().filter(clientFilter(name));
    }

    public Client getClientWithAddress(InetAddress address) {
        Object[] arr = clients.stream().filter(client -> client.getAddress().equals(address) && client.getName() != null).toArray();
        return arr.length == 0 ? null : (Client) arr[arr.length - 1];
    }

    public void broadcast(Client user, String message) {
        broadcast(user, message, client -> true);
    }

    public void broadcast(Client user, String message, Predicate<Client> filter) {
        ClientSettings settings = getSettings(user.getAddress());

        Message msg = new Message(user, settings, message);
        history.add(msg);

        clients.stream().filter(filter).forEach(client -> {
            if (!user.isMuted() || client.isMuted()) {
                client.sendMessage(user.getName(), settings, message);
            }
        });
    }

    public ClientSettings getSettings(InetAddress address){
        if(clientSettings.containsKey(address)){
            return clientSettings.get(address);
        }

        ClientSettings settings = new ClientSettings();
        clientSettings.put(address, settings);
        return settings;
    }

    public List<Message> getHistory() {
        return history;
    }

    public int getPort() {
        return port;
    }

    private void acceptThread() {
        while (true) {
            try {
                Socket client = server.accept();
                clients.add(new Client(this, client, (c)->{
                    clients.remove(c);
                    return true;
                }));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
