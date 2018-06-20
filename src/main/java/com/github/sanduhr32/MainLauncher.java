package com.github.sanduhr32;

import javax.swing.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainLauncher extends JFrame {
    private JPanel panel1;
    private JLabel label1;
    private JProgressBar progressBar1;

    public MainLauncher() {
        setContentPane(panel1);
        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor(run -> new Thread(run, "Startup-Thread"));

        service.scheduleAtFixedRate(() -> {
            if (progressBar1.getValue() <= 99)
                progressBar1.setValue(progressBar1.getValue() + 1);
            else {
                Appender appender = new Appender();
                appender.pack();
                appender.setVisible(true);
                this.setVisible(false);
                setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
                service.shutdown();
            }
        },2300, 120, TimeUnit.MILLISECONDS);
    }

    public static void main(String[] args) {
        MainLauncher main = new MainLauncher();
        main.pack();
        main.setVisible(true);
    }
}
