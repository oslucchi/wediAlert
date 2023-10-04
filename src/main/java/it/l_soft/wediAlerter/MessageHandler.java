package main.java.it.l_soft.wediAlerter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pi4j.io.gpio.exception.UnsupportedBoardType;
import com.pi4j.io.serial.*;

import com.pi4j.util.Console;

public class MessageHandler {
	final Logger log = LoggerFactory.getLogger(this.getClass()); 
	final Serial serial = SerialFactory.createInstance();

	ApplicationProperties ap;
	String msgIn = "";
	String msgOut = "";
	boolean newMsgIn = false;
	Console console;
	
	SerialReader serialReader;
	Thread serialReaderThread;
    
	SerialConfig config;
	String ttyDev;
	
	public MessageHandler(Console console, String ttyDev, ApplicationProperties ap)
	{
		this.console = console;
		this.ttyDev = "/dev/" + ttyDev;
		this.ap = ap;
		
	    config = new SerialConfig();
        try {
        	log.trace("Configuring device '" + this.ttyDev + "' for communications");
			config.device(this.ttyDev )
			      .baud(Baud._115200)
			      .dataBits(DataBits._8)
			      .parity(Parity.NONE)
			      .stopBits(StopBits._1)
			      .flowControl(FlowControl.NONE);
		} 
        catch (UnsupportedBoardType e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void waitForMS(long ms)
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
			console.clearScreen();
			while(true)
			{
				console.print("/> ");
	            if ((lineIn = br.readLine()).compareTo("") == 0)
	            {
	            	break;
		        }
	            if (lineIn.toUpperCase().substring(0,4).compareTo("QUIT") == 0)
	            {
	            	console.println();
	            	closePortAndExit("goodbye", 0);
	            	
	            }
	            lineIn = sendMsg(lineIn.getBytes(), true, "", false);
	            log.debug("sent '" + lineIn + "' - received '" + msgIn + "'"); 
			}
		}
		catch (Exception e) {
			log.error("received expception '" + e.getMessage() + "'", e);
		}
	}
	
	void closePort() throws IllegalStateException, IOException
	{
		serialReader.cleanupIncomingData();
		serialReader.stopReading();
		waitForMS(500);
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
        
//        serial.addListener(new SerialDataEventListener() {
//            @Override
//            public void dataReceived(SerialDataEvent event) {
// 
//                // NOTE! - It is extremely important to read the data received from the
//                // serial port.  If it does not get read from the receive buffer, the
//                // buffer will continue to grow and consume memory.
// 
//                // print out the data received to the console
//                try {
//                    console.println("[HEX DATA]   " + event.getHexByteString());
//                    console.println("[ASCII DATA] " + event.getAsciiString());
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//        });
		serialReader = new SerialReader(console, serial);
		serialReaderThread = new Thread(serialReader, "SerialReader");
		serialReaderThread.setDaemon(true);
		serialReaderThread.start();
		log.debug("Setting mode ECHO OFF");
		sendMsg("ATE 0\r", true, "OK", false);
		log.debug("Setting mode CMGF to 1");
		sendMsg("AT+CMGF=1\r", true, "OK", false);
		log.trace("Communication port opened and successfully set");
    }

	public String sendMsg(String message, boolean waitForAnswer, String expectedReturn, boolean cleanupQueue) throws IllegalStateException, IOException
	{
		return sendMsg(message.getBytes(), waitForAnswer, expectedReturn, cleanupQueue);
	}
	
	public String sendMsg(byte[] message, boolean waitForAnswer, String expectedReturn, boolean cleanupQueue) throws IllegalStateException, IOException
	{
		String receivedMsg = null;
        log.trace("pushing '" + new String(message) + "' to modem");
		serial.write(message);
//		serial.write("\r");

		if(waitForAnswer)
		{
			long interrupt = 0;
			log.trace("Required to wait for answer " + (expectedReturn != null ? "(" + expectedReturn + ")" : ""));
			
			
			while(receivedMsg == null)
			{
				while(!newMsgIn && (interrupt < 5000))
				{
					interrupt += 250;
					waitForMS(250);
				}
				
				if (newMsgIn)
				{
					log.debug("Received '" + msgIn + "'" + 
							  (expectedReturn != null ? " on expected '" + expectedReturn + "'" : ". No check required"));
					String returnCheck = expectedReturn;
					if (expectedReturn.length() > msgIn.length())
					{
						returnCheck = expectedReturn.substring(0, msgIn.length());
					}
					if ((expectedReturn == null) || 
						(expectedReturn.compareTo("") == 0 ) ||
						msgIn.toUpperCase().startsWith(returnCheck.toUpperCase()))
					{
						receivedMsg = new String(msgIn);
						log.debug("Received expected message");
					}
					else
					{
						log.debug("Received '" + receivedMsg + "'" + 
								  (expectedReturn != null ? " on expected '" + expectedReturn + "'" : ""));
						log.trace("trying to get more inpbound messages");
					}
					newMsgIn = false;
					msgIn = "";
					interrupt = 0;
				}
				else
				{
					break;
				}
			}
			
			if (interrupt >= 5000)
			{
				log.debug("wait pending msgin interrupted");				
			}
		}
		if (cleanupQueue || !waitForAnswer)
		{
			log.trace("Required to cleanup incoming data queue");
			serialReader.cleanupIncomingData();
		}
		return receivedMsg;
	}
	
	public class SerialReader implements Runnable {

	    private final Console console;
	    private final Serial serial;
        // We use a buffered reader to handle the data received from the serial port
        BufferedReader br;

	    private boolean continueReading = true;

	    public SerialReader(Console console, Serial serial) {
	        this.console = console;
	        this.serial = serial;
	    }

	    public void stopReading() {
	        continueReading = false;
	    }

	    public void cleanupIncomingData()
	    {
	    	waitForMS(2000);
	    	try {
				while(serial.available() > 0)
				{
					log.trace("Removed '" + br.readLine() + "' from queue");
				}
			} 
	    	catch (IllegalStateException | IOException e) {
	    		log.debug("Exception emptying incoming queue", e);
			}
			log.trace("No residual incomng data in queue");
	    }
	    
	    @Override
	    public void run() {
	    	br = new BufferedReader(new InputStreamReader(serial.getInputStream()));
	        try {
	            // Data from the GPS is received in lines

	            // Read data until the flag is false
	            while (continueReading) {
	                // First we need to check if there is data available to read.
	                // The read() command for pigio-serial is a NON-BLOCKING call, 
	                // in contrast to typical java input streams.
	                var available = serial.available();
	                if ((available > 0) && !newMsgIn) {
	    	            String line = "";
	    	            boolean crPendingLF = false;
	                	log.trace("SerialReader new message of len " + available + " pending");
	                	for (int i = 0; (i < available) && !newMsgIn; i++) {
	                        byte b = (byte) br.read();
	                        
	                        log.trace("SerialReader Read " + b);
	                        // Consider reading till the sequence CR+LF is in
	                        switch(b)
	                        {
	                        case 13:
	                        	crPendingLF = true;
	                        	break;
	                        case 10:
	                        	if (crPendingLF)
	                        	{
	                                if (!line.isEmpty()) {
	                                    // Here we should add code to parse the data to a GPS data object
	                                    log.trace("SerialReader received '" + line + "'");
	                                    msgIn = line;
	                                    newMsgIn = true;
	                                }
	                        	}
	                        	else
	                        	{
	                        		crPendingLF = false;
	                        	}
	                        	break;
	                        default:
	                        	if (b>=32)
	                        	{
	                        		line += (char) b;
	                        	}
	                        	else
	                        	{
	                        		crPendingLF = false;
	                        	}
	                        	break;
	                        }
	                    }
                        // All non-string bytes are handled as line breaks
                        if (!newMsgIn && !line.isEmpty()) {
                            // Data returned without CR+LF trailer. Likely a response to an AT+CMGS command '> '
                            log.trace("SerialReader received '" + line + "'");
                            msgIn = line;
                            newMsgIn = true;
                        }
	                } 
                    Thread.sleep(300);
	            }
	        } catch (Exception e) {
	            console.println("Error reading data from serial: " + e.getMessage());
	            System.out.println(e.getStackTrace());
	        }
	    }
	}

}
