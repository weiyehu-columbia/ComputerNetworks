/*
 * This application realizes a simple version of the distributed Bellman-Ford
 * algorithm. The algorithm is operated on a set of distributed client programs.
 * The clients perform the distributed distance computation and support a user 
 * interface, e.g., it allows the user to edit links to the neighbors and view
 * the routing table.
 */
import java.io.*;

/**
 * This class implements the distance information.
 */
public class Distance implements Serializable {
	/*
     * The cost (distance) to the destination.
     */
	public double cost;
	
	/*
     * The nest hop on the shortest path to the destination.
     */
	public Node nextHop;
	
	/*
     * Serial version UID.
     */
	private static final long serialVersionUID = 2;
	
	Distance(){
		this.cost = 0;
		this.nextHop = null;
	}
	
	Distance(double cost, Node nextHop){
		this.cost = cost;
		this.nextHop = nextHop;
	}
	
	@Override
	public int hashCode(){
		return this.nextHop.hashCode();
	}
}
