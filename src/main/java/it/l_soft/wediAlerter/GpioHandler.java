package main.java.it.l_soft.wediAlerter;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.util.Console;

public class GpioHandler extends Thread  {
	public static final int ALERT_INPUT_PIN = 29;
	public static final int ALARM_INPUT_PIN = 31;
	private static final int IO_WAIT_READ_TIME = 200;

	boolean shutdown = false;
	Console console;
	GpioPinDigitalInput pinAlarm;
	GpioPinDigitalInput pinAlert;
	int alertDownSince = 0;
	int alarmDownSince = 0;
	MessageHandler mh;
	
	public GpioHandler(MessageHandler mh, Console console)  
	{
		this.mh = mh;
		this.console = console;
	}

	private void waitForMS(long ms)
	{
		try {
			Thread.sleep(250);
		} catch (InterruptedException e) {
			;
		}
	}
	
	private void setUp()
	{	
        final GpioController gpio = GpioFactory.getInstance();
        
        // provision gpio pin #01 as an output pin and turn on
        pinAlarm = gpio.provisionDigitalInputPin(RaspiPin.GPIO_21, "alarm");
 
        pinAlert = gpio.provisionDigitalInputPin(RaspiPin.GPIO_22, "alert");
	}
	
	private void loop()
	{
		if (pinAlert.isLow())
		{
			alertDownSince += IO_WAIT_READ_TIME;
			if (alertDownSince == IO_WAIT_READ_TIME * 20)
			{
				// send alert msg
				System.out.println("Alert acticve since more than 4 seconds. Sending messages");
			}
		}
		else
		{
			if (alertDownSince > 0)
			{
				System.out.println("Reset alert to normal");
				alertDownSince = 0;
			}
		}
		
		if (pinAlarm.isLow())
		{
			alarmDownSince += IO_WAIT_READ_TIME;
			if (alarmDownSince == IO_WAIT_READ_TIME * 20)
			{
				// send alarm msg
				System.out.println("Alarm acticve since more than 4 seconds. Sending messages");
			}
		}
		else
		{
			if (alarmDownSince > 0)
			{
				System.out.println("Reset alarm to normal");
				alarmDownSince = 0;
			}
		}
		
		waitForMS(200);
	}
	
	public void run()
	{
		setUp();
		
		while(!shutdown)
		{
			loop();
			waitForMS(500);
		}
	}	
}
