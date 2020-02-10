package com.stefanbroeder.serially;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;

public class Util {
	public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_GREEN = "\u001B[32m";
	
	static void printInfo(String msg) {
		System.out.println("[*] " + msg);
	}
	
	static void printPositive(String msg) {
		System.out.println(ANSI_GREEN + "[+] " + msg + ANSI_RESET);
	}
	
	static void printHeader(String msg) {
		System.out.println("\n[** " + msg + " **]");
	}
	
	static void printError(String msg) {
		System.out.println("[!] " + msg);
	}
	
	static void DBG(String string) {
		System.out.println(string);
	}
	
	static void terminate(String message) {
		System.err.println(message);
		System.exit(1);		
	}
	
	static byte[] serialize(Object o) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try { 
			ObjectOutputStream oos = new ObjectOutputStream(bos);
			oos.writeObject(o);
		} catch (Exception | NoClassDefFoundError e) {
			return null;
		}
		byte[] ser_obj = bos.toByteArray();
		if(ser_obj.length == 5) {
			return null;
		}
		return ser_obj;
	}
}