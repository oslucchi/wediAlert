package main.java.it.l_soft.wediAlerter;


import com.pi4j.util.Console;

public class WediAlerter {
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
			// start thread to handle changes in GPIO
			gpioThread = new GpioHandler(mh, console);
			gpioThread.start();
		}
	}
}
