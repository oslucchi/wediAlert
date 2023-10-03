package main.java.it.l_soft.wediAlerter;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.util.Console;

public class GpioHandler extends Thread  {
	final Logger log = LoggerFactory.getLogger(this.getClass()); 

	public static final int ALERT_INPUT_PIN = 24;
	public static final int ALARM_INPUT_PIN = 25;
	private static final int IO_WAIT_READ_TIME = 500;
	private static final int WAIT_FOR_CYCLES = 20;

	boolean shutdown = false;
	Console console;
	GpioPinDigitalInput pinAlarm;
	GpioPinDigitalInput pinAlert;
	int alertDownSince = 0;
	int alarmDownSince = 0;
	MessageHandler mh;
	SimpleDateFormat fmt = new SimpleDateFormat("yy/MM/dd HH:mm:ss");
	
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
	private void sendSMS(String msg)
	{
		
		String retVal;
		String phoneNumber = "AT+CMGS=\"+393488704431\""; 
		try {
			log.trace("Sending '" + phoneNumber + "'");
			retVal = mh.sendMsg(phoneNumber + "\n", true, ">");
			log.trace(retVal);
			log.trace("Sending \"" + msg + "\"");
			retVal = mh.sendMsg(msg + "\u001A", true, null);
			log.trace(retVal);
		}
		catch (IllegalStateException | IOException e) {
			log.error("Errore durante la spedizione messaggio '" + msg + "'", e);
		}
	}
	
	private void setUp()
	{	
        final GpioController gpio = GpioFactory.getInstance();
//        gpio.setMode(GPIO.BCM), null);
        
        // provision gpio pin 19 & 26 as an input pin 
        pinAlarm = gpio.provisionDigitalInputPin(RaspiPin.GPIO_24, "alarm", PinPullResistance.PULL_DOWN);
 
        pinAlert = gpio.provisionDigitalInputPin(RaspiPin.GPIO_25, "alert", PinPullResistance.PULL_DOWN);
	}
	
	private void loop()
	{
		// Force the boolean value for testing purposes
		boolean alert = pinAlert.isLow() && false;
		boolean alarm = pinAlarm.isLow();
		
		System.out.println("Alarm is " + pinAlarm.getState().getName() + "- Alert is " + pinAlert.getState().getName());
		if (alert)
		{
			alertDownSince += IO_WAIT_READ_TIME;
			if (alertDownSince == IO_WAIT_READ_TIME * WAIT_FOR_CYCLES)
			{
				// send alert msg
				log.debug("Alert acticve since more than "  + 
						alertDownSince / WAIT_FOR_CYCLES / 1000  +
						" seconds. Sending messages");
				sendSMS("ALLERTA GUASTO IMPIANTO ANTINCENDIO. ore " + fmt.format(new Date()));
			}
		}
		else
		{
			if (alertDownSince > 0)
			{
				log.trace("Reset alert to normal");
				sendSMS("RIENTRATA ALLERTA GUASTO IMPIANTO ANTINCENDIO. ore " + fmt.format(new Date()));
				alertDownSince = 0;
			}
		}
		
		if (alarm)
		{
			alarmDownSince += IO_WAIT_READ_TIME;
			if (alarmDownSince == IO_WAIT_READ_TIME * WAIT_FOR_CYCLES)
			{
				// send alarm msg
				log.debug("Alarm acticve since more than "  + 
						alarmDownSince / WAIT_FOR_CYCLES / 1000 +
						" seconds. Sending messages");
				sendSMS("ALLARME IMPIANTO ANTINCENDIO. ore " + fmt.format(new Date()));
			}
		}
		else
		{
			if (alarmDownSince > 0)
			{
				log.trace("Reset alarm to normal");
				sendSMS("RIENTRATO ALLARME IMPIANTO ANTINCENDIO. ore " + fmt.format(new Date()));
				alarmDownSince = 0;
			}
		}
	}
	
	public void run()
	{
		setUp();
		
		while(!shutdown)
		{
			loop();
			waitForMS(IO_WAIT_READ_TIME);
		}
	}	
}
