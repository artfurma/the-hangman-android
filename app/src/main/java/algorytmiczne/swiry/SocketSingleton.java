package algorytmiczne.swiry;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

import shared.GameState;
import shared.Message;
import shared.MessageType;

public class SocketSingleton {
    private static Socket socket;
    private DataInputStream input;
    private DataOutputStream output;
    private static SocketSingleton instance;
    private static short SERVER_PORT = 6969;
    private static String SERVER_IP;
    private static boolean exit = false;
    private static GameState gameState = new GameState();
    private static ObjectInputStream objectInputStream;
    private static ObjectOutputStream objectOutputStream;
    private static GameActivity game;
    private static MainActivity mainActivity;

    public SocketSingleton() {
    }

    public SocketSingleton getInstance(MainActivity view) {
        mainActivity = view;
        if (instance == null)
            initSingleton();
        return instance;
    }

    public SocketSingleton getInstance(GameActivity view) {
        game = view;
        if (instance == null)
            initSingleton();
        return instance;
    }

    public SocketSingleton getInstance() {
        if (instance == null)
            initSingleton();
        return instance;
    }

    public void initSingleton() {
        if (instance == null) {
            instance = new SocketSingleton();
            new Thread(new ConnectingThread()).start();

        }
    }

    public static void initializeIP(String IP) {
        SERVER_IP = IP;
    }

    public void sendLogin(String login) {
        sendMessage(new Message(MessageType.Connect, login));
        waiting();
    }

    public void sendLetter(Character letter) {
        System.out.println("pick letter " + letter);
        sendMessage(new Message(MessageType.PickLetter, letter));
    }

    public void sendWord(String word) {
        sendMessage(new Message(MessageType.PickWord, word));
    }

    public void sendMessage(Message message) {

        new Thread(() -> {
            boolean flag = true;
            try {
                while (flag) {
                    if (objectOutputStream != null) {
                        objectOutputStream.writeObject(message);
                        flag = false;
                    }

                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();


    }

    private static Message readMessage() {
        try {
            return (Message) objectInputStream.readObject();
        } catch (SocketException e) {
            switch (e.getMessage()) {
                case "Socket closed":   // client is exiting
                    return null;
                case "Connection reset":   // server side fault
                    System.out.println("Server is down. Trying to reconnect...");
                    // TO DO: reconnect
                    break;
                default:
                    e.printStackTrace();
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void goodLogin() {
        mainActivity.gameIsReady = true;
        mainActivity.waiting = false;
    }

    private void waiting() {
        mainActivity.gameIsReady = false;
        mainActivity.waiting = true;
    }

    private void wrongLogin() {
        mainActivity.gameIsReady = false;
        mainActivity.waiting = false;
    }

    private void notifyToRedraw() {
        if (game != null)
            game.gameStateChanges(gameState);
    }

    public GameState getGameState() {
        return gameState;
    }

    class ConnectingThread implements Runnable {
        boolean printMessage;

        public void run() {
            try {
                InetAddress serverAddr = InetAddress.getByName(SERVER_IP);
                socket = new Socket(SERVER_IP, SERVER_PORT);
                System.out.print("\tCreating output stream...");
                objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                System.out.println(" OK");
                System.out.print("\tCreating input stream...");
                objectInputStream = new ObjectInputStream(socket.getInputStream());   // will hang there if server doesn't accept connection
                System.out.println(" OK");
                Message message;
                while (!Thread.interrupted()) {
                    message = readMessage();
                    if (message == null)    // client should quit
                        return;
                    switch (message.type) {
                        case Connect:   // server wants to disconnect
                            System.out.println((String) message.data);
                            break;
                        case Disconnect:   // server wants to disconnect
                            exit = true;
                            System.out.println("Server closed connection. Closing client.");
                            return;
                        case GameState:   // server wants to disconnect
                            gameState = (GameState) message.data;
                            System.out.println("Get new game state");

                            if (mainActivity.waiting) {
                                System.out.println("Game has started!");
                                goodLogin();
                                notifyToRedraw();
                            } else {
                                notifyToRedraw();
                                System.out.println("Notify to redrawing");

                            }
                            break;
                        case Ping:
                            sendMessage(new Message(MessageType.Ping));
                            System.out.println("ping");
                            printMessage = false;
                            break;

                        case LoginTaken:
                            System.out.println("Login exists");

                            printMessage = true;
                            wrongLogin();
                            break;
                        default:
                            System.out.println("Unknown message type received from server.");
                    }
                    if (printMessage)
                        System.out.println("Message from server: " + message.type + ": " + message.data);
                }
            } catch (UnknownHostException e1) {
                e1.printStackTrace();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }


}