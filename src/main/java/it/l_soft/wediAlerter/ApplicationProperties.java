package main.java.it.l_soft.wediAlerter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ApplicationProperties {
	final Logger log = LoggerFactory.getLogger(WediAlerter.class); 
	
	// site specific
	private String contacts[] = null;
	private float fanTempSetPoints[] = {(float) 52, (float) 49, (float) 47.5, (float) 46.5};
	private String defaultSystemPath = "";
	
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