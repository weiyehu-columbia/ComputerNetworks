/*
 * This application realizes a simple version of the distributed Bellman-Ford
 * algorithm. The algorithm is operated on a set of distributed client programs.
 * The clients perform the distributed distance computation and support a user 
 * interface, e.g., it allows the user to edit links to the neighbors and view
 * the routing table.
 */
import java.io.*;
import java.net.*;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Timer;
import java.util.TimerTask;

/**
 * This class implements the client running the distributed Bellman-Ford algorithm
 * and supporting a user interface.
 */
class Client {
	/*
     * The IP address of the client.
     */
	private String ip;
	
	/*
     * The port number of the client.
     */
	private int port;
	
	/*
     * The UDP socket which is used to send the client's distance vector to and
     * receive distances vector from the neighbors.
     */
	private DatagramSocket socket;
	
	/*
     * The timeout specifies the inter-transmission time of ROUTE UPDATE messages
     * in steady state. It is specified in seconds.
     */
	private int timeout;
	
	/*
     * The distance information of the neighbors.
     */
	private ConcurrentHashMap<Node, Neighbor> neighbors = new ConcurrentHashMap<Node, Neighbor>();
	
	/*
     * The distance vector of the client.
     */
	private ConcurrentHashMap<Node, Distance> distanceVector = new ConcurrentHashMap<Node, Distance>();
	
	/*
     * The distance vectors of neighbors.
     */
	private ConcurrentHashMap<Node, ConcurrentHashMap<Node, Distance>> neighborDistanceVectors = new ConcurrentHashMap<Node,ConcurrentHashMap<Node, Distance>>();
	
	/*
     * The timers of neighbors.
     */
	private ConcurrentHashMap<Node, Long> neighborTimers = new ConcurrentHashMap<Node, Long>();

	public static void main(String[] args) {
    	new Client(args);
    }
	
	public Client(String[] args) {
		try {
			if (args.length < 2 || (args.length - 2) % 3 != 0) {
				System.out.println("Error: incorrect input.");
				System.exit(1);
			}
			
			//Initialize the distance vectors.
			for (int i = 2; i < args.length; i += 3) {
				double weight = Double.parseDouble(args[i + 2]);
				String ip = InetAddress.getByName(args[i]).getHostAddress();
				int port = Integer.parseInt(args[i + 1]);
				Node dest = new Node(ip, port);
				Distance distance = new Distance(weight, dest);
				
				this.neighbors.put(dest, new Neighbor(distance, false));
				this.distanceVector.put(dest, distance);
			}
			
			routeUpdate();
			
			this.ip = InetAddress.getByName("localhost").getHostAddress();
			this.port = Integer.parseInt(args[0]);
			this.socket = new DatagramSocket(this.port);
			this.timeout = Integer.parseInt(args[1]) * 1000;
			
			Timer timer = new Timer();
			//Maintain a timer to deal with timeout.
			timer.schedule(new NeighborTimerTask(), 1000, 1000);
			//Maintain a timer to periodically send ROUTE UPDATE message to neighbors.
			timer.schedule(new SendMessageTimerTask(), this.timeout, this.timeout);
			
			//Create a new thread to send ROUTE UPDATE messages.
			ReceiveMessage receive = new ReceiveMessage();
			new Thread(receive).start();
			
			BufferedReader fromUser = new BufferedReader(new InputStreamReader(System.in));	
			String command = "";
			
			while(true) {
				try {
					command = fromUser.readLine().toUpperCase();
					
					if (command.contains("LINKDOWN")) {
						String[] neighbor = command.substring(9).trim().split(" ");
						String neighborIp = neighbor[0];
						int neighborPort = Integer.parseInt(neighbor[1]);
						linkdown(neighborIp, neighborPort);					
					}
					else if (command.contains("LINKUP")) {
						String[] neighbor = command.substring(7).trim().split(" ");
						String neighborIp = neighbor[0];
						int neighborPort = Integer.parseInt(neighbor[1]);
						linkup(neighborIp, neighborPort);					
					}
					else if (command.contains("SHOWRT")) {
						showrt();
					}
					else if (command.contains("CLOSE")) {
						close();
					}
					else {
						System.out.println("Error: incorrect command.");
					}
				}
				catch (Exception e) {
					System.err.println("Error: incorrect command.");
				}
			}
		}
		catch (Exception e) {
			System.err.println("Error: incorrect command.");
			System.exit(1);
		}
	}
	
	/**
     * This method supports the LINKDOWN command. This allows the user to destroy an
     * existing link, i.e., change the link cost to infinity to the mentioned neighbor.
     */
	private void linkdown(String ip, int port){
		try{
			Node neighbor = new Node(ip, port);
			if (!this.neighbors.containsKey(neighbor)){
				System.out.println("Error: this neighbor does not exist.");
			}
			else if (this.neighbors.get(neighbor).isLinkdown) {
				System.out.println("Error: this neighbor is already linked down.");
			}
			else {
				ConcurrentHashMap<Node, Distance> message = new ConcurrentHashMap<Node, Distance>();
				message.put(new Node(), new Distance());
				
				//Send a LINKDOWN message to the mentioned neighbor.
				byte[] byteArray = Message.toByteArray(message);
				InetAddress address = InetAddress.getByName(ip);
				DatagramPacket packet = new DatagramPacket(byteArray, byteArray.length, address, neighbor.port);
				this.socket.send(packet);
				
				Neighbor linkdownNeighbor = this.neighbors.get(neighbor);
				linkdownNeighbor.isLinkdown = true;
				this.neighbors.put(neighbor, linkdownNeighbor);
				this.neighborDistanceVectors.remove(neighbor);
				this.neighborTimers.remove(neighbor);
				this.distanceVector.put(neighbor, new Distance(Integer.MAX_VALUE, neighbor));
				
				System.out.println("The link to neighbor " + neighbor.toString() + " has been destroyed.");
				
				bellmanford();
			}					
		}
		catch (Exception e) {
			System.exit(1);
		}
	}
	
	/**
     * This method supports the LINKUP command. This allows the user to restore the
     * link to the mentioned neighbor to the original value after it was destroyed
     * by a LINKDOWN command.
     */
	private void linkup(String ip, int port){
		try{
			Node neighbor = new Node(ip, port);
			
			if (!this.neighbors.containsKey(neighbor)) {
				System.out.println("Error: this neighbor does not exist.");			
			}
			else if (!this.neighbors.get(neighbor).isLinkdown) {
				System.out.println("Error: this neighbor is active now.");				
			}
			else {
				Neighbor linkupNeighbor = this.neighbors.get(neighbor);
				linkupNeighbor.isLinkdown = false;
				this.neighbors.put(neighbor, linkupNeighbor);
				this.distanceVector.put(neighbor, this.neighbors.get(neighbor).distance);
				this.neighborTimers.put(neighbor, System.currentTimeMillis());
				
				System.out.println("The link to neighbor " + neighbor.toString() + " has been restored.");
			}
		}
		catch (Exception e) {
			System.exit(1);
		}
	}
	
	/**
     * This method supports the SHOWRT command. This allows the user to review the
     * current routing table of the client.
     */
	private void showrt() {
		System.out.println(new Date().toString());
		System.out.println("Distance vector list is:");
		synchronized(this.distanceVector) {
			for (Node node: this.distanceVector.keySet()) {
				if (this.distanceVector.get(node).cost < 1000) {
					System.out.print("Destination = " + node.toString() + ", ");
					System.out.print("Cost = " + this.distanceVector.get(node).cost + ", ");
					System.out.println("Next Hop = " + this.distanceVector.get(node).nextHop.toString());
				}
			}
		}
	}
	
	/**
     * This method supports the CLOSE command. This allows the user to shut down the
     * client. When a client has been closed, its distance will be set to infinity.
     */
	private void close() {
		this.socket.close();
		System.exit(0);
	}
	
	/**
     * This method sends ROUTE UPDATE messages to neighbors.
     */
	private void routeUpdate(){
		try {
			synchronized (this.neighbors) {
				for (Node neighbor: this.neighbors.keySet()){
					if (!this.neighbors.get(neighbor).isLinkdown) {
						byte[] byteArray = Message.toByteArray(this.distanceVector);
						InetAddress address = InetAddress.getByName(neighbor.ip);
						DatagramPacket packet = new DatagramPacket(byteArray, byteArray.length, address, neighbor.port);
						socket.send(packet);
					}
				}
			}
		}
		catch (Exception e) {
			
		}
	}
	
	/**
     * This method use the Bellman-Ford equation to update the client's distance vector.
     */
	private synchronized void bellmanford() {
		//Compute the client's distance vector.
		synchronized(this.distanceVector) {
			boolean isUpdate = false;
			
			for (Node node: this.distanceVector.keySet()){
				if (!node.equals(new Node(this.ip, this.port))) {
					double oldDistance = this.distanceVector.get(node).cost;
					double minDistance = Integer.MAX_VALUE;
					
					//Implement the Bellman-Ford equation: d(x, y) = min {c(x, v) + d(v, y)}.
					synchronized(this.neighbors) {
						for (Node neighbor: neighbors.keySet()){
							if (!this.neighbors.get(neighbor).isLinkdown) {
								double cxv = neighbors.get(neighbor).distance.cost;
								double dxy = 0;	
								
								if (neighbor.equals(node)){
									dxy = cxv;
								}
								else if (this.neighborDistanceVectors.containsKey(neighbor) && this.neighborDistanceVectors.get(neighbor).containsKey(node)) {
									dxy = cxv + this.neighborDistanceVectors.get(neighbor).get(node).cost;
								}
								else {
									dxy = Integer.MAX_VALUE;
								}	
								
								if (dxy < minDistance){
									minDistance = dxy;
									this.distanceVector.put(node, new Distance(minDistance, neighbor));
								}
							}
						}
						
						if (minDistance != oldDistance){
							isUpdate=true;
						}
					}
				}
			}
			
			if (isUpdate) {
				routeUpdate();
			}
		}
	}
	
	/**
	 * This class represents a timer task of the timer which checks whether a neighbor
	 * has a timeout. If so, remove it from the network.
	 */
	class NeighborTimerTask extends TimerTask {
		@Override
		public void run() {
			try {
				synchronized (neighborTimers){
					for (Node neighbor: neighborTimers.keySet()){
						if (!neighbors.get(neighbor).isLinkdown) {
							long elapsedTime = System.currentTimeMillis() - neighborTimers.get(neighbor);
							
							//Check timeout.
							if (elapsedTime > 3 * timeout){
								System.out.println("Neighbor " + neighbor.toString() + " timeout.");
								
								neighbors.remove(neighbor);
								neighborDistanceVectors.remove(neighbor);
								neighborTimers.remove(neighbor);
								distanceVector.put(neighbor, new Distance(Integer.MAX_VALUE, neighbor));
								
								bellmanford();
							}
						}
					}
				}
			}
			catch (Exception e) {
				System.exit(1);
			}
		}
	}
	
	/**
	 * This class represents a timer task of the timer which periodically send ROUTE
	 * UPDATE messages to neighbors.
	 */
	class SendMessageTimerTask extends TimerTask {
		@Override
		public void run() {
			routeUpdate();
		}
	}
	
	/**
	 * This class represents the thread which is used to receive and parse ROUTE UPDATE
	 * messages, LINKDOWN messages, and LINKUP messages.
	 */
	class ReceiveMessage implements Runnable {
		@Override
		public void run() {
			try {
				while(true){
					byte[] buffer = new byte[5000];
					DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
					socket.receive(packet);
					Node src = new Node(packet.getAddress().getHostAddress(), packet.getPort());
					byte[] data = packet.getData();
					ConcurrentHashMap<Node, Distance> message = Message.toMessage(data);
					
					//Parse the message.
					if (message.containsKey(new Node())){
						System.out.println("The link to neighbor " + src.toString() + " has been destroyed.");
						
						Neighbor linkdownNeighbor = neighbors.get(src);
						linkdownNeighbor.isLinkdown = true;
						neighbors.put(src, linkdownNeighbor);
						neighborDistanceVectors.remove(src);
						neighborTimers.remove(src);
						distanceVector.put(src, new Distance(Integer.MAX_VALUE, src));
					}
					else {
						if (!neighbors.containsKey(src)){
							double distance = message.get(new Node(ip, port)).cost;
							neighbors.put(src, new Neighbor(new Distance(distance, src), false));
							distanceVector.put(src, new Distance(distance, src));
						}
						else if (neighbors.get(src).isLinkdown) {
							Neighbor linkupNeighbor = neighbors.get(src);
							linkupNeighbor.isLinkdown = false;
							neighbors.put(src, linkupNeighbor);
							System.out.println("The link to neighbor " + src.toString() + " has been restored.");	
						}	
						
						neighborDistanceVectors.put(src, message);
						neighborTimers.put(src, System.currentTimeMillis());
						
						for (Node node: message.keySet()){
							if (!distanceVector.containsKey(node)) {
								distanceVector.put(node, new Distance(Integer.MAX_VALUE, src));
							}
						}
					}
					
					bellmanford();
				}
			}
			catch (Exception e) {
				System.exit(1);
			}
		}
	}
}
