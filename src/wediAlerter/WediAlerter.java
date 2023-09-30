package wediAlerter;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class WediAlerter {

	public static void main(String[] args) 
	{
		MessageHandler mh = new MessageHandler();
        BufferedReader br = null;
		String lineIn = "";
		try
		{
			mh.openPort();
			while(true)
			{
	            br = new BufferedReader(new InputStreamReader(System.in));
	            if ((lineIn = br.readLine()).compareTo("") == 0)
	            {
	            	break;
		        }
	            lineIn = mh.sendMsg(lineIn.getBytes(), true, "");
	            System.out.println(lineIn);
			}
			mh.serialReader.stopReading();
			mh.serial.close();;
		}
		catch (Exception e) {
			// TODO: handle exception
		}
	}
}
