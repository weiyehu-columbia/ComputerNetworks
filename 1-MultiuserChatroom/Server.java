/*
 * This application realizes a simple chat room. It contains two programs: 
 * a server program (Server.java), a client program (Client.java).
 */
import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Calendar;
import java.text.SimpleDateFormat; 

/**
 * This class represents the server that listens for connections from clients.
 */
public class Server {	
	/*
     * Maximum number of consecutive failures that are allowed when a user
     * inputs his password.
     */
	private final int FAILURE_NUM = 3;
	
	/*
     * Number of seconds that the server will block access for the user from
     * the failed attempt IP address.
     */
	private final int BLOCK_TIME = 60;
	
	/*
     * Number of minutes of the time out that a client is inactive. If a client
     * is inactive for more than this number of minutes, the server will automatically
     * log this user out.
     */
	private final int TIME_OUT = 30;
	
	/*
     * Number of hours used in command "wholasthr". 
     */
	private final int LAST_HOUR = 1;
	
	
	/*
     * Mapping between the username and the password.
     */
	private HashMap<String, String> usernamePasswordMap = new HashMap<String, String>();
	
	/*
     * Set of blocked usernames.
     */
	private HashSet<String> blockedUsername = new HashSet<String>();
	
	/*
     * Mapping between the blocked IP address and the time when it began to be blocked.
     */
	private HashMap<String, Long> blockedIpAddress = new HashMap<String, Long>();
	
	/*
     * Mapping between the online user and its thread.
     */
	private HashMap<String, ClientThread> onlineClients = new HashMap<String, ClientThread>();
	
	/*
     * Mapping between the offline user and the time when he logged out.
     */
	private HashMap<String, Long> hasLoggedOut = new HashMap<String, Long>();
	
	/*
     * Mapping between the offline user and the offline message he has received.
     */
	private HashMap<String, ArrayList<String>> offlineMessage = new HashMap<String, ArrayList<String>>();
		
	public static void main(String[] args) {
		new Server(args);
	}
	
	public Server(String[] args) {		
		try {
			File user_pass = new File("user_pass.txt");			
			Scanner input = new Scanner(user_pass);
			
			//Read a list of username-password combinations from "user_pass.txt".
			while (input.hasNext()) {
				String username = input.next();
				String password = input.next();
				
				usernamePasswordMap.put(username, password);
				
				offlineMessage.put(username, new ArrayList<String>());
			}
			
			input.close();
			
			int serverPort = Integer.parseInt(args[0]);			
			ServerSocket serverSocket = new ServerSocket(serverPort);
			
			System.out.println("This is the server program. If you want to exit, please input \"Ctrl + C\".");			
			System.out.println("Server stared at " + new Date() + ".");
			
			//Listen for new connections from clients.
			while (true) {
				Socket socket = serverSocket.accept();
				
				ClientThread client = new ClientThread(socket);	
				
				//Create a new thread for each connection.
				new Thread(client).start();
			}			
		}
		catch (Exception e) {
			System.out.println("The server is closed.");
		}
	}
	
	/**
     * This method displays the log on the server's window.
     */
	private void log(String log) {
		try {
			System.out.println(log);
		}
		catch (Exception e) {
			System.out.println("The server is closed.");
		}
	}
	
	/**
	 * This class represents each connection form clients.
	 */
	class ClientThread implements Runnable {		
		/*
	     * The current connected socket.
	     */
		private Socket socket;
		
		/*
	     * Data input stream from the client.
	     */
		private BufferedReader fromClient;
		
		/*
	     * Data output stream to the client.
	     */
		private DataOutputStream toClient;
		
		/*
	     * IP address of the client.
	     */
		private String clientIpAddress;
		
		/*
	     * Username of the client.
	     */
		private String username;
		
		/*
	     * Command from the client.
	     */
		private String commandFromUser;
		
		/*
	     * An instance of calendar.
	     */
		private Calendar clientTime = Calendar.getInstance();
		
		public ClientThread(Socket socket) {
			this.socket = socket;
		}
		
		@Override
		public void run() {
			try {
				clientTime.set(Calendar.MINUTE, TIME_OUT); 
				clientTime.set(Calendar.SECOND, 0);
		        
				InetAddress inetAddress = socket.getInetAddress();
				clientIpAddress = inetAddress.getHostAddress();
				
				fromClient = new BufferedReader(new InputStreamReader(socket.getInputStream()));			
				toClient = new DataOutputStream(socket.getOutputStream());
				
				log("Client " + clientIpAddress + " has connected to the server.");
				
				//Prompt the client to log in.
				logIn();
				
				while (true) {
					//Create a new timer to record the time from the client's last command.
					Timer clientTimer = new Timer();					
					clientTimer.schedule(new ClientTimerTask(), new Date(), 1000);
					
					commandFromUser = fromClient.readLine();
					
					//Close the timer after the client inputs his command.
					clientTimer.cancel();
										
					String[] command = commandFromUser.split(" ");
					
					//Check the command from the client.
					switch (command[0]) {
					    case "whoelse":
					    	whoElse();
					    	break;
					    	
					    case "wholasthr":
					    	whoLastHour();
					    	break;
					    
					    case "wholast":
					    	whoLast(command);
					    	break;
					    
					    case "broadcast":
					    	if (command[1].equals("message")) {
					    		broadcastToAll(command);
					    	}
					    	else {
					    		broadcastToList(command);
					    	}
					    	break;
					    
					    case "message":
					    	privateMessage(command);
					    	break;
					    
					    case "logout":
					    	logOut();
					    	break;
					    
					    case "":
					    	break;
					    
					    default:
					    	errorCommand();
					    	break;
					}
				}	
			}
			catch (Exception e) {
				log("The connection from client " + clientIpAddress + " is terminated.");
				
				onlineClients.remove(username);
			}
		}
		
		/**
	     * This method prompts the user to input his username and password
	     * before log in. If the password is incorrect, the server should 
	     * ask the user to try again until there are FAILURE_NUM consecutive
	     * failures. In this case, the server will drop this connection and
	     * block access only for this user from the failed attempt IP address
	     * for BLOCK_TIME seconds. 
	     */
		private void logIn() {		
			try {
				toClient.writeBytes("You need to log in before you use this chat room application." + '\n');
				
				String password;
						
				while (true) {
					toClient.writeBytes("Username: " + '\n');
					
					username = fromClient.readLine();
					
					//Check whether the username exists.
					if (usernamePasswordMap.containsKey(username)) {
						//Check whether the user has already logged in.
						if (onlineClients.containsKey(username)) {
							toClient.writeBytes("The username you entered is already logged in, please enter another username." + '\n');
						}
						//Check whether the user and its IP address is blocked.
						else if (blockedUsername.contains(username) && blockedIpAddress.containsKey(clientIpAddress)) {
							long blockStartTime = blockedIpAddress.get(clientIpAddress);							
							long remainTime = blockStartTime - new Date().getTime() / 1000 + BLOCK_TIME;
								
							if (remainTime <= 0) {
								blockedIpAddress.remove(clientIpAddress);
							}
							else {
								toClient.writeBytes("Access from IP " + clientIpAddress + " has been bolcked, please wait for " + remainTime + " seconds." + '\n');							
								
								Thread.sleep(remainTime * 1000);
							}
						}
						else {
							break;
						}					
					}
					else {
						toClient.writeBytes("The username you entered does not exist, please enter your username again." + '\n');
					}
				}
							
				//Give the user FAILURE_NUM chances to input his password.
				for (int i = 0; i < FAILURE_NUM; i++) {
					toClient.writeBytes("Password: " + '\n');
					
					password = fromClient.readLine();
					
					//Check whether the password matches the username.
					if (usernamePasswordMap.get(username).equals(password)) {
						onlineClients.put(username, this);
						
						log("User " + username + " (IP: " + clientIpAddress + ") has logged in.");
						
						toClient.writeBytes("You've logged in successfully. Welcome to the chat room!" + '\n');
						toClient.writeBytes("Please enter your command AT ANY TIME you want." + '\n');
						
						for (String m: offlineMessage.get(username)) {
							toClient.writeBytes(m + '\n');
						}
						
						offlineMessage.remove(username);
						
						break;
					}
					
					if (i < FAILURE_NUM - 1) {
						toClient.writeBytes("The password you entered is incorrect, please enter your password again." + '\n');
					}
					//Block block access for this user from the failed attempt IP address for BLOCK_TIME seconds.
					else {
						long blockStartTime = new Date().getTime() / 1000;
						
						blockedUsername.add(username);
						
						blockedIpAddress.put(clientIpAddress, blockStartTime);
						
						log("Access from client " + clientIpAddress + " will be bolcked for " + BLOCK_TIME + " seconds.");
						
						toClient.writeBytes("Since there are " + FAILURE_NUM + " consecutive failures, access from IP " +
						                    clientIpAddress + " will be bolcked for " + BLOCK_TIME + " seconds. " + 
								            "Please wait." + '\n');
						
						Thread.sleep(BLOCK_TIME * 1000);
						
						logIn();
					}				
				}			
			}
			catch (Exception e) {
				log("The connection from client " + clientIpAddress + " is terminated.");
			}
		}
		
		/**
	     * This method displays name of other connected users.
	     */
		private void whoElse() {
			String whoElse = "";
			
			try {
				log("User " + username + "'s command: " + commandFromUser);
				
				Iterator<Entry<String, ClientThread>> it = onlineClients.entrySet().iterator();
				
				while (it.hasNext()) {
					Map.Entry<String, ClientThread> client = (Map.Entry<String, ClientThread>)it.next();
					
					if (!client.getKey().equals(username)) {
						whoElse += client.getKey() + ", ";
					}	
				}
							
				if (whoElse.equals("")) {
					toClient.writeBytes("Nobody is online now except you." + '\n');
				}
				else {
					whoElse = whoElse.substring(0, whoElse.length() - 2);
					
					toClient.writeBytes("Other connected users: " + whoElse + "." + '\n');
				}				
			}
			catch (Exception e) {
				log("The connection from client " + clientIpAddress + " is terminated.");
			}
		}
		
		/**
	     * This method displays name of those users connected within the last LAST_HOUR hours.
	     */
		private void whoLastHour() {
			try {
				log("User " + username + "'s command: " + commandFromUser);
				
				String whoLastHour = "";				
				HashSet<String> set = new HashSet<String>();
				
				Iterator<Entry<String, ClientThread>> it1 = onlineClients.entrySet().iterator();
				
				//Add name of users who is currently online.
				while (it1.hasNext()) {
					Map.Entry<String, ClientThread> client = (Map.Entry<String, ClientThread>)it1.next();
					
					if (!client.getKey().equals(username)) {
						whoLastHour += client.getKey() + ", ";
						
						set.add(client.getKey());
					}	
				}
				
				Iterator<Entry<String, Long>> it2 = hasLoggedOut.entrySet().iterator();
				
				//Add name of users who is logged out but has logged in within the last LAST_HOUR hours.
				while (it2.hasNext()) {
					Map.Entry<String, Long> client = (Map.Entry<String, Long>)it2.next();
					
					if (!client.getKey().equals(username) && !set.contains(client.getKey())) {
						long logOutTime = new Date().getTime() / 1000 - client.getValue();
						
						if (logOutTime < LAST_HOUR * 3600) {
								whoLastHour += client.getKey() + ", ";
						}
					}	
				}
				
				if (whoLastHour.equals("")) {
					toClient.writeBytes("Nobody connected within the last " + LAST_HOUR + " hour(s) except you." + '\n');
				}
				else {
					whoLastHour = whoLastHour.substring(0, whoLastHour.length() - 2);
					
					toClient.writeBytes("Other users that connected within the last " + LAST_HOUR + " hour(s): " + whoLastHour + "." + '\n');
				}
			}
			catch (Exception e) {
				log("The connection from client " + clientIpAddress + " is terminated.");
			}
		}
		
		/**
	     * This method displays name of those users connected within the last number of minutes.
	     */
		private void whoLast(String[] command) {
			try {
				if (command.length < 2) {
					errorCommand();
					return;
				}
				
				log("User " + username + "'s command: " + commandFromUser);
				
				if (Double.parseDouble(command[1]) >= 60) {
					toClient.writeBytes("The time in minutes should be between 0~60, please enter your command again." + '\n');
					return;
				}
				
				double minutes = Double.parseDouble(command[1]);
				String whoLast = "";				
				HashSet<String> set = new HashSet<String>();
				
				Iterator<Entry<String, ClientThread>> it1 = onlineClients.entrySet().iterator();
				
				//Add name of users who is currently online.
				while (it1.hasNext()) {
					Map.Entry<String, ClientThread> client = (Map.Entry<String, ClientThread>)it1.next();
					
					if (!client.getKey().equals(username)) {
						whoLast += client.getKey() + ", ";
						
						set.add(client.getKey());
					}
				}
				
				Iterator<Entry<String, Long>> it2 = hasLoggedOut.entrySet().iterator();
				
				//Add name of users who is logged out but has logged in within the last number of minutes.
				while (it2.hasNext()) {
					Map.Entry<String, Long> client = (Map.Entry<String, Long>)it2.next();
					
					if (!client.getKey().equals(username) && !set.contains(client.getKey())) {
						long logOutTime = new Date().getTime() / 1000 - client.getValue();
						
						if (logOutTime < minutes * 60) {
								whoLast += client.getKey() + ", ";
						}
					}	
				}
				
				if (whoLast.equals("")) {
					toClient.writeBytes("Nobody connected within the last " + minutes + " minute(s) except you." + '\n');
				}
				else {
					whoLast = whoLast.substring(0, whoLast.length() - 2);
					
					toClient.writeBytes("Other users that connected within the last " + minutes + " minute(s): " + whoLast + "." + '\n');
				}
			}
			catch (NumberFormatException nfe) {
				log("Error: user " + username + "'s command cannot be recognized.");
				
				try {
				    toClient.writeBytes("Your input after the \"wholast\" command is not a number, please enter your command again." + '\n');
				}
				catch (Exception e) {
					log("The connection from client " + clientIpAddress + " is terminated.");
				}
			}
			catch (Exception e) {
				log("The connection from client " + clientIpAddress + " is terminated.");
			}
		}
		
		/**
	     * This method broadcasts message to all connected users.
	     */
		private void broadcastToAll(String[] command) {
			try {
				if (command.length < 3 || !command[1].equals("message")) {
					errorCommand();
					return;
				}
				
				log("User " + username + "'s command: " + commandFromUser);
				
				String message = "";
				
				for (int i = 2; i < command.length; i++) {
					message += command[i] + " ";
				}
				
				message = username + ": " + message.trim();			
				String whoElse = "";
				
				Iterator<Entry<String, ClientThread>> it = onlineClients.entrySet().iterator();
				
				while (it.hasNext()) {
					Map.Entry<String, ClientThread> client = (Map.Entry<String, ClientThread>)it.next();
					
					if (!client.getKey().equals(username)) {
						client.getValue().toClient.writeBytes(message + '\n');
						
						whoElse += client.getKey() + ", ";
					}
				}
				
				if (whoElse.equals("")) {
					toClient.writeBytes("Nobody is online now except you." + '\n');
				}
				else {
					whoElse = whoElse.substring(0, whoElse.length() - 2);
					
					toClient.writeBytes("Your message has been broadcast to all online users: " + whoElse + "." + '\n');
				}
			}
			catch (Exception e) {
				log("The connection from client " + clientIpAddress + " is terminated.");
			}
		}
		
		/**
	     * This method broadcasts message to the list of users. If the user is
	     * not online, the message will be saved as an offline message.
	     */
		private void broadcastToList(String[] command) {
			try {
				if (command.length < 3 || !command[1].equals("user")) {
					errorCommand();
					return;
				}
				
				log("User " + username + "'s command: " + commandFromUser);
				
				ArrayList<String> users = new ArrayList<String>();				
				int messageIndex = command.length;
				
				for (int i = 2; i < command.length; i++) {
					if (command[i].equals("message")) {
						messageIndex = i;
						break;
					}
					
					if (!usernamePasswordMap.containsKey(command[i])) {
						toClient.writeBytes("Error: user " + command[i] + " does not exist, please enter your command again." + '\n');					
						return;
					}
					
					users.add(command[i]);
				}
				
				if (messageIndex >= command.length - 1) {
					errorCommand();
					return;
				}
				
				String message = "";
				
				for (int i = messageIndex + 1; i < command.length; i++) {
					message += command[i] + " ";
				}
				
				message = username + ": " + message.trim();				
				String whoElse = "";				
				String notOnline = "";
				
				for (int i = 0; i < users.size(); i++) {
					//Send message to the user directly if he is online.
					if (onlineClients.containsKey(users.get(i))) {
						onlineClients.get(users.get(i)).toClient.writeBytes(message + '\n');
						
						whoElse += users.get(i) + ", ";
					}
					//Save message as an offline message if the user is not online.
					else {
						offlineMessage.get(users.get(i)).add(message);
						
						notOnline += users.get(i) + ", ";
					}
				}
				
				if (!notOnline.equals("")) {
					notOnline = notOnline.substring(0, notOnline.length() - 2);
					
					toClient.writeBytes("These users are not online: " + notOnline + ". Your message sent to these users will be saved as an offline message.");
				}
				
				if (whoElse.equals("")) {
					toClient.writeBytes("" + '\n');
				}
				else {
					whoElse = whoElse.substring(0, whoElse.length() - 2);
					
					toClient.writeBytes("Your message has been broadcast to these users: " + whoElse + "." + '\n');
				}				
			}
			catch (Exception e) {
				log("The connection from client " + clientIpAddress + " is terminated.");
			}
		}
		
		/**
	     * This method sends private message to a certain user. If the user is
	     * not online, the message will be saved as an offline message.
	     */
		private void privateMessage(String[] command) {
			try {
				if (command.length < 3) {
					errorCommand();
					return;
				}
				
				log("User " + username + "'s command: " + commandFromUser);
				
				String user = command[1];
				
				if (!usernamePasswordMap.containsKey(user)) {
					toClient.writeBytes("Error: user " + user + " does not exist, please enter your command again." + '\n');
					return;
				}
				
				String message = "";
				
				for (int i = 2; i < command.length; i++) {
					message += command[i] + " ";
				}
				
				message = username + ": "  + message.trim();
				
				//Send message to the user directly if he is online.
				if (onlineClients.containsKey(user)) {
					onlineClients.get(user).toClient.writeBytes(message + '\n');
					
					toClient.writeBytes("Your message has been sent to user " + user + " successfully." + '\n');
				}
				//Save message as an offline message if the user is not online.
				else {
					offlineMessage.get(user).add(message);
					
					toClient.writeBytes("User " + user + " is not online. Your message will be saved as an offline message." + '\n');
				}
			}
			catch (Exception e) {
				log("The connection from client " + clientIpAddress + " is terminated.");
			}
		}
		
		/**
	     * This method logs out the user.
	     */
		private void logOut() {
			try {
				log("User " + username + "'s command: " + commandFromUser);
				
				toClient.writeBytes("You're going to log out." + '\n');
				
				long logOutTime = new Date().getTime() / 1000;
				
				hasLoggedOut.put(username, logOutTime);
				
				offlineMessage.put(username, new ArrayList<String>());
				
				onlineClients.remove(username);
				
				socket.close();
				
				log("User " + username + " has logged out.");
			}
			catch (Exception e) {
				log("The connection from client " + clientIpAddress + " is terminated.");
			}
		}
		
		/**
	     * This method displays an error message when a command cannot be recognized.
	     */
		private void errorCommand() {
			try {
				log("Error: user " + username + "'s command cannot be recognized.");
				
				toClient.writeBytes("Error: your command cannot be recognized, please enter your command again." + '\n');
			}
			catch (Exception e) {
				log("The connection from client " + clientIpAddress + " is terminated.");
			}
		}
		
		/**
		 * This class represents the timer task of the timer.
		 */
		class ClientTimerTask extends TimerTask {
			@Override
			public void run() {
				try {
					clientTime.add(Calendar.SECOND, -1); 
					
					SimpleDateFormat sdf = new SimpleDateFormat("mmm:ss");
		            String strTime = sdf.format(clientTime.getTime()); 

		            //Check whether TIME_OUT minutes has passed.
		            if ("000:00".equals(strTime)) { 
		            	log("User " + username + " has been inactive for " + TIME_OUT + " minutes, he is automatically logged out.");
						
		            	toClient.writeBytes("Since you've been inactive for " + TIME_OUT + " minutes, the server automatically logged you out." + '\n');
		            	
		            	toClient.writeBytes("You're going to log out." + '\n');
						
						long logOutTime = new Date().getTime() / 1000;
						
						hasLoggedOut.put(username, logOutTime);
						
						offlineMessage.put(username, new ArrayList<String>());
						
						onlineClients.remove(username);
						
						log("User " + username + " has logged out.");
						
						socket.close();

		                this.cancel(); 
		            }
				}
				catch (Exception e) {
					InetAddress inetAddress = socket.getInetAddress();
					String clientIpAddress = inetAddress.getHostAddress();
					
					log("The connection from client " + clientIpAddress + " is terminated.");
				}
			}
		}
	}	
}
