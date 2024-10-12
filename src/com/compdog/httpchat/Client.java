package com.compdog.httpchat;

import java.io.*;
import java.net.Socket;

public class Client {

    private final Server server;
    private final Thread socketThread;
    private final Socket socket;
    private String name;

    private BufferedReader reader;
    private BufferedWriter writer;

    public Client(Server server, Socket socket){
        this.server = server;
        this.socket = socket;

        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        } catch (IOException e) {
            e.printStackTrace();
        }

        this.socketThread = new Thread(this::socketThread);
        this.socketThread.start();
    }

    public void close(){
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static final String INTRODUCTION_HTML = """
            <!DOCTYPE html>
            <html>
                <head>
                    <title>HTTP Chat</title>
                </head>
                <body>
                    <div style="margin:auto;width:fit-content;">
                        <h1>Welcome to HTTP Chat!</h1>
                        <h3>Choose a name</h3>
                        <form onsubmit="event.preventDefault();">
                            <input type="text" id="usr" name="user" placeholder="Your name"/>
                            <input onclick="window.location.href = '/name/'+usr.value;" type="submit" value="Join" />
                        </form>
                    </div>
                </body>
            </html>
            """;

    private static final String CHAT_HTML = """
            <!DOCTYPE html>
            <html>
                <head>
                    <title>HTTP Chat</title>
                    <style>
                        html,body{
                            margin:0;
                            padding:0;
                            width:100%;
                            height:100%;
                        }
                        
                        .chat {
                            padding:10px;
                            width:80%;
                            display:flex;
                            flex-direction:column;
                        }
                        
                        .bubble {
                            padding:4px;
                            border:1px solid #ddd;
                            margin:2px;
                        }
                        
                        .user {
                            font-weight:700;
                            font-size:18px;
                        }
                        
                        .banner {
                            background-color:#fff;
                            position:sticky;
                            top: 0px;
                            display:flex;
                            flex-direction:column;
                        }
                    </style>
                    <script>
                        function sendMessage(){
                            const text = document.getElementById("mchat").value;
                            document.getElementById("mchat").value = "";
                            fetch('/chat',{
                                method:'POST',
                                body:text
                            });
                        }
                        
                        function textdown(event){
                            if (!event.shiftKey && event.key == "Enter") {
                                event.preventDefault();
                                sendMessage();
                            }
                        }
                    </script>
                </head>
                <body>
                    <div style="margin:auto;width:80%;">
                        <div class="banner">
                            <h1>HTTP Chat: %name%</h1>
                            <div>
                                <button onclick="window.location.href = '/';">Leave</button>
                                <button onclick="sendMessage();">Send</button>
                            </div>
                            <textarea onkeydown="textdown(event)" id="mchat" name="mchat" rows="10" cols="50" placeholder="Type your message..."></textarea>
                        </div>
                        <div id="cht" class="chat">
                            <script>
                                function scrollDown(){
                                    window.scrollTo(0, document.body.scrollHeight);
                                }
                                
                                const targetElement = document.getElementById('cht');
                    
                                const observer = new MutationObserver(mutations => {
                                  mutations.forEach(mutation => {
                                    if (mutation.type === 'childList') {
                                        if(document.body.scrollHeight-window.innerHeight === 0 || window.scrollY/(document.body.scrollHeight-window.innerHeight) > 0.9){
                                        scrollDown();
                                      }
                                    }
                                  });
                                });
                    
                                const config = { childList: true };
                    
                                observer.observe(targetElement, config);
                            </script>
            """;

    private void sendIntroduction(){
        try {
            writer.write("HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/html; charset=utf-8\r\n" +
                    "\r\n" +
                    INTRODUCTION_HTML
                            .replaceAll("\r\n","\n")
                            .replaceAll("\n","\r\n"));
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean handleRequest() {
        String[] reqParts = null;
        try {
            reqParts = reader.readLine().split(" ");
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        if (reqParts.length < 2)
            return false;

        String method = reqParts[0];
        String path = reqParts[1];
        String line;

        String referer = "";
        int contentLength = 0;

        do {
            try {
                line = reader.readLine();
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
            String[] parts = line.split(" ");
            String header = parts[0];
            if(parts.length > 1) {
                String value = parts[1];
                if (header.equalsIgnoreCase("Content-Length:")) {
                    contentLength = Integer.parseInt(value);
                } else if (header.equalsIgnoreCase("Referer:")) {
                    referer = value;
                }
            }
        } while (!line.isEmpty());

        if(method.equalsIgnoreCase("POST") && (path.equalsIgnoreCase("/chat") || path.equalsIgnoreCase("/chat/"))){
            try {
                char[] buf = new char[contentLength];
                int r = reader.read(buf, 0, contentLength);
                String message = new String(buf, 0, r);
                var refParts = referer.split("/");
                String user = refParts[refParts.length - 1];

                try {
                    writer.write("HTTP/1.1 200 OK\r\n\r\n");
                    writer.flush();
                    writer.close();
                } catch (IOException ignored) {
                }

                server.broadcast(user, message);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return false;
        }

        if(!path.startsWith("/name/")){
            sendIntroduction();
            return false;
        }

        this.name = path.substring("/name/".length());
        System.out.println("Client with name " + this.name + " connected.");

        try {
            writer.write("HTTP/1.1 200 OK\r\n" +
                    "Connection: Keep-Alive\r\n" +
                    "Content-Type: text/html; charset=utf-8\r\n" +
                    "Keep-Alive: timeout=120, max=1000\r\n" +
                    "\r\n" +
                    CHAT_HTML.replaceAll("%name%", this.name)
                            .replaceAll("\r\n","\n")
                            .replaceAll("\n","\r\n"));
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public boolean sendMessage(String user, String message){
        try {
            writer.write("<div class=\"bubble\"><div class=\"user\">" + user + "</div><pre>" + message +  "</pre></div>");
            writer.flush();
        } catch (IOException ignored) {
            return false;
        }

        return true;
    }

    private void socketThread(){
        if(!handleRequest()){
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }

        while(socket.isConnected() && !socket.isClosed()){
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                break;
            }


        }
    }
}
