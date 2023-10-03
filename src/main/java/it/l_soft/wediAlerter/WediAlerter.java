package main.java.it.l_soft.wediAlerter;


import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pi4j.util.Console;

public class WediAlerter {
	static final Logger log = LoggerFactory.getLogger(WediAlerter.class); 
	static Console console;
	public static void main(String[] args) 
	{
		Thread gpioThread;
		console = new Console();
	    // print program title/header
        console.title("<-- wediAlert -->", "monitoring input signals");

        // allow for user to exit program using CTRL-C
        console.promptForExit();

		// Create the serial port communication class
		MessageHandler mh = new MessageHandler(console, args[1]);
		if (args[0].toUpperCase().compareTo("TEST") == 0)
		{
			mh.testMsgsFromLineInput();
		}
		else
		{
			try {
				mh.openPort();
			} 
			catch (InterruptedException | IOException e) {
				log.error("Errore durante apertura porta comunicazione modem '" + e.getMessage() + "'", e);
				System.exit(-1);
			}
			// start thread to handle changes in GPIO
			gpioThread = new GpioHandler(mh, console);
			gpioThread.start();
		}
	}
}
