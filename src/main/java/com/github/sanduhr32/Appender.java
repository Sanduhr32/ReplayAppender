package com.github.sanduhr32;

import org.json.JSONObject;
import org.json.JSONTokener;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.Map;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class Appender extends JFrame {
    private JPanel panel1;
    private JTextField textField1;
    private JButton selectFile1Button;
    private JTextField textField2;
    private JButton selectFile2Button;
    private JButton combineButton;
    private JTextField outputTextField;
    private Desktop desktop;
    private ZipFile recording1;
    private ZipFile recording2;

    public Appender() {
        setContentPane(panel1);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        if (!Desktop.isDesktopSupported()) {
            System.err.println("This is an desktop application.");
        }
        desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.OPEN)) {
            System.err.println("I can not open files for exploring. Aborting.");
        }
        selectFile1Button.addActionListener((listener) -> {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(selectFile1Button) == JFileChooser.APPROVE_OPTION) {
                try {
                    File selected = chooser.getSelectedFile();
                    recording1 = new ZipFile(selected);
                    textField1.setText(selected.toPath().toString());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        selectFile2Button.addActionListener((listener) -> {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(selectFile1Button) == JFileChooser.APPROVE_OPTION) {
                try {
                    File selected = chooser.getSelectedFile();
                    recording2 = new ZipFile(selected);
                    textField2.setText(selected.toPath().toString());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        combineButton.addActionListener((listener) -> {
            if (recording1 == null || recording2 == null) {
                return;
            }

            try {
                JSONObject entityData1 = new JSONObject(new JSONTokener(recording1.getInputStream(recording1.getEntry("entity_positions.json"))));
                JSONObject entityData2 = new JSONObject(new JSONTokener(recording2.getInputStream(recording2.getEntry("entity_positions.json"))));
                JSONObject metaData1 = new JSONObject(new JSONTokener(recording1.getInputStream(recording1.getEntry("metaData.json"))));
                JSONObject metaData2 = new JSONObject(new JSONTokener(recording2.getInputStream(recording2.getEntry("metaData.json"))));

                JSONObject newMeta = new JSONObject();
                JSONObject newPositions = new JSONObject();

                for (final Map.Entry<String, Object> entry : metaData1.toMap().entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    if (!key.equals("duration") && value.equals(metaData2.get(key)))
                        newMeta.put(key, value);
                    else {
                        newMeta.put(key, metaData1.getLong(key) + metaData2.getLong(key));
                    }
                }
                Map<String, Object> pos1 = entityData1.toMap();
                if (pos1.size() == entityData2.toMap().size()) {
                    for (final Map.Entry<String, Object> entry : pos1.entrySet()) {
                        JSONObject entity1 = entityData1.getJSONObject(entry.getKey());
                        JSONObject entity2 = entityData2.getJSONObject(entry.getKey());
                        JSONObject newEntity = entity1;
                        long duration = metaData1.getLong("duration");
                        for (final Map.Entry<String, Object> timePosition : entity2.toMap().entrySet()) {
                            newEntity.put(
                                    Long.toUnsignedString(Long.parseLong(timePosition.getKey()) + duration),
                                    timePosition.getValue());
                        }
                        newPositions.put(entry.getKey(), newEntity);
                    }
                } else {
                    System.err.println("ENTITY VALUE IS DIFFERENT. Aborting");
                    return;
                }

                ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(new File("COMBINED.mcpr")));

                zos.putNextEntry(recording1.getEntry("mods.json"));

                zos.write(readFully(recording2.getInputStream(recording1.getEntry("mods.json"))));

                zos.closeEntry();

                zos.putNextEntry(recording1.getEntry("entity_positions.json"));

                zos.write(newPositions.toString().getBytes());

                zos.closeEntry();

                zos.putNextEntry(recording1.getEntry("metaData.json"));

                zos.write(newMeta.toString().getBytes());

                zos.closeEntry();

                zos.putNextEntry(recording1.getEntry("recording.tmcpr"));

                zos.write(readPackets(recording1.getInputStream(recording1.getEntry("recording.tmcpr")), metaData1));

                zos.closeEntry();

                zos.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private byte[] readFully(InputStream stream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;

        while ((read = stream.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }

        output.close();

        return output.toByteArray();
    }

    private byte[] readPackets(InputStream stream, JSONObject meta) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int read;
        int index = 0;
        int length = 0;

        while ((read = stream.read()) != -1) {
            if (index == 0) {
                int b0 = read;
                int b1 = stream.read();
                int b2 = stream.read();
                int b3 = stream.read();

                if ((b0 | b1 | b2 | b3) < 0) {
                    out.write(-1);
                } else {
                    out.write((int) ((b0 << 24 | b1 << 16 | b2 << 8 | b3)  + meta.getLong("duration")));
                }
                index = 1;
            }
            if (index == 1) {
                int b0 = read;
                int b1 = stream.read();
                int b2 = stream.read();
                int b3 = stream.read();

                if ((b0 | b1 | b2 | b3) < 0) {
                    length = -1;
                } else {
                    length = (b0 << 24 | b1 << 16 | b2 << 8 | b3);
                }
                index = 2;
            }
            if (index == 2) {
                byte[] packet = new byte[length];
                stream.read(packet, 8, length);
                out.write(packet);
                index = 0;
            }
        }

        return out.toByteArray();
    }

    private int readInt(InputStream in) throws IOException {
        int b0 = in.read();
        int b1 = in.read();
        int b2 = in.read();
        int b3 = in.read();
        if ((b0 | b1 | b2 | b3) < 0) {
            return -1;
        }
        return b0 << 24 | b1 << 16 | b2 << 8 | b3;
    }
}
