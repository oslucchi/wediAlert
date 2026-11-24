package main.java.it.l_soft.wediAlerter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ApplicationProperties {
	final Logger log = LoggerFactory.getLogger(WediAlerter.class); 
	
	// site specific
	private String contacts[] = null;
	private float fanTempSetPoints[] = {(float) 52, (float) 49, (float) 47.5, (float) 46.5};
	private String defaultSystemPath = "";
	// heartbeat: periodic test SMS, so that a dead modem/SIM/network is noticed
	private Set<DayOfWeek> heartbeatDays = EnumSet.noneOf(DayOfWeek.class);
	private LocalTime heartbeatTime = LocalTime.of(10, 0);
	private int heartbeatWindowMinutes = 60;
	private String heartbeatContacts[] = null;
	// bench test only: see gpio.benchPullUp in site.example.properties
	private boolean benchPullUp = false;
	// fan.gpio: BCM number of the fan PWM pin (see site.example.properties); -1 = no fan
	private int fanGpio = 18;
	private int fanWiringPiPin = 1;
	// panel inputs, BCM numbers (see site.example.properties): fault and fire alarm outputs of the panel
	private int faultGpio = 26;
	private int fireGpio = 19;
	// SMS texts for the two panel outputs; " - RIENTRATO" is appended when the condition clears
	private String faultText = "GUASTO IMPIANTO ANTINCENDIO";
	private String fireText = "ALLARME INCENDIO";
	private String testTriggerFile = null;
	
	private static ApplicationProperties instance = null;
		
	public static ApplicationProperties getInstance(String env)
	{
		if (instance == null)
		{
			instance = new ApplicationProperties(env);
			
		}
		return(instance);
	}
	
	private ApplicationProperties(String env)
	{
		log.trace("ApplicationProperties start");
		
		defaultSystemPath = System.getProperty("directory.for.config");
		log.debug("default conf dir is '" + defaultSystemPath + "'");
		
		Properties properties = new Properties();
		// site.<env>.properties holds the phone numbers and is not in the repository: it is read from
		// the -Ddirectory.for.config folder (/etc/wedialert on the Pi), else from the classpath for a
		// local run. Copy site.example.properties to create it.
		String siteProps = "site." + (env == null ? "prod" : env) + ".properties";
		boolean fromDir = defaultSystemPath != null && Files.isDirectory(Paths.get(defaultSystemPath));
		String source = fromDir ? defaultSystemPath + File.separator + siteProps : "classpath /" + siteProps;
		log.debug("Get conf from " + source);
		try (InputStream in = fromDir ? new FileInputStream(new File(defaultSystemPath, siteProps))
									  : ApplicationProperties.class.getResourceAsStream("/" + siteProps))
		{
			if (in == null)
			{
				throw new IOException("not found");
			}
			properties.load(in);
		}
		catch(IOException e)
		{
			String msg = "Cannot read the site configuration " + source + " (" + e.getMessage() +
						 "). Create it from site.example.properties, or set -Ddirectory.for.config";
			log.error(msg);
			throw new IllegalStateException(msg, e);
		}
		if (properties.getProperty("contacts", "").trim().isEmpty())
		{
			String msg = "No contacts in " + source + ": nobody would get the SMS";
			log.error(msg);
			throw new IllegalStateException(msg);
		}
		contacts = properties.getProperty("contacts").split(";");
		int i = 0;
		for(String point : properties.getProperty("fanTempSetPoints", "46.5, 47.5, 49, 52").split(","))
		{
			fanTempSetPoints[i++] = Float.valueOf(point);
		}
		loadHeartbeat(properties);
		benchPullUp = Boolean.parseBoolean(properties.getProperty("gpio.benchPullUp", "false").trim());
		loadFanPin(properties);
		faultText = smsText(properties, "sms.fault.text", faultText);
		fireText = smsText(properties, "sms.fire.text", fireText);
		loadInputPins(properties);
		log.info("SMS texts: fault (GPIO" + faultGpio + ") '" + faultText + "', fire (GPIO" + fireGpio + ") '" + fireText + "'");
	}

	// BCM -> wiringPi numbering for the 40-pin header (index = BCM number, -1 = not on the header)
	private static final int[] BCM_TO_WIRINGPI = {
			30, 31,  8,  9,  7, 21, 22, 11, 10, 13, 12, 14, 26, 23, 15, 16,
			27,  0,  1, 24, 28, 29,  3,  4,  5,  6, 25,  2 };
	// pins the inputs must not use: I2C ID EEPROM (0, 1) and the modem UART (14, 15)
	private static final Set<Integer> RESERVED_GPIO = Set.of(0, 1, 14, 15);

	/** wiringPi number of a BCM pin, -1 if it is not on the header */
	public static int toWiringPi(int bcm)
	{
		return bcm >= 0 && bcm < BCM_TO_WIRINGPI.length ? BCM_TO_WIRINGPI[bcm] : -1;
	}

	/*
	 * fault.gpio=26   BCM number of the input wired to the panel's fault output
	 * fire.gpio=19    BCM number of the input wired to the panel's fire alarm output
	 * Both read 1 while the panel loop is closed (normal) and 0 when it opens. An invalid setting
	 * (unknown pin, reserved pin, same pin twice or the fan pin) is logged and the defaults are kept.
	 */
	private void loadInputPins(Properties properties)
	{
		int fault = parseGpio(properties, "fault.gpio", faultGpio);
		int fire = parseGpio(properties, "fire.gpio", fireGpio);
		if (fault == fire || fault == fanGpio || fire == fanGpio)
		{
			log.error("fault.gpio=" + fault + ", fire.gpio=" + fire + ", fan.gpio=" + fanGpio +
					  ": the inputs must be two different pins, not the fan pin. Using fault GPIO" +
					  faultGpio + ", fire GPIO" + fireGpio);
			return;
		}
		faultGpio = fault;
		fireGpio = fire;
	}

	private int parseGpio(Properties properties, String key, int fallback)
	{
		String value = properties.getProperty(key, "").trim();
		if (value.isEmpty())
		{
			return fallback;
		}
		try {
			int bcm = Integer.parseInt(value);
			if (toWiringPi(bcm) >= 0 && !RESERVED_GPIO.contains(bcm))
			{
				return bcm;
			}
		}
		catch(NumberFormatException e) {
			// reported below
		}
		log.error(key + "=" + value + " is not a usable GPIO (2-27, not 14 or 15): using GPIO" + fallback);
		return fallback;
	}

	/** BCM number of the fault input */
	public int getFaultGpio()
	{
		return faultGpio;
	}

	/** BCM number of the fire alarm input */
	public int getFireGpio()
	{
		return fireGpio;
	}

	/*
	 * sms.fault.text   sent when the fault input (fault.gpio) opens
	 * sms.fire.text    sent when the fire alarm input (fire.gpio) opens
	 * " - RIENTRATO" is appended when the condition clears; the time is added to both.
	 */
	private String smsText(Properties properties, String key, String fallback)
	{
		String text = properties.getProperty(key, "").trim();
		return text.isEmpty() ? fallback : text;
	}

	public String getFaultText()
	{
		return faultText;
	}

	public String getFireText()
	{
		return fireText;
	}

	/*
	 * fan.gpio=18   BCM number of the pin driving the fan, as in "gpio -g". Only the hardware PWM
	 *               pins work: 12, 13 or 18. none = no fan control.
	 */
	private void loadFanPin(Properties properties)
	{
		String value = properties.getProperty("fan.gpio", "18").trim();
		if (value.equalsIgnoreCase("none"))
		{
			fanGpio = -1;
			fanWiringPiPin = -1;
			log.info("fan control off (fan.gpio=none)");
			return;
		}
		// BCM -> wiringPi numbering, PWM capable pins only
		switch(value)
		{
		case "12": fanWiringPiPin = 26; break;
		case "13": fanWiringPiPin = 23; break;
		case "18": fanWiringPiPin = 1; break;
		default:
			log.error("fan.gpio=" + value + " is not a free hardware PWM pin (12, 13 or 18): fan control off");
			fanGpio = -1;
			fanWiringPiPin = -1;
			return;
		}
		fanGpio = Integer.parseInt(value);
		log.info("fan on GPIO" + fanGpio + " (wiringPi " + fanWiringPiPin + ")");
	}

	/** BCM number of the fan pin, -1 when there is no fan control */
	public int getFanGpio()
	{
		return fanGpio;
	}

	/** wiringPi number of the fan pin, -1 when there is no fan control */
	public int getFanWiringPiPin()
	{
		return fanWiringPiPin;
	}

	public boolean isBenchPullUp()
	{
		return benchPullUp;
	}

	/*
	 * heartbeat.days=MONDAY             days of the week (comma separated); empty/missing = off
	 * heartbeat.time=10:00              local time the test SMS is sent at
	 * heartbeat.windowMinutes=60        sent once if the service runs in [time, time + window)
	 * heartbeat.contacts=+39...;+39...  optional; default: first contact only; 'all' = every contact
	 * test.triggerFile=/run/wedialert/send-test   optional: when this file appears, a test SMS
	 *                                   is sent at once and the file deleted
	 */
	private void loadHeartbeat(Properties properties)
	{
		String days = properties.getProperty("heartbeat.days", "").trim();
		for(String day : days.split(","))
		{
			if (!day.trim().isEmpty())
			{
				try {
					heartbeatDays.add(DayOfWeek.valueOf(day.trim().toUpperCase()));
				}
				catch(IllegalArgumentException e) {
					log.error("heartbeat.days: '" + day.trim() + "' is not a day of the week (MONDAY ... SUNDAY)");
				}
			}
		}
		try {
			heartbeatTime = LocalTime.parse(properties.getProperty("heartbeat.time", "10:00").trim());
		}
		catch(Exception e) {
			log.error("heartbeat.time is not HH:mm, using " + heartbeatTime);
		}
		try {
			heartbeatWindowMinutes = Integer.parseInt(properties.getProperty("heartbeat.windowMinutes", "60").trim());
		}
		catch(NumberFormatException e) {
			log.error("heartbeat.windowMinutes is not a number, using " + heartbeatWindowMinutes);
		}
		String hbContacts = properties.getProperty("heartbeat.contacts", "").trim();
		heartbeatContacts = hbContacts.isEmpty() ? (contacts.length == 0 ? contacts : new String[] { contacts[0] }) : hbContacts.equalsIgnoreCase("all") ? contacts : hbContacts.split(";");
		String trigger = properties.getProperty("test.triggerFile", "").trim();
		testTriggerFile = trigger.isEmpty() ? null : trigger;
		log.info("heartbeat: " + (heartbeatDays.isEmpty() ? "off" :
				 heartbeatDays + " at " + heartbeatTime + " (window " + heartbeatWindowMinutes + " min, " +
				 heartbeatContacts.length + " contacts)") +
				 (testTriggerFile == null ? "" : ", test trigger file " + testTriggerFile));
	}

	public Set<DayOfWeek> getHeartbeatDays() {
		return heartbeatDays;
	}

	public LocalTime getHeartbeatTime() {
		return heartbeatTime;
	}

	public int getHeartbeatWindowMinutes() {
		return heartbeatWindowMinutes;
	}

	public String[] getHeartbeatContacts() {
		return heartbeatContacts;
	}

	public String getTestTriggerFile() {
		return testTriggerFile;
	}

	public String[] getContacts() {
		return contacts;
	}
	
	public float[] getFanTempSetPoints() {
		return fanTempSetPoints;
	}

	public void dump() {
		System.out.println("\n\n******************");
		System.out.println("Parameters from config file:\n");

		System.out.println("Contacts are:");
		for(String contact : contacts)
		{
			System.out.print(contact + " - ");
		}
		System.out.print("\ntemp set points are: ");
		for(float setPoint: fanTempSetPoints)
		{
			System.out.print(setPoint + ", ");
		}
		System.out.println("\n******************\n\n");
	}

}