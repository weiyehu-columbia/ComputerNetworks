/*
 * This application realizes a simple version of the distributed Bellman-Ford
 * algorithm. The algorithm is operated on a set of distributed client programs.
 * The clients perform the distributed distance computation and support a user 
 * interface, e.g., it allows the user to edit links to the neighbors and view
 * the routing table.
 */
import java.io.Serializable;

/**
 * This class implements the neighbor of a client.
 */
public class Neighbor implements Serializable {
	/*
     * The distance information of the neighbor.
     */
	public Distance distance;
	
	/*
     * Whether the neighbor is linked down.
     */
	public boolean isLinkdown;
	
	/*
     * Serial version UID.
     */
	private static final long serialVersionUID = 3;
	
	public Neighbor(){
		this.distance = null;
		this.isLinkdown = false;
	}
	
	public Neighbor(Distance distance, boolean isLinkdown){
		this.distance = distance;
		this.isLinkdown = isLinkdown;
	}
	
	@Override
	public int hashCode(){
		return this.distance.hashCode();
	}
}
