package com.project.testers;

import com.project.my_udp.Config;
import com.project.my_udp.control_hub.ControlHub;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;

public class OutputIntegrityCheck {
    public static void main(String[] args) throws Exception{
        RandomAccessFile inf;
        RandomAccessFile outf;
        try {
            inf = new RandomAccessFile(Config.UART_INPUT_FILE, "r");
            outf = new RandomAccessFile(Config.UART_FILE, "r");
        }catch (Exception e){
            System.out.println("Error on opening files " + e.getMessage());
            return;
        }

        int bufi = 0 ,bufo = 0;
        while(true){
            bufi = inf.read();
            bufo = outf.read();
            if(bufi != bufo){
                System.out.println("File mismatch at " + inf.getFilePointer());
                break;
            }

            if(inf.getFilePointer() % 1000000 == 0) System.out.println(inf.getFilePointer());

            if(bufi == -1) break;
        }

        if( bufo != -1) System.out.println("File mismatch at " + inf.getFilePointer());
        else System.out.println("Files match");

        inf.close();
        outf.close();
    }
}
