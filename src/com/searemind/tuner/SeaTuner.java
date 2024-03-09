package com.searemind.tuner;

import javax.sound.sampled.*;
import javax.sound.sampled.DataLine.Info;
import javax.swing.*;
import java.awt.*;
import java.util.Hashtable;

public class SeaTuner {
    private static final double[] FREQUENCIES = {174.61, 164.81, 155.56, 146.83, 138.59,
        130.81, 123.47, 116.54, 110.00, 103.83, 98.00, 92.50, 87.31, 82.41, 77.78};
    private static final String[] NAME = { "F", "E", "D#", "D", "C#", "C",
        "B", "A#", "A", "G#", "G", "F#", "F", "E", "D#"};
    private static String matchLabelPrev = "--";
    private static String prevLabelPrev = "--";
    private static String nextLabelPrev = "--";
    private static String freqLabelPrev = "--";
    private static int freqValuePrev = 0;

    private static double normaliseFreq(double hz) {
        // get hz into a standard range to make things easier to deal with
        while ( hz < 82.41 ) {
            hz = 2*hz;
        }
        while ( hz > 164.81 ) {
            hz = 0.5*hz;
        }
        return hz;
    }
    
    private static int closestNote(double hz) {
        double minDist = Double.MAX_VALUE;
        int minFreq = -1;
        for (int i = 0; i < FREQUENCIES.length; i++) {
            double distance = Math.abs(FREQUENCIES[i] - hz);
            if (distance < minDist) {
                minDist = distance;
                minFreq = i;
            }
        }
        return minFreq;
    }
    
    public static void main(String[] args) throws Exception {
        Font font = new Font("sansserif", Font.PLAIN, 24);
        Font bigFont = new Font("sansserif", Font.PLAIN, 48);

        JFrame frame = new JFrame("SeaTuner");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        JLabel matchLabel = new JLabel(matchLabelPrev);
        matchLabel.setFont(bigFont);
        JLabel prevLabel = new JLabel(prevLabelPrev);
        prevLabel.setFont(font);
        JLabel nextLabel = new JLabel(nextLabelPrev);
        nextLabel.setFont(font);
        
        int FREQ_RANGE = 128;        
        JSlider freqSlider = new JSlider(JSlider.HORIZONTAL, -FREQ_RANGE, FREQ_RANGE, 0);
        
        Hashtable<Integer, JLabel> labels = new Hashtable<>();
        labels.put(0, matchLabel);
        labels.put(-FREQ_RANGE, nextLabel);
        labels.put(FREQ_RANGE, prevLabel);
        freqSlider.setLabelTable(labels);
        freqSlider.setPaintLabels(true);
        freqSlider.setPaintTicks(true);
        freqSlider.setMajorTickSpacing(FREQ_RANGE/2);
        freqSlider.setMinorTickSpacing(FREQ_RANGE/8);
        frame.add(freqSlider, BorderLayout.NORTH);
        JLabel freqLabel = new JLabel(freqLabelPrev);
        freqLabel.setFont(new Font("sansserif", Font.PLAIN, 14));
        frame.add(freqLabel, BorderLayout.SOUTH);
        
        frame.pack();
        frame.setVisible(true);
        
        float sampleRate = 44100;
        int sampleSizeInBits = 16;
        int channels = 1;
        boolean signed = true;
        boolean bigEndian = false;
        AudioFormat format = new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
        Info dataLineInfo = new Info(TargetDataLine.class, format);
        TargetDataLine targetDataLine = (TargetDataLine)AudioSystem.getLine(dataLineInfo);
        // read about a second at a time
        targetDataLine.open(format, (int)sampleRate);
        targetDataLine.start();
        
        byte[] buffer = new byte[2*1200];
        int[] a = new int[buffer.length/2];
        
        int n = -1;
        while ( (n = targetDataLine.read(buffer, 0, buffer.length)) > 0 ) {
            for (int i = 0; i < n; i+=2) {
                // convert two bytes into single value
                int value = (short)((buffer[i]&0xFF) | ((buffer[i + 1]&0xFF) << 8));
                a[i >> 1] = value;
            }
            double prevDiff = 0;
            double prevDx = 0;
            double maxDiff = 0;
            int sampleLen = 0;
            int len = a.length/2;
            for (int i = 0; i < len; i++) {
                double diff = 0;
                for ( int j = 0; j < len; j++ ) {
                    diff += Math.abs(a[j] - a[i + j]);
                }
                double dx = prevDiff-diff;
                // change of sign in dx
                if (dx < 0 && prevDx > 0) {
                    // only look for troughs that drop to less than 10% of peak
                    if (diff < (0.1 * maxDiff)) {
                        if (sampleLen == 0) {
                            sampleLen = i - 1;
                        }
                    }
                }
                prevDx = dx;
                prevDiff=diff;
                maxDiff=Math.max(diff,maxDiff);
            }
         
            if (sampleLen > 0) {
                double frequency = (format.getSampleRate()/sampleLen);
                freqLabel.setText(String.format("%.2fhz",frequency));
                frequency = normaliseFreq(frequency);
                int note = closestNote(frequency);
                matchLabelPrev = NAME[note];
                prevLabelPrev = NAME[note - 1];
                nextLabelPrev = NAME[note + 1];
                matchLabel.setText(NAME[note]);
                prevLabel.setText(NAME[note-1]);
                nextLabel.setText(NAME[note+1]);
                
                int value = 0;
                double matchFreq = FREQUENCIES[note];
                if ( frequency < matchFreq ) {
                    double prevFreq = FREQUENCIES[note+1];
                    value = (int)(-FREQ_RANGE*(frequency-matchFreq)/(prevFreq-matchFreq));
                }
                else {
                    double nextFreq = FREQUENCIES[note-1];
                    value = (int)(FREQ_RANGE*(frequency-matchFreq)/(nextFreq-matchFreq));
                }
                freqValuePrev = value;
                freqLabelPrev = String.format("%.2fhz",frequency);
                freqSlider.setValue(value);
            }
            else {
                matchLabel.setText(matchLabelPrev);
                prevLabel.setText(prevLabelPrev);
                nextLabel.setText(nextLabelPrev);
                freqSlider.setValue(freqValuePrev);
                freqLabel.setText(freqLabelPrev);
            }
            prevLabel.setSize(prevLabel.getPreferredSize());
            nextLabel.setSize(nextLabel.getPreferredSize());
            matchLabel.setSize(matchLabel.getPreferredSize());
            freqSlider.repaint();
            freqLabel.repaint();
            try {
                Thread.sleep(10);
            } catch (Exception e) {
                ;;
            }
        }
    } 
}