package main.java.it.l_soft.wediAlerter;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.GpioPinPwmOutput;
import com.pi4j.io.gpio.Pin;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.util.Console;

public class GpioHandler extends Thread  {
	final Logger log = LoggerFactory.getLogger(this.getClass()); 

	public static final Pin ALERT_INPUT_PIN = RaspiPin.GPIO_24;
	public static final Pin ALARM_INPUT_PIN = RaspiPin.GPIO_25;
	public static final Pin FAN_SPEED_PIN = RaspiPin.GPIO_26;
	public static final int IO_WAIT_READ_TIME = 500;
	private static final int WAIT_BEFORE_REPORT = 8;
	private static final int TEMP_CHECK_INTERVAL = 5000;
	private static final int TEMP_LOG_INTERVAL = 60000;

	private boolean shutdown = false;
	private GpioPinDigitalInput pinAlarm;
	private GpioPinDigitalInput pinAlert;
	private GpioPinPwmOutput pinTemperature;
	private int alertDownSince = 0;
	private int alarmDownSince = 0;
	private int alertUpSince = 0;
	private int alarmUpSince = 0;
	private long lastTempCheck = 0;
	private long lastTempLog = 0;
	private MessageHandler mh;
	private SimpleDateFormat fmt = new SimpleDateFormat("yy/MM/dd HH:mm:ss");
	private ApplicationProperties ap;
	
	public GpioHandler(MessageHandler mh, Console console, ApplicationProperties ap)  
	{
		this.mh = mh;
		this.ap = ap;		
	}

	private void sendSMS(String msg)
	{

		for(String phoneNumber : ap.getContacts())
		{
			try {
				log.trace("Sending to '" + phoneNumber + "'");
				mh.sendMsg("AT+CMGS=\"" + phoneNumber + "\"\r", true, "> ", false);
				log.trace("Info message is '" + msg + "'");
				mh.sendMsg(msg + "\u001A", true, "OK", false);
			}
			catch (IllegalStateException | IOException e) {
				log.error("Errore durante la spedizione messaggio '" + msg + "'", e);
			}
		}
	}
	
	private void setUp()
	{	
        final GpioController gpio = GpioFactory.getInstance();
//        gpio.setMode(GPIO.BCM), null);
        
        // provision gpio pin 19 & 26 as an input pin 
        pinAlarm = gpio.provisionDigitalInputPin(ALARM_INPUT_PIN, "alarm", PinPullResistance.PULL_DOWN);
 
        pinAlert = gpio.provisionDigitalInputPin(ALERT_INPUT_PIN, "alert", PinPullResistance.PULL_DOWN);
        pinTemperature = gpio.provisionPwmOutputPin(FAN_SPEED_PIN, "fan");
        pinTemperature.setPwm(0);
        
        System.out.println("Pin alarm set to '" + pinAlarm.getPin().getName() + "' current value " + pinAlarm.getState().getName());
        System.out.println("Pin alert set to '" + pinAlert.getPin().getName() + "' current value " + pinAlert.getState().getName());
        System.out.println("Pin temperature set to '" + pinTemperature.getPin().getName());
	}
	
	private void loop()
	{
		long millsNow = new Date().getTime();
		
		// Force the boolean value for testing purposes
		boolean alert = pinAlert.isLow();
		boolean alarm = pinAlarm.isLow();
		
		if (alert)
		{
			if (alertUpSince == 0)
			{
				System.out.println("** Alert is gone UP **");
				log.debug("** Alert is gone UP **");
			}
			alertDownSince = 0 ;
			alertUpSince += IO_WAIT_READ_TIME;
			if (alertUpSince == WAIT_BEFORE_REPORT * 1000)
			{
				// send alert msg
				log.debug("Alert active since more than "  + 
							alertUpSince / 1000  +
							" seconds. Sending messages");
				sendSMS("ALLERTA GUASTO IMPIANTO ANTINCENDIO. ore " + fmt.format(new Date()));
			}
		}
		else
		{
			if (alertUpSince > 0)
			{
				alertDownSince += IO_WAIT_READ_TIME;
				if (alertDownSince == WAIT_BEFORE_REPORT * 1000)
				{
					System.out.println("** Alert is now DOWN **");
					// send alert reset msg
					log.trace("Reset alert to normal");
					sendSMS("RIENTRATA ALLERTA GUASTO IMPIANTO ANTINCENDIO. ore " + fmt.format(new Date()));
					alertUpSince = 0;
				}

			}
		}
		
		if (alarm)
		{
			if (alarmUpSince == 0)
			{
				System.out.println("** Alarm is gone UP **");
				log.debug("** Alarm is gone UP **");
			}
			alarmDownSince = 0 ;
			alarmUpSince += IO_WAIT_READ_TIME;
			if (alarmUpSince == WAIT_BEFORE_REPORT * 1000)
			{
				// send alert msg
				log.debug("Alarm active since more than "  + 
							alarmUpSince / 1000 +
							" seconds. Sending messages");
				sendSMS("ALLARME IMPIANTO ANTINCENDIO. ore " + fmt.format(new Date()));
			}
		}
		else
		{
			if (alarmUpSince > 0)
			{
				alarmDownSince += IO_WAIT_READ_TIME;
				if (alarmDownSince == WAIT_BEFORE_REPORT * 1000)
				{
					System.out.println("** Alarm is now DOWN **");
					// send alert reset msg
					log.trace("Reset alarm to normal");
					sendSMS("RIENTRATO ALLARME IMPIANTO ANTINCENDIO. ore " + fmt.format(new Date()));
					alarmUpSince = 0;
				}

			}
		}
		if (millsNow - lastTempCheck > TEMP_CHECK_INTERVAL)
		{
			float cpuTemp = SystemParameterHandler.getCPUTemp();

			int setPoint = 0;
			if (cpuTemp > ap.getFanTempSetPoints()[3])
			{
				setPoint = 1024;
			}
			else if (cpuTemp > ap.getFanTempSetPoints()[2])
			{
				setPoint = 778;
			}
			else if (cpuTemp > ap.getFanTempSetPoints()[1])
			{
				setPoint = 512;

			}
			else if (cpuTemp > ap.getFanTempSetPoints()[0])
			{
				setPoint = 256;
			}
			pinTemperature.setPwm(setPoint);
			lastTempCheck = millsNow;  

			if (millsNow - lastTempLog > TEMP_LOG_INTERVAL)
			{
				log.debug("last CPU Temp read, was " + cpuTemp);
				log.debug("Set PWM to " + setPoint);
				lastTempLog = millsNow;  
			}
		}
	}
	
	public void setShutdown(boolean value)
	{
		shutdown = value;
	}

	
	public void run()
	{
		setUp();
		
		while(!shutdown)
		{
			loop();
			mh.waitForMS(IO_WAIT_READ_TIME);
		}
	}	
}
