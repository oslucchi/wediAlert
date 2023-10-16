package main.java.it.l_soft.wediAlerter;


import java.io.IOException;
import java.text.ParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pi4j.util.Console;

public class WediAlerter {
	static final Logger log = LoggerFactory.getLogger(WediAlerter.class); 
	static Console console;
	public static void main(String[] args) throws InterruptedException, IOException, ParseException 
	{
		GpioHandler gpioThread = null;
		ApplicationProperties ap = ApplicationProperties.getInstance(args[0]);
		ap.dump();
		
		// Create the serial port communication class
		MessageHandler mh = new MessageHandler(console, args[1], ap);
		switch(args[2].toUpperCase())
		{
		case "SMS":
			console = new Console();
		    // print program title/header
	        console.title("<-- wediAlert -->", "monitoring input signals");
	
	        // allow for user to exit program using CTRL-C
	        console.promptForExit();
			mh.testMsgsFromLineInput();
			break;
			
		case "SYS":
			new SystemParameterHandler();
			break;
			
		case "PARMS":
			ap.dump();
			break;

		case "GPIO":
			try {
				mh.openPort();
			} 
			catch (InterruptedException | IOException e) {
				log.error("Errore durante apertura porta comunicazione modem '" + e.getMessage() + "'", e);
				System.exit(-1);
			}
			mh.waitForMS(1500);
			log.trace("initialization completed, going to monitor GPIO");
			// start thread to handle changes in GPIO
			gpioThread = new GpioHandler(mh, console, ap);
			gpioThread.start();
		}
		
		while (true){
			try {
				Thread.sleep(30000);
		    } 
			catch (InterruptedException ex){
				log.trace("wait interrupted, going to shutdown");
				break;
		    }
		}
		
		if (mh.isPortOpened())
		{
			mh.closePort();
		}
		
		if ((gpioThread != null) && gpioThread.isAlive())
		{
			gpioThread.setShutdown(true);
			gpioThread.join(GpioHandler.IO_WAIT_READ_TIME * 5);
		}
		
		log.trace("Exiting app");
	}
}
