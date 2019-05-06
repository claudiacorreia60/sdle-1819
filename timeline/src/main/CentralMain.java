package main;

import spread.SpreadException;

import java.io.InterruptedIOException;
import java.net.UnknownHostException;

public class CentralMain {
    public static void main(String[] args) throws SpreadException, UnknownHostException, InterruptedIOException {
        central.Central central = new central.Central("localhost");

        System.out.println("CentralMain starting....");
        central.start();

    }
}
