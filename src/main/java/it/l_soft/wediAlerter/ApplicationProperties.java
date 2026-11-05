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
		
		Path confFilePath = Paths.get(defaultSystemPath);
		log.debug("Path from command line '" + confFilePath.toString() + "' (isDir " +
				  Files.isDirectory(confFilePath) + ")");
		Properties properties = new Properties();
    	try 
    	{
    		String siteProps = "site." + (env == null ? "prod" : env) + ".properties";
    		InputStream in;        	
    		if (Files.isDirectory(confFilePath))
    		{
    			log.debug("Get conf from system file '" + defaultSystemPath + File.separator + siteProps + "'");
    			in = new FileInputStream(new File(defaultSystemPath + File.separator + siteProps));        	
    		}
    		else
    		{
        		log.debug("Get conf from resources at '" + ApplicationProperties.class.getResource(siteProps).getPath() + "'");
        		in = ApplicationProperties.class.getResourceAsStream(File.separator + siteProps);        	
    		}

    		properties.load(in);
	    	in.close();
		}
    	catch(IOException e) 
    	{
			log.error("Exception " + e.getMessage(), e);
    		return;
		}
		contacts = properties.getProperty("contacts").split(";");
		int i = 0;
		for(String point : properties.getProperty("fanTempSetPoints").split(","))
		{
			fanTempSetPoints[i++] = Float.valueOf(point);
		}
		loadHeartbeat(properties);
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