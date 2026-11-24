package main.java.it.l_soft.wediAlerter;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pi4j.util.Console;
import com.pi4j.wiringpi.Gpio;

public class GpioHandler extends Thread  {
	final Logger log = LoggerFactory.getLogger(this.getClass()); 

	// wiringPi numbers of the inputs, from fault.gpio and fire.gpio in the site properties:
	// "alert" is the panel's fault output, "alarm" its fire alarm output
	private int alertInputPin;
	private int alarmInputPin;
	public static final int IO_WAIT_READ_TIME = 500;
	private static final int WAIT_BEFORE_REPORT = 8;
	private static final int TEMP_CHECK_INTERVAL = 5000;
	private static final int TEMP_LOG_INTERVAL = 60000;

	private boolean shutdown = false;
	private int alertDownSince = 0;
	private int alarmDownSince = 0;
	private int alertUpSince = 0;
	private int alarmUpSince = 0;
	private long lastTempCheck = 0;
	private long lastTempLog = 0;
	private MessageHandler mh;
	private SimpleDateFormat fmt = new SimpleDateFormat("yy/MM/dd HH:mm:ss");
	private ApplicationProperties ap;
	private LocalDate lastHeartbeat = null;
	
	public GpioHandler(MessageHandler mh, Console console, ApplicationProperties ap)  
	{
		this.mh = mh;
		this.ap = ap;		
	}

	private void sendSMS(String msg)
	{
		sendSMS(msg, ap.getContacts());
	}

	private void sendSMS(String msg, String[] contacts)
	{
		for(String phoneNumber : contacts)
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
	
	/*
	 * WiringPi directly, not pi4j's GpioFactory: provisioning a pin there exports it through
	 * /sys/class/gpio using the pin number, which fails on current kernels ("Unable to open GPIO
	 * direction interface"), while WiringPi itself reads and drives the pins correctly.
	 */
	private void setUp()
	{
		if (Gpio.wiringPiSetup() == -1)
		{
			throw new IllegalStateException("WiringPi setup failed");
		}

		// Pull-down: a cut wire reads as an alarm (fail-safe). The bench setting pulls up instead, so
		// that a Pi with nothing wired reads "normal"; it must never be on when wired to the panel.
		alertInputPin = ApplicationProperties.toWiringPi(ap.getFaultGpio());
		alarmInputPin = ApplicationProperties.toWiringPi(ap.getFireGpio());
		int pull = ap.isBenchPullUp() ? Gpio.PUD_UP : Gpio.PUD_DOWN;
		if (ap.isBenchPullUp())
		{
			log.error("BENCH MODE (gpio.benchPullUp=true): a cut wire is NOT detected. Never use it on the panel");
		}
		Gpio.pinMode(alarmInputPin, Gpio.INPUT);
		Gpio.pullUpDnControl(alarmInputPin, pull);
		Gpio.pinMode(alertInputPin, Gpio.INPUT);
		Gpio.pullUpDnControl(alertInputPin, pull);
		if (ap.getFanWiringPiPin() >= 0)
		{
			Gpio.pinMode(ap.getFanWiringPiPin(), Gpio.PWM_OUTPUT);
			Gpio.pwmWrite(ap.getFanWiringPiPin(), 0);
		}

		log.info("GPIO ready. fire input GPIO" + ap.getFireGpio() + " = " + Gpio.digitalRead(alarmInputPin) +
				 ", fault input GPIO" + ap.getFaultGpio() + " = " + Gpio.digitalRead(alertInputPin));
	}

	private void loop()
	{
		long millsNow = new Date().getTime();
		
		// Force the boolean value for testing purposes
		boolean alert = Gpio.digitalRead(alertInputPin) == 0;
		boolean alarm = Gpio.digitalRead(alarmInputPin) == 0;
		
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
				sendSMS(ap.getFaultText() + ". ore " + fmt.format(new Date()));
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
					sendSMS(ap.getFaultText() + " - RIENTRATO. ore " + fmt.format(new Date()));
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
				sendSMS(ap.getFireText() + ". ore " + fmt.format(new Date()));
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
					sendSMS(ap.getFireText() + " - RIENTRATO. ore " + fmt.format(new Date()));
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
			if (ap.getFanWiringPiPin() >= 0)
			{
				Gpio.pwmWrite(ap.getFanWiringPiPin(), setPoint);
			}
			lastTempCheck = millsNow;  
			checkHeartbeat(cpuTemp);

			if (millsNow - lastTempLog > TEMP_LOG_INTERVAL)
			{
				log.debug("last CPU Temp read, was " + cpuTemp);
				log.debug("Set PWM to " + setPoint);
				lastTempLog = millsNow;  
			}
		}
	}
	
	private String heartbeatText(String what, float cpuTemp)
	{
		return what + " wediAlert: sistema attivo. Allarme " + (alarmUpSince > 0 ? "ATTIVO" : "normale") +
			   ", guasto " + (alertUpSince > 0 ? "ATTIVO" : "normale") +
			   ", CPU " + Math.round(cpuTemp) + "C. ore " + fmt.format(new Date());
	}

	// Called every TEMP_CHECK_INTERVAL: periodic test SMS, and on-demand test through the trigger file
	private void checkHeartbeat(float cpuTemp)
	{
		String trigger = ap.getTestTriggerFile();
		if (trigger != null)
		{
			File f = new File(trigger);
			if (f.exists())
			{
				if (!f.delete())
				{
					log.error("Cannot delete test trigger file '" + trigger + "': test SMS not sent");
				}
				else
				{
					log.info("Test trigger file found: sending a test SMS");
					sendSMS(heartbeatText("PROVA", cpuTemp), ap.getHeartbeatContacts());
				}
			}
		}

		if (ap.getHeartbeatDays().isEmpty())
		{
			return;
		}
		LocalDateTime now = LocalDateTime.now();
		LocalDate today = now.toLocalDate();
		if (today.equals(lastHeartbeat) || !ap.getHeartbeatDays().contains(today.getDayOfWeek()))
		{
			return;
		}
		LocalDateTime from = today.atTime(ap.getHeartbeatTime());
		if (now.isBefore(from) || !now.isBefore(from.plusMinutes(ap.getHeartbeatWindowMinutes())))
		{
			return;
		}
		lastHeartbeat = today;
		log.info("Sending the periodic test SMS");
		sendSMS(heartbeatText("TEST PERIODICO", cpuTemp), ap.getHeartbeatContacts());
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
