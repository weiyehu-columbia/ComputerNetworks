/*
 * This application realizes a simple chat room. It contains two programs: 
 * a server program (Server.java), a client program (Client.java).
 */
import java.io.*;
import java.net.*;

/**
 * This class represents the client that will connect to the server.
 */
public class Client {
	/*
     * Whether this client is connected to the server.
     */
	private boolean isConnected;
	
	public static void main(String[] args) {
		new Client(args);
	}
	
	public Client(String[] args) {
		String serverIpAddress = args[0];
		int serverPort = Integer.parseInt(args[1]);
		
		try {
			System.out.println("This is the client program. If you want to exit, please input \"Ctrl + C\".");
			
			Socket clientSocket = new Socket(serverIpAddress, serverPort);				
			BufferedReader fromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			
			isConnected = true;
			
			while (true) {
				String messageFromServer = "";				
				SendMessageThread sendMessage = new SendMessageThread(clientSocket);			
				BufferedReader fromUser = new BufferedReader(new InputStreamReader(System.in));				
				DataOutputStream toServer = new DataOutputStream(clientSocket.getOutputStream());
				
				while (true) {
					messageFromServer = fromServer.readLine();
					
					//The server prompts the client to input his username and password before log in.
					if (messageFromServer.equals("Username: ") || messageFromServer.equals("Password: ") ) {
						System.out.print(messageFromServer);
						toServer.writeBytes(fromUser.readLine() + '\n');
						break;
					}
					//The server informs the client he is going to log out.
					else if (messageFromServer.equals("You're going to log out.")) {
						isConnected = false;
						break;
					}
					//The server informs the client he has logged in successfully.
					else if (messageFromServer.equals("You've logged in successfully. Welcome to the chat room!")) {
						System.out.println(messageFromServer);
						
						//Create a new thread in order to let the client input his command.
						new Thread(sendMessage).start();
					}
					//Receive usual message from the server.
					else {
						System.out.println(messageFromServer);
					}
				}
				
				if (!isConnected) {
					System.out.println("You've logged out successfully.");
					break;
				}				
			}
						
			clientSocket.close();
		}
		catch (Exception e) {
			System.out.println("The server is closed. Connection failed.");
		}		
	}
	
	/**
	 * This class implements the thread which is used for sending message to the server.
	 */
	class SendMessageThread implements Runnable {
		private Socket clientSocket;
		
		public SendMessageThread(Socket clientSocket) {
			this.clientSocket = clientSocket;
		}
		
		@Override
		public void run() {			
			try {
				BufferedReader fromUser = new BufferedReader(new InputStreamReader(System.in));
				DataOutputStream toServer = new DataOutputStream(clientSocket.getOutputStream());
				
				while (true) {
					//Let the client input his command. 
					toServer.writeBytes(fromUser.readLine() + '\n');					
				}			
			}
			catch (Exception e) {
				System.out.println("This client is closed. Connection failed.");
			}
		}
	}
}
