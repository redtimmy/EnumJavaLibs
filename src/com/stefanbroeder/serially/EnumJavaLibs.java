package com.stefanbroeder.serially;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.rmi.NotBoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.ServiceConfigurationError;

import javax.management.remote.rmi.RMIServerImpl_Stub;
import sun.misc.Unsafe;

@SuppressWarnings("restriction")
public class EnumJavaLibs {
	public static final String JAVA_TOOLS_PATH = System.getProperty("user.home") + "/.serially";
	public static final String JAR_PATH = JAVA_TOOLS_PATH + "/jars";
	
	private String mode = "base64";
	private String filter = "";
	private String csv_filename;
	private FileWriter csvWriter;
	private String rmi_server;
	private int rmi_port;
	private boolean debug = false;
	
	private class JavaDBJar {
		public String filename;
		public ArrayList<String> classnames;

		public JavaDBJar(String filename, String fqdn) {
			this.classnames = new ArrayList<String>();
			this.filename = filename;
			this.classnames.add(fqdn);
		}
	}
	
	static Unsafe unsafe;
	static {
		try {
			Field singleoneInstanceField = Unsafe.class.getDeclaredField("theUnsafe");
			singleoneInstanceField.setAccessible(true);
			unsafe = (Unsafe) singleoneInstanceField.get(null);
		} catch (Exception e) {
			e.printStackTrace();
			Util.terminate("Unable to initialize Unsafe class");
		}
	}
	

	public EnumJavaLibs(String[] args) {
		if(args.length < 1) {
			printUsage();
			System.exit(1);
		}
		
		int cnt = 0;
		// Read mode
		if(args[cnt++].contentEquals("rmi")) { 
			this.mode = "rmi";
			this.rmi_server = args[cnt++];
			this.rmi_port = Integer.parseInt(args[cnt++]);
		}

		// Read options
		while(args.length > cnt) {
			switch(args[cnt++]) {
			case "-f":
				this.filter = args[cnt++];
				break;
			case "-d":
				this.debug = true;
				break;
			}
		}
		
		if(this.mode.contentEquals("base64")) openOutfile();
		runTest();
		if(this.mode.contentEquals("base64")) closeOutfile();
	}
	
	private void DBG(String message) {
		if(this.debug) {
			Util.DBG(message);
		}
	}

	private void openOutfile() {
		DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
	    csv_filename = "enumjavalibs_" + dateFormat.format(new Date()) + ".csv";
		try {
			csvWriter = new FileWriter(csv_filename);
		} catch (Exception e) {
			Util.printError("Couldn't open CSV file");
			e.printStackTrace();
		} 
		
	}

	private void closeOutfile() {
		try {
			csvWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void printUsage() {
		System.out.println(
				"-----------------\n" +
				"Serially - v1.1\n" +
				"by Stefan Broeder\n" +
				"-----------------\n" +
				"Usage:\n" + 
						"\n" + 
						"EnumJavaLibs {MODE} [OPTIONS]\n" +  
						"\n" +
						"MODES:\n" + 
						"base64                  Write base64 encoded serialized objects to CSV file in current directory\n" +
						"rmi {host} {port}       RMI mode. Connect to RMI endpoint and try deserialization.\n" +
						"\n" + 
						"OPTIONS:\n" + 
					    "-f {string}             Only serialize classes from packages which contain the given string (e.g. org.apache.commons) \n" +
					    "-d                      Debug mode on\n");
	}

	public static void main(String[] args) throws Exception {
		new EnumJavaLibs(args);
	}

	private void runTest() {
		Util.printInfo("Fetching jars from " + JAR_PATH + "..");
		
		HashMap<String, JavaDBJar> jars = getJavaDBJars();
		if(jars.isEmpty()) {
			if(this.filter.isEmpty()) {
				Util.terminate("No jar files found, please run JavaClassDB first");
			} else {
				Util.terminate("No jar files found that match the filter");
			}
		}
		
		if(this.mode.equals("rmi")) {
			Util.printInfo("Serializing classes from " + jars.size() + " jars and sending them to RMI endpoint..");
		} else {
			Util.printInfo("Serializing classes from " + jars.size() + " jars..");
		}
		for(JavaDBJar jar : jars.values()) {
			boolean found = false;
			if(loadJar(jar)) {
				DBG("Dynamically loaded " + jar.filename);
				for(String classname : jar.classnames) {
					if(tryToSerializeAndDoOutput(jar, classname)) {
						found = true;
						break;
					}
				}
				if(!found) {
					DBG("No serializable classes in " + jar.filename + ", this jar can not be tested");
				}
			}
		}
		
		Util.printInfo("Finished");
		if(this.mode.equals("base64")) {
			Util.printInfo("See output in " + System.getProperty("user.dir") + "/" + csv_filename);
		}
	}

	private boolean tryToSerializeAndDoOutput(JavaDBJar jar, String classname) {
		byte[] ser_obj = tryToSerialize(classname);
		if(ser_obj != null) {
			DBG("[+] Succesfully serialized "+classname+" of jar "+jar.filename);
			doOutput(jar.filename, classname, ser_obj);
			return true;
		}
		return false;
	}

	private byte[] tryToSerialize(String classname) {
		Object instance = instantiateClass(classname);
		return Util.serialize(instance);
	}

	private void doOutput(String filename, String classname, byte[] ser_obj) {
		if(mode.equals("rmi")) {
			if(doOutputRMI(filename, classname, ser_obj)) {
				Util.printPositive("Library " + filename + " is loaded by the remote application");
			} else {
				DBG("Library " + filename + " is NOT loaded by the remote application");
			}
		} else {
			doOutputB64(filename, classname, ser_obj);
		}
	}

	private void doOutputB64(String filename, String classname, byte[] ser_obj) {
		String b64 = Base64.getEncoder().encodeToString(ser_obj);
		try {
			csvWriter.append(filename + "," + b64 + "\n");
			csvWriter.flush();
		} catch (IOException e) {
			Util.printError("Couldn't append to CSV file");
			e.printStackTrace();
		}
	}

	private boolean doOutputRMI(String filename, String classname, byte[] ser_obj) {
		try {
			Registry registry = LocateRegistry.getRegistry(rmi_server, rmi_port);
			String bound_name = registry.list()[0];
			RMIServerImpl_Stub ri = (RMIServerImpl_Stub) registry.lookup(bound_name);
			Object o = instantiateClass(classname);
			ri.newClient(o);
		} catch (Exception e) {
			if(e.getMessage().contains("ConnectException")) {
				Util.printError("Connection issue with RMI: " + e.getMessage());
			} else if(e.getMessage().contains("cannot be cast to javax.management.remote.rmi.RMIServerImpl_Stub")) {
				Util.printError("Target is using patched Java version. RMI is only vulnerable before implementation of JEP290 (<6u141, <7u131, <8u121)");
				System.exit(1);
			} else if(e.getMessage().contains("ClassNotFoundException")) {
				// Object not found on server, expected in most cases
			} else if(e.getMessage().contains("RemoteException") ||
				  e.getMessage().contains("UnmarshalException")) {
				// Something went wrong, can't assume anything
				Util.printError("Something went wrong with deserialization of " + classname + " at remote side, no conclusions can be drawn");
			} else {
				Util.printError("RMI: " + e.getMessage());
				// Object is found on server, no exception came back
				return true;
			}
			return false;
		}
		return true;
	}

	private Object instantiateClass(String classname) {
		try {
			return unsafe.allocateInstance(Class.forName(classname));
		} catch (Exception | NoClassDefFoundError | 
				 UnsupportedClassVersionError | ServiceConfigurationError |
				 ExceptionInInitializerError | VerifyError e) {}
		return null;
	}

	private boolean loadJar(JavaDBJar jar) {
		String path = JAR_PATH + "/" + jar.filename;
		File file = new File(path);

		try {
			/*We are using reflection here to circumvent encapsulation; addURL is not public*/
			java.net.URLClassLoader loader = (java.net.URLClassLoader)ClassLoader.getSystemClassLoader();
			java.net.URL url = file.toURI().toURL();
			/*Disallow if already loaded*/
			for (java.net.URL it : java.util.Arrays.asList(loader.getURLs())){
				if (it.equals(url)){
					DBG("- "+file.getName()+" contains the class but is already loaded");
					return true;
				}
			}
			java.lang.reflect.Method method = java.net.URLClassLoader.class.getDeclaredMethod("addURL", new Class[]{java.net.URL.class});
			method.setAccessible(true); /*promote the method to public access*/
			method.invoke(loader, new Object[]{url});
			DBG("- " + file.getName() + " is loaded succesfully");
		} catch (final java.lang.NoSuchMethodException | 
				java.lang.IllegalAccessException | 
				java.net.MalformedURLException | 
				java.lang.reflect.InvocationTargetException e){
			DBG("Couldn't dynamically load jar file: "+ e.getMessage());
			return false;
		}
		return true;
	}

	private HashMap<String, JavaDBJar> getJavaDBJars() {
		HashMap<String, JavaDBJar> jars = new HashMap<String, JavaDBJar>();

		Connection connection = null;
		String path = JAVA_TOOLS_PATH + "/java.sqlite";
		try {
			Class.forName("org.sqlite.JDBC");
			connection = DriverManager.getConnection("jdbc:sqlite:" + path);
			String query = "SELECT filename, fqdn FROM jar j JOIN class c ON j.rowid = c.jarID";
			PreparedStatement statement = connection.prepareStatement(query);
			ResultSet rs = statement.executeQuery();
			while(rs.next()) {
				if(applyFilter(rs.getString("fqdn"))) {
					addJarToJars(jars, rs.getString("filename"), rs.getString("fqdn"));
				}
			}
		} catch(SQLException | ClassNotFoundException e) {
			Util.terminate(e.getMessage());
		} finally {
			try {
				if(connection != null) connection.close();
			} catch(SQLException e) {
				Util.terminate(e.getMessage());
			}
		}

		return jars;
	}

	private boolean applyFilter(String fqdn) {
		return filter.length() == 0 || fqdn.contains(filter);
	}

	private void addJarToJars(HashMap<String, JavaDBJar> jars, String filename, String fqdn) {
		if(!jars.containsKey(filename)) {
			JavaDBJar jar = new JavaDBJar(filename, fqdn);
			jars.put(filename, jar);
		} else {
			jars.get(filename).classnames.add(fqdn);
		}

	}
}
