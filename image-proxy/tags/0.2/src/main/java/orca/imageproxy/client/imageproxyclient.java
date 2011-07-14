package orca.imageproxy.client;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.util.Properties;

public class imageproxyclient {
	
	public static String[] parseCommandLine(String args[])
	{
		String[] parameters = new String[4];
		int paralength=0;
		for(int i=0;i< args.length;i++)
		{
			if(args[i].equals("--url")||args[i].equals("-u"))
			{
				if(i+1>=args.length)
				{
					System.err.println("unparsable parameters, please use --help to see available options.");
					return null;
				}
				if(parameters[0]!=null)
				{
					System.err.println("too many url parameters, only one url parameter is acceptable.");
					System.err.println("try --help for more information");
					return null;
				}
				parameters[0]=args[i+1];
				i++;
				paralength++;
			}
			else if(args[i].equals("--signature")||args[i].equals("-s"))
			{
				if(i+1>=args.length)
				{
					System.err.println("unparable parameters, please use --help to see available options.");
					System.err.println("try --help for more information.");
					return null;
				}
				if(parameters[1]!=null)
				{
					System.err.println("too many signature parameters, only one signature parameter is acceptable.");
					System.err.println("try --help for more information.");
					return null;
				}
				parameters[1]=args[i+1];
				i++;
				paralength++;
			}
			else if(args[i].equals("--help")||args[i].equals("-h"))
			{
				System.out.println("Usage: PROGRAM [OPTION] -u <metadata file url> -g <metadata file's global unique ID> -p <proxy url>");
				System.out.println("Parse the metadata file; Download and register Images in that metadata file.");
				System.out.println();
				System.out.println("Mandatory arguments to long options are mandatory for short options too.");
				System.out.println("-u, --url			url of metadata file");
				System.out.println("-s, --signature			SHA1 hash of metadata file");
				System.out.println("-t, --timeout			the maximum timeout to the connection of server, " +
														"exceeding which the connection will be stopped.");
				System.out.println("-p, --proxy			URL of the ImageProxy installation");
				return null;
			}
			else if(args[i].equals("--timeout")||args[i].equals("-t"))
			{
				if(i+1>=args.length)
				{
					System.err.println("unparable parameters, please use --help to see available options.");
					return null;
				}
				if(parameters[2]!=null)
				{
					System.err.println("too many timeout parameters, only one timeout parameter is acceptable.");
					System.err.println("try --help for more information.");
					return null;
				}
				parameters[2]=args[i+1];
				i++;
			}
			else if (args[i].equals("--proxy") || args[i].equals("-p")) {
				if (i+1>=args.length) {
					System.err.println("you must specify proxy URL. See --help for more information.");
					return null;
				}
				if(parameters[3]!=null)
				{
					System.err.println("too many proxy parameters, only one proxy parameter is acceptable.");
					System.err.println("try --help for more information.");
					return null;
				}
				parameters[3]=args[i+1];
				i++;
				paralength++;
			}
		}
		if(paralength!=3)
		{
			System.err.println("Invalid parameters, please check.");
			System.err.println("Try --help for more information.");
			return null;
		}
		return parameters;
	}

	public static void main(String args[])
	{
		String FILE_SYSTEM_IMAGE_KEY = "FILESYSTEM";
		String KERNEL_IMAGE_KEY = "KERNEL";
		String RAMDISK_IMAGE_KEY = "RAMDISK";
		String ERROR_CODE = "ERROR";
		
		BasicConfigurator.configure();
		Logger l = Logger.getRootLogger();
		l.setLevel(Level.ERROR);
		try{
			String[] parameters=parseCommandLine(args);
			if(parameters==null)
				return;
			System.out.println("Connecting to " + parameters[3]);
			IMAGEPROXYStub stub=new IMAGEPROXYStub(parameters[3]);
			if(parameters[2]==null)
				stub._getServiceClient().getOptions().setTimeOutInMilliSeconds(1000*3600);
			else
			{
				try{
					int timeout=Integer.parseInt(parameters[2]);
					if(timeout<=0)
					{
						System.err.println("timeout parameter should be larger than 0.");
						return;
					}
					stub._getServiceClient().getOptions().setTimeOutInMilliSeconds(1000*timeout);
				}catch(Exception e){
					System.err.println("timeout parameter's number format is not recognizable, please check.");
					return;
				}
			}

			IMAGEPROXYStub.RegisterImage registerimage=new IMAGEPROXYStub.RegisterImage();
			registerimage.setSignature(parameters[1]);
			registerimage.setUrl(parameters[0]);
			IMAGEPROXYStub.RegisterImageResponse response=stub.registerImage(registerimage);
			String returnVal=response.get_return();
			if(!returnVal.equals(ERROR_CODE)){
				Properties imageIds = new Properties();
				ByteArrayInputStream stream = new ByteArrayInputStream(returnVal.getBytes());
				imageIds.load(stream);
	            
				System.out.println("EMI is " + imageIds.getProperty(FILE_SYSTEM_IMAGE_KEY));
	    		if(imageIds.getProperty(KERNEL_IMAGE_KEY) != null){
	    			System.out.println("EKI is " + imageIds.getProperty(KERNEL_IMAGE_KEY));
	    		}
	    		if(imageIds.getProperty(RAMDISK_IMAGE_KEY) != null){
	    			System.out.println("ERI is " + imageIds.getProperty(RAMDISK_IMAGE_KEY));
	    		}
			}else{
				System.out.println("Error");
			}
			
		}catch(Exception e){
			e.printStackTrace();
		}
	}
}