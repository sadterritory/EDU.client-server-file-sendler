import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.HashMap;

public class FileServer {
    private static final int SERVER_PORT = 12346;
    private final HashMap<String, DataOutputStream> clientMap = new HashMap<>();
    private ExecutorService pool = Executors.newCachedThreadPool();

    public FileServer() {
        try (ServerSocket serverSocket = new ServerSocket(SERVER_PORT)) {
            System.out.println("Server is listening on port " + SERVER_PORT);

            while (true) {
                Socket socket = serverSocket.accept();
                pool.execute(new ClientHandler(socket));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void broadcastClientList() {
        String clientList = String.join(", ", clientMap.keySet());
        for (DataOutputStream out : clientMap.values()) {
            try {
                out.writeUTF("CLIENT_LIST " + clientList);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ClientHandler implements Runnable {
        private Socket socket;
        private String clientName;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                DataInputStream dis = new DataInputStream(socket.getInputStream());
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

                // First message from client is expected to be its username
                this.clientName = dis.readUTF();
                synchronized (clientMap) {
                    clientMap.put(clientName, dos);
                    broadcastClientList();
                }

                // Handle client messages
                String command;
                while ((command = dis.readUTF()) != null) {
                    if (command.startsWith("SEND_FILE")) {
                        String[] parts = command.split(" ", 3);
                        if (parts.length == 3) {
                            String fileName = parts[1];
                            String targetUsername = parts[2];
                    
                            DataOutputStream targetDos = clientMap.get(targetUsername);
                            if (targetDos != null) {
                                // Inform the target client a file is coming
                                targetDos.writeUTF("RECEIVE_FILE " + fileName + " from " + this.clientName);
                    
                                // Read file length
                                long fileLength = dis.readLong();
                                targetDos.writeLong(fileLength);
                                
                                // Buffer for file transfer
                                byte[] buffer = new byte[4096];
                                int bytesRead;
                                while (fileLength > 0 && (bytesRead = dis.read(buffer, 0, (int)Math.min(buffer.length, fileLength))) != -1) {
                                    targetDos.write(buffer, 0, bytesRead);
                                    fileLength -= bytesRead;
                                }
                                dos.writeUTF("ACK_FILE_RECEIVED " + fileName);
                                System.out.println("File " + fileName + " sent from " + this.clientName + " to " + targetUsername);
                            } else {
                                System.out.println("User " + targetUsername + " not found.");
                            }
                        }
                    }
                }
            } catch (SocketException e) {
                System.out.println(String.format("Client connection interrupted: " + clientName + " (%s)", e));
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                System.out.println("Client disconnected: " + clientName);
                // Remove client on disconnect
                synchronized (clientMap) {
                    clientMap.remove(clientName);
                    broadcastClientList();
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args) {
        new FileServer();
    }
}