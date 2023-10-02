package main.java.it.l_soft.wediAlerter;


import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.util.Console;

public class WediAlerter {
	static Context pi4j;
	static Console console;
	public static void main(String[] args) 
	{
		Thread gpioThread;
		pi4j = Pi4J.newAutoContext();
		console = new Console();
	    // print program title/header
        console.title("<-- wediAlert -->", "monitoring input signals");

        // allow for user to exit program using CTRL-C
        console.promptForExit();

		// Create the serial port communication class
		MessageHandler mh = new MessageHandler(pi4j, console, args[1]);
		if (args[0].toUpperCase().compareTo("TEST") == 0)
		{
			mh.testMsgsFromLineInput();
		}
		else
		{
			// start thread to handle changes in GPIO
			gpioThread = new GpioHandler(pi4j, mh, console);
			gpioThread.start();
		}
		
		pi4j.shutdown();
	}
}
