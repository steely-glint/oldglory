/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.co.westhawk.oldglory;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.Reader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Random;
import java.util.Timer;

/**
 *
 * @author thp
 */
public class ColourSender implements Runnable {

    InetSocketAddress _toAdd;
    DatagramSocket _udp;
    Timer tick;
    int colours[];
    int usacolours[];
    int ruscolours[];
    int leds;
    long sleep;
    Thread sender;
    boolean state[];
    Random rand;
    ArrayList<Integer> loyal;

    public int map[];
    double ripples = 0.5;
    double speed = 10.0;
    double min = 0.4;
    double depth = 0.3;
    double rmin = 0.4;
    double rdepth = 0.3;
    double ripplevariance = 0.1;
    double ripplecycle = 500.0;
    private Thread tin;
    private int percentSpeed = 50;

    public ColourSender(String label, int l, long s) {
        tick = new Timer();
        leds = l;
        colours = new int[leds];
        usacolours = new int[leds];
        ruscolours = new int[leds];
        map = new int[leds];
        state = new boolean[leds];
        rand = new Random();
        sleep = s;

        try {
            this.loadProps();
        } catch (IOException ex) {
            System.out.println(ex.getMessage());
        }
        int stride = leds / 3;
        // top row is backwards 
        int le = 0;
        for (int i = stride - 1; i >= 0; i--) {
            map[le++] = i;
        }
        // middlerow is sane
        for (int i = stride; i < stride * 2; i++) {
            map[le++] = i;
        }
        //bottom row is backwards
        for (int i = (stride * 3) - 1; i >= stride * 2; i--) {
            map[le++] = i;
        }

        short port = 0;
        String bits[] = label.split(":");
        if (bits.length != 3) {
            throw new IllegalArgumentException("Wrong format for label");
        }
        if (!bits[0].equals("opc")) {
            throw new IllegalArgumentException("Wrong format for label - expected opc: ");
        }
        port = (short) Integer.parseInt(bits[2]);
        _toAdd = new InetSocketAddress(bits[1], port);

        try {
            _udp = new DatagramSocket();
        } catch (SocketException ex) {
            throw new IllegalArgumentException("Socket problem " + ex.getMessage());
        }

        sender = new Thread(this);
        sender.start();
        tin = new Thread(() -> { runIn();});
        tin.start();
        loyal = new ArrayList();
        reset();
        setAll(0x664444);
        fillruss();
        creep();
    }

    void runIn() {
        DataInputStream din = new DataInputStream(System.in);
        while (true) {
            try {
                String l = din.readLine();
                int rspeed = Integer.parseInt(l);
                if ((rspeed <= 100) && (rspeed >= 0)){
                    percentSpeed = rspeed;
                    System.out.println("% speed ="+ percentSpeed);
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
    
    static double getFloatProps(Properties p, String n, double defv) {
        double ret = defv;
        String vs = p.getProperty(n);
        if (vs != null) {
            ret = Double.parseDouble(vs);
        }
        return ret;
    }

    public void loadProps() throws IOException {
        Properties pr = new Properties();
        Reader reader = Files.newBufferedReader(Paths.get("fadedglory.properties"));
        pr.load(reader);
        ripples = getFloatProps(pr, "ripples", 0.5);
        speed = getFloatProps(pr, "speed", 15.0);
        min = getFloatProps(pr, "min", 0.1);
        depth = getFloatProps(pr, "depth", 0.4);
        rmin = getFloatProps(pr, "rmin", 0.1);
        rdepth = getFloatProps(pr, "rdepth", 0.4);
        ripplevariance = getFloatProps(pr, "ripplevariance", 0.4);
        ripplecycle = getFloatProps(pr, "ripplecycle", 500.0);

        System.out.println("loaded props :");
        System.out.println("ripples:" + ripples);
        System.out.println("speed:" + speed);
        System.out.println("min:" + min);
        System.out.println("depth:" + depth);
        System.out.println("rmin:" + rmin);
        System.out.println("rdepth:" + rdepth);
        System.out.println("ripplevariance:" + ripplevariance);
        System.out.println("ripplecycle:" + ripplecycle);
    }

    public int reset() {
        for (int i = 0; i < leds; i++) {
            loyal.add(i);
            state[i] = false;
        }
        return loyal.size();
    }
    int defFlag[] = {0xff0000, 0x0000FF, 0xFFFFFF};

    public void fillruss() {
        fillruss(defFlag);

    }

    public void fillruss(int[] flag) {
        int n = 0;
        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < ruscolours.length / 3; i++) {
                ruscolours[n++] = flag[j];
            }
        }

    }

    public void setAll(int c) {
        for (int i = 0; i < colours.length; i++) {
            usacolours[i] = c;
        }
    }

    public void ripple() {
        int loop = 0;
        int stripe = leds / 3;
        while (true) {
            double lspeed = speed + speed * ripplevariance * Math.sin(loop/this.ripplecycle);
            for (int j = 0; j < 3; j++) {
                int offs = stripe * j;
                for (int i = 0; i < stripe; i++) {
                    double a = (j + i + (ripples * loop)) * (Math.PI * speed) / leds;
                    double ldepth = state[offs + i] ? rdepth : depth;
                    double lmin = state[offs + i] ? rmin : min;

                    double fac = (lmin + ldepth) + (ldepth * Math.sin(a));
                    int c = state[offs + i] ? this.ruscolours[offs + i] : this.usacolours[offs + i];
                    char rr = (char) ((c & 0xFF0000) >> 16);
                    char rg = (char) ((c & 0x00FF00) >> 8);
                    char rb = (char) (c & 0x0000FF);
                    char r = (char) (rr * fac);
                    char g = (char) (rg * fac);
                    char b = (char) (rb * fac);
                    int shade = ((r << 16) + (g << 8) + b);
                    colours[map[offs + i]] = shade;
                }
            }
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException ex) {
                ;
            }
            loop++;
        }
    }

    public void run() {
        while (true) {
            try {
                writePixels(colours);
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException ex) {
                    ;
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

    }

    public static void main(String[] args) {
        String cmd = "opc:127.0.0.1:7890";
        int leds = 90;
        long sleep = 20;
        if (args.length >= 1) {
            cmd = args[0];
        }
        if (args.length >= 2) {
            sleep = Integer.parseInt(args[1]);
        }
        if (args.length >= 3) {
            leds = Integer.parseInt(args[2]);
        }
        ColourSender opc = new ColourSender(cmd, leds, sleep);
        opc.ripple();
    }

    private void writePixels(int[] colours) throws IOException {
        int capacity = (colours.length * 3 + 4);
        ByteBuffer b = ByteBuffer.allocate(capacity);
        short body_len = (short) (capacity - 4);
        b.put(0, (byte) 0);// channel
        b.put(1, (byte) 0);// command
        b.putShort(2, body_len);
        int offs = 4;
        for (int colour : colours) {
            b.put(offs++, (byte) ((colour & 0xFF0000) >> 16));
            b.put(offs++, (byte) ((colour & 0x00FF00) >> 8));
            b.put(offs++, (byte) (colour & 0x0000FF));
        }
        byte[] opcMess = b.array();
        DatagramPacket dgp = new DatagramPacket(opcMess, 0, opcMess.length, this._toAdd);

        _udp.send(dgp);
    }

    private void rust() {
        Runnable r = () -> {
            while (true) {
                int max = loyal.size();
                long sleeptime;
                if (max == 0) {
                    max = reset();
                    sleeptime = 45000 + (90000 * this.percentSpeed)/100;
                } else {
                    sleeptime = (long) (500 + (1000 * this.percentSpeed)/100);
                }
                try {
                    Thread.sleep(sleeptime);
                    int vic = rand.nextInt(max);
                    int loc = loyal.remove(vic);
                    state[loc] = true;
                } catch (InterruptedException ex) {
                    ;
                }

            }
        };
        Thread rust = new Thread(r);
        rust.start();
    }

    private void creep() {
        Runnable r = () -> {
            while (true) {
                int max = loyal.size();
                long sleeptime;
                if (max == 0) {
                    max = reset();
                    sleeptime = 45000 + (90000 * this.percentSpeed)/100;
                    System.out.println("Rippling"+ " "+sleeptime);
                } else {
                    sleeptime = (500 + (1000 * this.percentSpeed)/100);
                    System.out.println("creeping " + max+ " "+sleeptime);
                }
                try {
                    Thread.sleep(sleeptime);
                    int loc = loyal.remove(max - 1);
                    state[loc] = true;
                } catch (InterruptedException ex) {
                    ;
                }

            }
        };
        Thread rust = new Thread(r);
        rust.start();
    }

    String printRGB(int v) {
        return "r=" + ((0xff0000 & v) >> 16) + " g=" + ((0xff00 & v) >> 8) + " b=" + (0xff & v);
    }

    private void fade() {
        Runnable r = () -> {
            int steps = 0xff - 0x44;
            int step = 0;
            long interval = 90000 / steps;
            int ur = 0x66;
            int ug = 0x44;
            int ub = 0x44;
            for (int i = 0; i < state.length; i++) {
                state[i] = true;
            }
            while (true) {
                try {
                    if (step == steps) {
                        Thread.sleep(45000);
                        reset();
                        step = 0;
                        Thread.sleep(45000);
                        for (int i = 0; i < state.length; i++) {
                            state[i] = true;
                        }
                    } else {
                        System.out.println("step is " + step);
                        int[] fadeFlag = new int[3];
                        for (int u = 0; u < this.defFlag.length; u++) {
                            int tcol = defFlag[u];
                            float rdiff = ((tcol & 0xff0000) >> 16) - ur;
                            float gdiff = ((tcol & 0xff00) >> 8) - ug;
                            float bdiff = (tcol & 0xff) - ub;
                            int rval = ur + ((rdiff < 0.0) ? -step : step);
                            rval = rval > 255 ? 255 : rval;
                            int gval = ur + ((gdiff < 0.0) ? -step : step);
                            gval = gval > 255 ? 255 : gval;
                            int bval = ur + ((bdiff < 0.0) ? -step : step);
                            bval = bval > 255 ? 255 : bval;
                            fadeFlag[u] = ((rval & 0xff) << 16) + ((gval & 0xff) << 8) + (bval & 0xff);
                        }
                        System.out.println("fade flag is " + printRGB(fadeFlag[0]) + " " + printRGB(fadeFlag[1]) + " " + printRGB(fadeFlag[2]));
                        this.fillruss(fadeFlag);
                        step++;
                        Thread.sleep(interval);

                    }
                    //state[loc] = true;
                } catch (InterruptedException ex) {
                    ;
                }

            }
        };
        Thread rust = new Thread(r);
        rust.start();
    }

}
