import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.Scanner;

public class FileClient {
    private String serverIp;
    private int serverPort;
    private Socket socket;
    private DataInputStream dis;
    private DataOutputStream dos;
    private final String username;
    private boolean isConnected = false;

    public FileClient(String serverIp, int serverPort, String username) {
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        this.username = username;
        connectToServer();
        listenForServerMessages();
        processInput();
    }

    private enum Commands {
        CLIENT_LIST, RECEIVE_FILE, ACK_FILE_RECEIVED
    }

    private void connectToServer() {
        try {
            socket = new Socket(serverIp, serverPort);
            dis = new DataInputStream(socket.getInputStream());
            dos = new DataOutputStream(socket.getOutputStream());
            dos.writeUTF(username);
            isConnected = true;
            System.out.println("Connected to the server as " + username);
        } catch (IOException ex) {
            System.out.println("Could not connect to server: " + ex.getMessage());
            isConnected = false;
        }
    }

    private void listenForServerMessages() {
        new Thread(() -> {
            try {
                while (isConnected) {
                    String msg = dis.readUTF();
                    String[] msgParts = msg.split(" ");
                    Commands cmd = Commands.valueOf(msgParts[0]);
                    switch (cmd) {
                        case CLIENT_LIST:
                            System.out.println("Connected clients: " + String.join(" ", Arrays.copyOfRange(msgParts, 1, msgParts.length)));
                            break;
                        case RECEIVE_FILE:
                            recieveFile(Arrays.copyOfRange(msgParts, 1, msgParts.length));
                            break;
                        case ACK_FILE_RECEIVED:
                            System.out.println("File " + msgParts[1] + " was sent.");
                            break;
                        default:
                            System.out.println("Server: " + msg);
                            break;
                    }
                }
            } catch (IOException ex) {
                System.out.println("Disconnected from server. Attempting to reconnect...");
                isConnected = false;
                attemptReconnect();
            }
        }).start();
    }

    private void processInput() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            if (!isConnected) {
                attemptReconnect();
                continue;
            }

            String input = scanner.nextLine();
            // Add command handling for "reconnect"
            if ("reconnect".equalsIgnoreCase(input.trim())) {
                connectToServer();
                continue;
            }

            if ("exit".equalsIgnoreCase(input)) {
                break; // Exit the loop and close the client
            }

            try {
                if (input.startsWith("sendfile")) {
                    String[] parts = input.split(" ", 3);
                    if (parts.length == 3) {
                        String filePath = parts[1];
                        String targetUsername = parts[2];

                        File file = new File(filePath);
                        if (file.exists()) {
                            // Notify server of intent to send file
                            dos.writeUTF("SEND_FILE " + file.getName() + " " + targetUsername);

                            // Send file length first
                            dos.writeLong(file.length());

                            // Send file data
                            FileInputStream fis = new FileInputStream(file);
                            byte[] buffer = new byte[4096];
                            int bytesRead, blockNum = 1;
                            System.out.println("Starting send file " + filePath + " to " + targetUsername + "...");
                            while ((bytesRead = fis.read(buffer)) != -1) {
                                String blockStr = "block №" + blockNum + " with size " + bytesRead;
                                System.out.print("Sending " + blockStr + "... ");
                                dos.writeUTF(blockStr);
                                dos.write(buffer, 0, bytesRead);
                                System.out.println("Successfully sent");
                                blockNum++;
                            }
                            System.out.println("All blocks were sent");
                            fis.close();
                        } else {
                            System.out.println("File does not exist: " + filePath);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void recieveFile(String[] msgParts) {
        try {
            if (msgParts.length == 3) {
                String fileName = msgParts[0];
                String fromUsername = msgParts[2];
                long fileLength = dis.readLong();

                File userDir = new File(username);
                if (!userDir.exists()) {
                    userDir.mkdir();
                }

                File file = new File(userDir, fileName);
                FileOutputStream fos = new FileOutputStream(file);

                byte[] buffer = new byte[4096];
                int bytesRead = -2;
                System.out.println("Starting receive file " + fileName + " from client " + fromUsername + "...");
                while (fileLength > 0 && bytesRead != -1) {
                    String blockStr = dis.readUTF();
                    bytesRead = dis.read(buffer, 0, (int)Math.min(buffer.length, fileLength));
                    System.out.print("Got " + blockStr + ". Writing to file... ");
                    fos.write(buffer, 0, bytesRead);
                    System.out.println("Successfully written");
                    fileLength -= bytesRead;
                }
                System.out.println("File was received fully");
                fos.close();

                System.out.println("Received file: " + fileName + " from " + fromUsername);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void attemptReconnect() {
        int[] intervals = {5, 10, 15, 20, 25}; // Reconnection intervals
        for (int interval : intervals) {
            try {
                System.out.println("Attempting to reconnect in " + interval + " seconds...");
                Thread.sleep(interval * 1000);
                connectToServer();
                if (isConnected) {
                    listenForServerMessages();
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Reconnection attempt interrupted.");
                return;
            }
        }
        System.out.println("Failed to reconnect after several attempts.");
    }

    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = 12346;
        String username = "";
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter the username: ");
        username = scanner.nextLine();
        new FileClient(host, port, username);
    }
}