package main.java.it.l_soft.wediAlerter;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApplicationProperties {
	final Logger log = LoggerFactory.getLogger(WediAlerter.class); 
	
	// site specific
	private String contacts[] = null;
	
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
		Properties properties = new Properties();
    	try 
    	{
    		String siteProps = "/site." + (env == null ? "prod" : env) + ".properties";
    		log.debug("using '" + siteProps + "' at '" + ApplicationProperties.class.getResource(siteProps).getPath() + "'");
        	InputStream in = ApplicationProperties.class.getResourceAsStream(siteProps);        	
			properties.load(in);
	    	in.close();
		}
    	catch(IOException e) 
    	{
			log.error("Exception " + e.getMessage(), e);
    		return;
		}
		contacts = properties.getProperty("contacts").split(";");
	}

	public String[] getContacts() {
		return contacts;
	}
}