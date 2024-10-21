package com.compdog.httpchat;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Predicate;

public class Client {

    private static final Random random = new Random();

    private final Server server;
    private final Thread socketThread;
    private final Socket socket;
    private String name;

    private BufferedReader reader;
    private BufferedWriter writer;

    private final Function<Client, Boolean> onClose;

    public Client(Server server, Socket socket, Function<Client, Boolean> onClose) {
        this.server = server;
        this.socket = socket;
        this.onClose = onClose;

        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        } catch (IOException e) {
            e.printStackTrace();
        }

        this.socketThread = new Thread(this::socketThread);
        this.socketThread.start();
    }

    public void close() {
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
                        
                        pre {
                            font-family: inherit;
                            margin:0;
                            display:inline-block;
                        }
                        
                        button {
                            border: 2px solid #fff;
                            color: #fff;
                            font-family: Helvetica;
                            font-weight: 600;
                            font-size: 15px;
                            padding: 8px 17px;
                            border-radius: 10px;
                            background-color:#1954bf;
                            background: linear-gradient(48deg, #1954bf, #5ed8de);
                            box-shadow: 0 3px 9px 0 rgb(0 0 0 / 37%);
                            outline: none;
                        }
                        
                        button.danger {
                            background-color:#af2c2c;
                            background: linear-gradient(48deg, #af2c2c, #de9f5e);
                        }
                        
                        .header {
                            font-family: Helvetica;
                        }
                        
                        .header>span{
                            background-image: linear-gradient(333deg, #43e4b6 5%, #0b92cb 90%);
                            -webkit-background-clip: text;
                            background-clip: text;
                            -webkit-text-fill-color: transparent;
                            color:transparent;
                            font-size: 48px;
                        }

                        .navbar {
                            display: flex;
                            flex-direction: row;
                            gap: 25px;
                            margin: 20px 0px;
                        }
                        
                        .inmsg {
                            border-radius: 15px;
                            margin: 0 0px;
                            box-shadow: 0 8px 20px 0px rgb(0 0 0 / 19%);
                            border: 3px solid #fff;
                            color: #fff;
                            font-size: 16px;
                            font-family: Helvetica;
                            padding: 15px 20px;
                            background-color:#47bca6;
                            background: linear-gradient(30deg, #47bca6, #93dca6);
                            outline:none;
                        }
                        
                        .inmsg::placeholder {
                            color:#fff;
                            opacity:.65;
                        }
            
                        .chat {
                            padding:10px;
                            display:flex;
                            flex-direction:column;
                            gap: 10px;
                            align-items: flex-start;
                        }
            
                        .bubble {
                            padding: 10px 100px 10px 20px;
                            border: 3px solid #ffede0;
                            font-family: Helvetica;
                            margin:2px;
                            color:#fff;
                            border-radius:15px;
                            box-shadow:0px 6px 15px 0 rgb(0 0 0 / 24%);
                            background-color:#2264d6;
                            background:linear-gradient(27deg, #2264d6, #5fdbff);
                        }
            
                        .user {
                            font-family: Helvetica;
                            font-weight:700;
                            font-size:18px;
                            display:flex;
                            flex-direction:row;
                            gap:10px;
                            align-items: center;
                        }
                        
                        .badge {
                            background-color: #7300b6;
                            background: linear-gradient(30deg, #1e3ea9, #6bb9e3);
                            outline: 2px solid #fff;
                            color: #fff;
                            font-family: Helvetica;
                            font-weight: 600;
                            font-size: 14px;
                            padding: 5px 10px;
                            border-radius: 999px;
                            box-shadow: 0 2px 9px 0 rgb(0 0 0 / 19%);
                        }
                        
                        .donate {
                            background: linear-gradient(30deg, #6b1ea9, #e36bab);
                            background-color:#e36bab;
                            outline: 2px solid #fff;
                        }
                        
                        .rich {
                            outline: 2px solid #ffb57a;
                            background: linear-gradient(30deg, #6b1ea9, #e38a6b);
                            background-color:#e38a6b;
                            box-shadow: 0 2px 9px 0 rgb(242 103 57);
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
                        
                        function watchdogReached(){
                            location.reload();
                        }
                        
                        let watchdogTimeout = 5000;
                        let watchdog = setTimeout(watchdogReached, watchdogTimeout);
                        
                        function watchdogReset(){
                            clearTimeout(watchdog);
                            watchdog = setTimeout(watchdogReached, watchdogTimeout);
                        }
                        
                        function cleanEval(){
                            document.querySelectorAll("script[eval]").forEach(v=>v.remove());
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
                    <div style="margin:auto;width:95%;">
                        <div class="banner">
                            <h1 class="header">Logged in as <span>%name%</span></h1>
                            <div class="navbar">
                                <button class="danger" onclick="window.location.href = '/';">Leave</button>
                                <button onclick="sendMessage();">Send</button>
                            </div>
                            <textarea class="inmsg" onkeydown="textdown(event)" id="mchat" name="mchat" rows="10" cols="50" placeholder="Type your message...&#10;&#10;Commands&#10;/badge [emoji or text]           -    Set or clear your user badge&#10;/tier [None|1|2]                 -    Upgrade your tier subscription&#10;/mute [name]                     -    Requires Tier 1 ($1 / month)&#10;/unmute [name]                   -    Requires Tier 1 ($1 / month)&#10;/redirect [name] [full url]      -    Requires Tier 2 ($5 / month)&#10;"></textarea>
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

    private void sendIntroduction() {
        try {
            writer.write("HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/html; charset=utf-8\r\n" +
                    "\r\n" +
                    INTRODUCTION_HTML
                            .replaceAll("\r\n", "\n")
                            .replaceAll("\n", "\r\n"));
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean handleRequest() {
        String[] reqParts = null;
        try {
            String ln = reader.readLine();
            if (ln != null) {
                reqParts = ln.split(" ");
            } else {
                return false;
            }
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
            if (parts.length > 1) {
                String value = parts[1];
                if (header.equalsIgnoreCase("Content-Length:")) {
                    contentLength = Integer.parseInt(value);
                } else if (header.equalsIgnoreCase("Referer:")) {
                    referer = value;
                }
            }
        } while (!line.isEmpty());

        if (method.equalsIgnoreCase("POST") && (path.equalsIgnoreCase("/chat") || path.equalsIgnoreCase("/chat/"))) {
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

                if (message.startsWith("/")) {
                    String[] parts = message.substring(1).split(" ");
                    if (parts.length == 3 && parts[0].equalsIgnoreCase("redirect") && server.getSettings(getAddress()).tier.atLeast(ClientSettings.ClientTier.Tier2)) {
                        String name = parts[1];
                        String url = parts[2];
                        System.out.println("Redirecting " + name + " to " + url);
                        server.getClients(name).forEach((client -> {
                            client.executeJavascript("location.href='" + url + "';");
                        }));
                    } else if (parts.length == 2 && parts[0].equalsIgnoreCase("mute") && server.getSettings(getAddress()).tier.atLeast(ClientSettings.ClientTier.Tier1)) {
                        String name = parts[1];
                        int time = random.nextInt(60);
                        System.out.println("Muting " + name + " for " + time + " seconds");
                        server.getClients(name).forEach((client -> {
                            server.getSettings(client.getAddress()).mute(System.currentTimeMillis() + (long) time * 1000);
                        }));

                        var br = server.getClientWithAddress(getAddress());
                        if(br != null) {
                            server.broadcast(br, message + "\nMuted for " + time + " seconds.", server.notClientFilter(name));
                        }
                        return false;
                    } else if (parts.length == 2 && parts[0].equalsIgnoreCase("unmute") && server.getSettings(getAddress()).tier.atLeast(ClientSettings.ClientTier.Tier1)) {
                        String name = parts[1];
                        System.out.println("Unmuting " + name);
                        server.getClients(name).forEach((client -> {
                            server.getSettings(client.getAddress()).unmute();
                        }));
                    }  else if (parts.length >= 1 && parts[0].equalsIgnoreCase("badge")) {
                        String badge = (parts.length == 1 ? "" : Arrays.stream(parts).skip(1).reduce("", (a,b)->a+" "+b)).trim();
                        System.out.println("Setting badge of " + server.getClientWithAddress(getAddress()).getName() + " to " + badge);
                        server.getSettings(getAddress()).badge = badge.trim();
                    } else if (parts.length == 2 && parts[0].equalsIgnoreCase("tier")) {
                        String tierStr = parts[1];
                        ClientSettings.ClientTier tier = ClientSettings.ClientTier.Parse(tierStr);
                        System.out.println("Setting tier of " +  server.getClientWithAddress(getAddress()).getName() + " to "+tier.toBadge());
                        server.getSettings(getAddress()).tier = tier;
                    }
                }

                var cl = server.getClientWithAddress(getAddress());
                if(cl != null)
                    server.broadcast(cl, message);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return false;
        }

        if (!path.startsWith("/name/")) {
            sendIntroduction();
            return false;
        }

        this.name = path.substring("/name/".length());
        System.out.println("Client with name " + this.name + " connected.");

        try {
            writer.write("HTTP/1.1 200 OK\r\n" +
                    "Connection: Keep-Alive\r\n" +
                    "Content-Type: text/html; charset=utf-8\r\n" +
                    "Keep-Alive: timeout=1, max=1000\r\n" +
                    "\r\n" +
                    CHAT_HTML.replaceAll("%name%", this.name)
                            .replaceAll("\r\n", "\n")
                            .replaceAll("\n", "\r\n"));
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        sendMessages(server.getHistory());

        return true;
    }

    public boolean sendMessage(String user, ClientSettings settings, String message) {
        try {
            String userHeader = "<div class=\"bubble\"><div class=\"user\">" + user;

            StringBuilder builder = new StringBuilder();
            builder.append(userHeader);

            if(!settings.badge.isBlank()){
                builder.append("<div class=\"badge\">").append(settings.badge.trim()).append("</div>");
            }

            if(settings.tier != ClientSettings.ClientTier.None) {
                if (settings.tier == ClientSettings.ClientTier.Tier2) {
                    builder.append("<div class=\"badge donate rich\">");
                } else {
                    builder.append("<div class=\"badge donate\">");
                }
                builder.append(settings.tier.toBadge()).append("</div>");
            }

            String messageHeader = "</div><pre>" + message + "</pre></div>";
            builder.append(messageHeader);

            writer.write(builder.toString());
            writer.flush();
        } catch (IOException ignored) {
            return false;
        }

        return true;
    }

    public boolean sendMessages(List<Message> messages){
        try {
            StringBuilder builder = new StringBuilder();

            for(var message : messages) {
                if(!message.isMuted || isMuted()) {
                    String userHeader = "<div class=\"bubble\"><div class=\"user\">" + message.user;
                    builder.append(userHeader);

                    if (!message.badge.isBlank()) {
                        builder.append("<div class=\"badge\">").append(message.badge.trim()).append("</div>");
                    }

                    if (message.tier != ClientSettings.ClientTier.None) {
                        if (message.tier == ClientSettings.ClientTier.Tier2) {
                            builder.append("<div class=\"badge donate rich\">");
                        } else {
                            builder.append("<div class=\"badge donate\">");
                        }
                        builder.append(message.tier.toBadge()).append("</div>");
                    }

                    String messageHeader = "</div><pre>" + message.message + "</pre></div>";
                    builder.append(messageHeader);
                }
            }

            writer.write(builder.toString());
            writer.flush();
        } catch (IOException ignored) {
            return false;
        }

        return true;
    }

    public boolean executeJavascript(String code) {
        try {
            writer.write("<script eval>" + code + ";(cleanEval());</script>");
            writer.flush();
        } catch (IOException ignored) {
            return false;
        }

        return true;
    }

    public InetAddress getAddress(){
        return ((InetSocketAddress)socket.getRemoteSocketAddress()).getAddress();
    }

    private void socketThread() {
        if (!handleRequest()) {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }

        while (socket.isConnected() && !socket.isClosed()) {
            try {
                // client side watchdog since connection could time out
                executeJavascript("watchdogReset();");
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                break;
            }
        }

        System.out.println("Client with nam "+name+" disconnected.");
        onClose.apply(this);
    }

    public String getName() {
        return name;
    }

    public boolean isMuted() {
        return server.getSettings(getAddress()).isMuted();
    }
}
