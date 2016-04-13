/*
 * This application realizes a simple version of the distributed Bellman-Ford
 * algorithm. The algorithm is operated on a set of distributed client programs.
 * The clients perform the distributed distance computation and support a user 
 * interface, e.g., it allows the user to edit links to the neighbors and view
 * the routing table.
 */
import java.io.*;

/**
 * This class implements the node in the network.
 */
public class Node implements Serializable{
	/*
     * The IP address of the node.
     */
	public String ip;
	
	/*
     * The port number of the node.
     */
	public int port;
	
	/*
     * Serial version UID.
     */
	private static final long serialVersionUID = 1;
	
	public Node(){
		this.ip = "";
		this.port = 0;
	}
	
	public Node(String ip, int port){
		this.ip = ip;
		this.port = port;
	}
	
	@Override
	public String toString(){
		return (this.ip + ":" + this.port);
	}
	
	@Override
	public boolean equals(Object obj){
		Node node = (Node) obj;
		return (this.ip.equals(node.ip) && this.port == node.port);
	}
	
	@Override
	public int hashCode(){
		return this.ip.hashCode();
	}
}
