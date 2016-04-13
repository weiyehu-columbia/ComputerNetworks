Multi-user Chatroom

==========================================================================

1. Description

This application realizes a simple chat room. It implements all the functions required by Programming Homework 1 (Table 1). Also, it implements 3 optional functions which are not required, that is, offline messaging, log, wholasthr.

This application contains two programs: a server program (Server.java), a client program (Client.java). The client program is very simple, since it only needs to send messages to and receive messages from the server. All functions are implemented in the server program.

==========================================================================

2. Development Environment

OS: Ubuntu 14.04.1
Language: Java
JDK:1.7.0
IDE: Eclipse

==========================================================================

3. How to Run My Code

Step 1:

Enter the folder "wh2333_java", open a terminal here, use command "make" to build the application. If you fail to "make" the makefile, please
follow Step 2.

Step 2: (If you succeed in Step 1, please ignore Step 2 and go to Step 3)

Use command "javac Server.java" to compile the server program;
Use command "javac Client.java" to compile the client program;

Step 3:

Use command "java Server <server_port_no>" to invoke the server program. e.g. java Server 8000

Step 4:

Use command "java Client <server_IP_address> <server_port_no>" to invoke the client program. e.g. java Client 127.0.0.1 8000

==========================================================================

4. Sample Commands for Users

When you connect to the server, the server will ask you to input your username and password. If the username does not exist or is already logged in, the server will ask you to enter again and again, until you enter a right username. Then the server will give you 3 (variable FAILURE_NUM) chances to enter your password. If you failed, the server will block access only for the same username from the failed attempt IP address for 60 seconds (variable BLOCK_TIME). If you use the same username to log in from other IP addresses during the block time, you will still be able to log in.

After you have successfully logged in, you will see a welcome message. Then you can enter your command AT ANY TIME you want. Here are some examples:

whoelse

-- Displays name of other connected users

wholast 25.5

-- Displays name of those users connected within last 25.5 minutes.
-- If you enter a number larger than 60 or less than 0, the server will give you an error message.

broadcast message hello world

-- Broadcast message "hello world" to all connected users.

broadcast user columbia foobar message hello world

-- Broadcast message "hello world" to user columbia and foobar.
-- If a user is not online now, the server will inform you, then your message will be saved as an offline message to him/her.
-- if a user you entered does not exist, the server will inform you.

message columbia hello world

-- Send a private message "hello world" to user columbia.
-- If user columbia is not online now, the server will inform you, then your message will be saved as an offline message to him/her.
-- if the user you entered does not exist, the server will inform you.

logout

-- You log out, and the connection will be terminated.

If you keep inactive for over 30 minutes (variable TIME_OUT), the server will automatically "logout" you.

If you want to exit the program (either server program or client program), you can use "Ctrl + C" AT ANY TIME you want. When you do this, the terminal of all other running programs will display a relevant message.

If the server cannot recognize some command, an error message will be displayed on your terminal.

==========================================================================

5. Optional Functions

This application also implements 3 optional functions which are not
required by Programming Homework 1.

offline messaging

-- When you use command "broadcast" or "message", if the user you want to send message to is not online, the server will inform you, then your message will be saved as an offline message to him/her.
-- Once him/her log in, he/she will see the offline message displayed on the terminal.

log

-- After you run the server program, the server terminal will display every event happens in the chat room, including new connection from clients, wrong username, wrong password, a user is blocked, error message, login message, logout message, etc.

wholasthr

-- Displays name of those users connected within last 1 (variable LAST_HOUR)hour.

==========================================================================

6. Variables

FAILURE_NUM

-- Maximum number of consecutive failures that are allowed when a user inputs his password.

BLOCK_TIME

-- Number of seconds that the server will block access for the user from the failed attempt IP address.

TIME_OUT

-- Number of minutes of the time out that a client is inactive. If a client is inactive for more than this number of minutes, the server will automatically log this user out.

LAST_HOUR

-- Number of hours used in command "wholasthr"
