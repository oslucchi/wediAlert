package main.java.it.l_soft.wediAlerter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pi4j.io.gpio.exception.UnsupportedBoardType;
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
	SerialReader serialReader;
	Thread serialReaderThread;
	final Serial serial = SerialFactory.createInstance();
    SerialConfig config;
	String simPin = "9234";
	String ttyDev;
	
	public MessageHandler(Console console, String ttyDev)
	{
		this.console = console;
		this.ttyDev = ttyDev;
		
	    config = new SerialConfig();
        try {
        	log.trace("Trying to open '" + SerialPort.getDefaultPort() + "'");
			config.device(SerialPort.getDefaultPort())
			      .baud(Baud._115200)
			      .dataBits(DataBits._8)
			      .parity(Parity.NONE)
			      .stopBits(StopBits._1)
			      .flowControl(FlowControl.NONE);
		} 
        catch (UnsupportedBoardType | IOException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void waitForMS(long ms)
	{
		try {
			Thread.sleep(250);
		} catch (InterruptedException e) {
			;
		}
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
	
	void closePort() throws IllegalStateException, IOException
	{
		serialReader.stopReading();
		serial.close();;
	}
	
	void closePortAndExit(String errMsg, int errCOde) throws IllegalStateException, IOException
	{
		closePort();
		System.out.println(errMsg);
		waitForMS(250);
		System.exit(errCOde);
	}

	void openPort() throws InterruptedException, IOException
	{
		log.trace("Opening configured serial");
        serial.open(config);
        
        serial.addListener(new SerialDataEventListener() {
            @Override
            public void dataReceived(SerialDataEvent event) {
 
                // NOTE! - It is extremely important to read the data received from the
                // serial port.  If it does not get read from the receive buffer, the
                // buffer will continue to grow and consume memory.
 
                // print out the data received to the console
                try {
                    console.println("[HEX DATA]   " + event.getHexByteString());
                    console.println("[ASCII DATA] " + event.getAsciiString());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        
 
    }
	
	public String sendMsg(byte[] message, boolean waitForAnswer, String expectedAnswer) throws IllegalStateException, IOException
	{
		String receivedMsg = null;
        log.trace("Sending '" + new String(message) + "'");
		serial.write(message);
		serial.write("\r");
		while(waitForAnswer && !newMsgIn)
		{
			waitForMS(250);
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
