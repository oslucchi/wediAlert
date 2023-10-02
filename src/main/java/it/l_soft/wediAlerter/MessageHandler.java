package main.java.it.l_soft.wediAlerter;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pi4j.context.Context;

import com.pi4j.io.serial.*;

//import com.pi4j.io.serial.FlowControl;
//import com.pi4j.io.serial.Parity;
//import com.pi4j.io.serial.Serial;
//import com.pi4j.io.serial.StopBits;


import com.pi4j.util.Console;

public class MessageHandler {
	final Logger log = LoggerFactory.getLogger(this.getClass()); 
	String msgIn = "";
	String msgOut = "";
	boolean newMsgIn = false;
	Console console;
	Serial serial;
	SerialReader serialReader;
	Thread serialReaderThread;
	Context pi4j;
	String simPin = "9234";
	String ttyDev;
	
	public MessageHandler(Context pi4j, Console console, String ttyDev)
	{
		this.pi4j = pi4j;
		this.console = console;
		this.ttyDev = ttyDev;

// Let's print out to the console the detected and loaded
// providers that Pi4J detected when it was initialized.
//        Providers providers = pi4j.providers();
//        console.box("Pi4J PROVIDERS");
//        console.println();
//        providers.describe().print(System.out);
//        console.println();
	}
	
	void testMsgsFromLineInput()
	{
        BufferedReader br = null;
		String lineIn = "";

		try
		{
			log.trace("Going to open port");
			openPort();
			log.trace("Port Openend");
            br = new BufferedReader(new InputStreamReader(System.in));
			log.trace("BufferedReader created");
			while(true)
			{
	            if ((lineIn = br.readLine()).compareTo("") == 0)
	            {
	            	break;
		        }
	            lineIn = sendMsg(lineIn.getBytes(), true, "");
	            log.debug("sent '" + lineIn + "' - received '" + msgIn + "'"); 
			}
		}
		catch (Exception e) {
			log.error("received expception '" + e.getMessage() + "'", e);
		}
	}
	
	void closePort()
	{
		serialReader.stopReading();
		serial.close();;
	}
	
	void closePortAndExit(String errMsg, int errCOde)
	{
		closePort();
		System.out.println(errMsg);
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			;
		}
		System.exit(errCOde);
	}

	void openPort() throws InterruptedException
	{
		log.trace("building serial on /dev/" + ttyDev);
		
//		serial = pi4j.create(Serial.newConfigBuilder(pi4j)
//		        .use_38400_N81()
//		        .dataBits_8()
//		        .parity(Parity.NONE)
//		        .stopBits(StopBits._1)
//		        .flowControl(FlowControl.NONE)
////		        .id("my-serial")
//		        .device("/dev/" + ttyDev)
//		        .provider("raspberrypi-serial")
//		        .build());
//		log.trace("serial built. " + serial.available() + " " + serial.getDescription());
//		serial.open();

        serial = pi4j.create(Serial.newConfigBuilder(pi4j)
					.baud(115200)
					.dataBits_8()
					.parity(Parity.NONE)
					.stopBits(StopBits._1)
					.flowControl(FlowControl.NONE)
					.id("gsmBord")
					.provider("raspberrypi-serial")
					.device("/dev/merda" + ttyDev)
					.build());
        serial.open();


		
		
		
		
		// Wait till the serial port is open
		console.print("Waiting till serial port is open");
		while (!serial.isOpen()) {
		    Thread.sleep(1000);
		    log.debug("serial is " + (serial.isOpen() ? "" : "not ") + "opened");
		}
	
		// Start a thread to handle the incoming data from the serial port
		serialReader = new SerialReader(console, serial);
		serialReaderThread = new Thread(serialReader, "SerialReader");
		serialReaderThread.setDaemon(true);
		serialReaderThread.start();
		
		if (simPin.compareTo("") == 0)
		{
			sendMsg(("AT+CPIN='" + simPin + "'").getBytes(), true, null);
			if (!newMsgIn || msgIn.compareTo("OK") != 0)
			{
				closePortAndExit("The required PIN " + simPin + " has not been accepted", -1);
			}
		}
		// wait for the CPIN READY to appear
		while(!newMsgIn)
		{
			try {
				Thread.sleep(250);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		newMsgIn = false;
		if (!msgIn.contains("READY"))
		{
			closePortAndExit("The sim could not be registered (" + msgIn + ")", -1);
		}
		
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
