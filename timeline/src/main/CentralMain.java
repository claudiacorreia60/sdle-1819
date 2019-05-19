package main;

import spread.SpreadException;

import java.io.IOException;

public class CentralMain {
    public static void main(String[] args) throws SpreadException, IOException {
        central.Central central = new central.Central("localhost");

        System.out.println("CentralMain starting....");
        central.start();

    }
}
