package main.java.it.l_soft.wediAlerter;

import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.DigitalInput;
import com.pi4j.io.gpio.digital.DigitalInputProvider;
import com.pi4j.io.gpio.digital.PullResistance;
import com.pi4j.util.Console;

public class GpioHandler extends Thread  {
	public static final int ALERT_INPUT_PIN = 29;
	public static final int ALARM_INPUT_PIN = 31;
	private static final int IO_WAIT_READ_TIME = 200;

	Context pi4j;
	boolean shutdown = false;
	Console console;
	DigitalInput pinAlarm;
	DigitalInput pinAlert;
	int alertDownSince = 0;
	int alarmDownSince = 0;
	
	public GpioHandler(Context pi4j, MessageHandler mh, Console console)  
	{
		this.pi4j = pi4j;
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
        // get a Digital Input I/O provider from the Pi4J context
        DigitalInputProvider digitalInputProvider = pi4j.provider("pigpio-digital-input");

        // create a digital input instance using the default digital input provider
        // we will use the PULL_DOWN argument to set the pin pull-down resistance on this GPIO pin
        var pinConfig = DigitalInput.newConfigBuilder(pi4j)
                .id("FirePrevAlert")
                .address(ALERT_INPUT_PIN)
                .pull(PullResistance.PULL_DOWN)
                .provider("raspberrypi-digital-input")
                .build();
        pinAlert  = digitalInputProvider.create(pinConfig);

        pinConfig = DigitalInput.newConfigBuilder(pi4j)
                .id("FirePrevAlarm")
                .address(ALARM_INPUT_PIN)
                .pull(PullResistance.PULL_DOWN)
                .provider("raspberrypi-digital-input")
                .build();
        pinAlarm = digitalInputProvider.create(pinConfig);
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
