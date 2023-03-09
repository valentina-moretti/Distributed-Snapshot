package org.application;

import org.project.SnapshotCreator;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Controller extends Thread implements Serializable{
    private Farm farm;
    private int id;
    private int serverPort;
    private SnapshotCreator sc;
    //todo: valentina chiede: ci sta il singleton?
    private static Controller instance;
    public static Controller getInstance(){
        if (instance == null){
            instance = new Controller();
        }
        return instance;
    }
    private Controller(){
        instance=null;
    }

    public void run(){
        try {
            sc = new SnapshotCreator(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
        String s = "";
        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
        while(!s.equals("quit")) {
            try {
                s = console.readLine();
                if(s.equals("connect")){
                    System.out.println("ip: ");
                    s = console.readLine();
                    sc.connect_to(InetAddress.getByName(s));
                }
                else if (s.equals("connections")){
                    System.out.println(sc.getNameToConnection());
                }
                else if (s.equals("write")){
                    System.out.println("Select IP address from: ");
                    System.out.println(sc.getNameToConnection());
                    System.out.println("IP: ");
                    s = console.readLine();
                    sc.connect_to(InetAddress.getByName(s));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public Farm getFarm() {
        return farm;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    void read(){
        System.out.println(sc.readMessages());
    }
}
