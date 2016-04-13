/*
 * This application realizes a simple version of the distributed Bellman-Ford
 * algorithm. The algorithm is operated on a set of distributed client programs.
 * The clients perform the distributed distance computation and support a user 
 * interface, e.g., it allows the user to edit links to the neighbors and view
 * the routing table.
 */
import java.io.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class implements the message conversion.
 */
public class Message {
	/**
     * This method converts a ROUTE UPDATE/LINKDOWN message to a byte array.
     */
	public static byte[] toByteArray(ConcurrentHashMap<Node, Distance> message){
		byte[] byteArray = null;
		
		try { 
			ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
			ObjectOutput out = new ObjectOutputStream(byteOut);;
			out.writeObject(message);
			byteArray = byteOut.toByteArray();
		}
		catch (Exception e) {
			System.exit(1);
		}
					
		return byteArray;		
	}
	
	/**
     * This method recovers a ROUTE UPDATE/LINKDOWN message from a byte array.
     */
	@SuppressWarnings("unchecked")
	public static ConcurrentHashMap<Node, Distance> toMessage(byte[] byteArray){
		ConcurrentHashMap<Node, Distance> message = null;		
		try {
			ByteArrayInputStream byteIn = new ByteArrayInputStream(byteArray);
			ObjectInputStream in = new ObjectInputStream(byteIn);
			message = (ConcurrentHashMap<Node, Distance>)in.readObject();
		}
		catch (Exception e) {
			System.exit(1);
		}
		
		return message;
	}
}
