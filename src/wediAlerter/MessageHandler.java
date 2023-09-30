package wediAlerter;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.serial.FlowControl;
import com.pi4j.io.serial.Parity;
import com.pi4j.io.serial.Serial;
import com.pi4j.io.serial.StopBits;
import com.pi4j.util.Console;

public class MessageHandler {
	String msgIn = "";
	String msgOut = "";
	boolean newMsgIn = false;
	Console console;
	Context pi4j;
	Serial serial;
	SerialReader serialReader;
	Thread serialReaderThread;
	
	void openPort() throws InterruptedException
	{
		console = new Console();
		pi4j = Pi4J.newAutoContext();
	
		serial = pi4j.create(Serial.newConfigBuilder(pi4j)
		        .use_9600_N81()
		        .dataBits_8()
		        .parity(Parity.NONE)
		        .stopBits(StopBits._1)
		        .flowControl(FlowControl.NONE)
		        .id("my-serial")
		        .device("/dev/ttyS0")
		        .provider("pigpio-serial")
		        .build());
		serial.open();
	
		// Wait till the serial port is open
		console.print("Waiting till serial port is open");
		while (!serial.isOpen()) {
		    Thread.sleep(250);
		}
	
		// Start a thread to handle the incoming data from the serial port
		serialReader = new SerialReader(console, serial);
		serialReaderThread = new Thread(serialReader, "SerialReader");
		serialReaderThread.setDaemon(true);
		serialReaderThread.start();	
	}
	
	public String sendMsg(byte[] message, boolean waitForAnswer, String expectedAnswer)
	{
		String receivedMsg = null;
		serial.write(message);
		while(waitForAnswer && !newMsgIn)
		{
			try {
				Thread.sleep(250);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		receivedMsg = msgIn;
		newMsgIn = false;
		msgIn = "";
		return receivedMsg;
	}
	
	public class SerialReader implements Runnable {

	    private final Console console;
	    private final Serial serial;

	    private boolean continueReading = true;

	    public SerialReader(Console console, Serial serial) {
	        this.console = console;
	        this.serial = serial;
	    }

	    public void stopReading() {
	        continueReading = false;
	    }

	    @Override
	    public void run() {
	        // We use a buffered reader to handle the data received from the serial port
	        BufferedReader br = new BufferedReader(new InputStreamReader(serial.getInputStream()));

	        try {
	            // Data from the GPS is received in lines
	            String line = "";

	            // Read data until the flag is false
	            while (continueReading) {
	                // First we need to check if there is data available to read.
	                // The read() command for pigio-serial is a NON-BLOCKING call, 
	                // in contrast to typical java input streams.
	                var available = serial.available();
	                if (available > 0) {
	                    for (int i = 0; i < available; i++) {
	                        byte b = (byte) br.read();
	                        if (b < 32) {
	                            // All non-string bytes are handled as line breaks
	                            if (!line.isEmpty()) {
	                                // Here we should add code to parse the data to a GPS data object
	                                console.println("Data: '" + line + "'");
	                                msgIn = line;
	                                newMsgIn = true;
	                                line = "";
	                            }
	                        } else {
	                            line += (char) b;
	                        }
	                    } 
	                } else {
	                    Thread.sleep(250);
	                }
	            }
	        } catch (Exception e) {
	            console.println("Error reading data from serial: " + e.getMessage());
	            System.out.println(e.getStackTrace());
	        }
	    }
	}

}
