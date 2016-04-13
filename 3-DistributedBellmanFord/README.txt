Distributed Bellman-Ford

==========================================================================

1. Description

This application realizes a simple version of the distributed Bellman­Ford algorithm. The algorithm is operated on a set of distributed client programs. The clients perform the distributed distance computation and support a user interface.

This application contains one programs: a client program (Client.java) which relys on four classes: Node.java, Distance.java, Neighbor.java, Message.java.

==========================================================================

2. Development Environment

OS: Ubuntu 14.04.1
Language: Java
JDK:1.7.0
IDE: Eclipse

==========================================================================

3. How to Run My Code

Step 1:

Open a terminal in the folder "wh2333_java", use command "make" to build the application. If you fail to "make" the makefile, please follow Step 2.

Step 2: (If you succeed in Step 1, please ignore Step 2 and go to Step 3)

Use command "javac Node.java" to compile the Node class;
Use command "javac Distance.java" to compile the Distance class;
Use command "javac Neighbor.java" to compile the Neighbor class;
Use command "javac Message.java" to compile the Message class;
Use command "javac Client.java" to compile the client program;

Step 3:

Use command "java Client <local_port> <timeout> <IP_1> <port_1> <weight_1> <IP_2> <port_2> <weight_2> ..." to invoke the client program. e.g. java Client 4115 3 128.59.196.2 4116 5.0 128.59.196.2 4118 30.0

Step 4:

Use the command format described in Step 3 to invoke different clients, and thus create the network topology.

==========================================================================

4. Sample Commands for Users

LINKDOWN 128.59.196.2 4116

-- Destroy the link to neighbor 128.59.196.2:4116.
-- If the node is not a neighbor, an error message will be displayed.

LINKUP 128.59.196.2 4116

-- Restore the link to neighbor 128.59.196.2:4116.
-- If the node is not a neighbor, or it is already active, an error message will be displayed.

SHOWRT

-- Display the current routing table of the client.

CLOSE

-- Shut down the client, and exit the program.
-- When a client has been closed, its distance will be set to infinity.

==========================================================================

5. Message Description

ROUTE UPDATE message:

-- Once the client updates its distance vector, it will send ROUTE UPDATE messages to its neighbors. It converts its new distance vector to a byte array and use the UDP socket to send it to neighborsis.
-- Each time the client receives a ROUTE UPDATE message, it recomputes its distance vector immediately. If it finds its distance vector is updated during the recomputaton, then it sends his updated distance vector to its neighbors.
-- If a neighbor receives the ROUTE UPDATE message and finds the sender of this message is contained in its linked down neighbors set, then it knows the link from this source node is restored by the LINKUP command.
-- ROUTE UPDATE message format: <Node, Distance>, where Node = <IP, Port>, Distance = <Cost, Next Hop>. If a node is dead, that is, a timeout occurs, the cost to this node will be set to infinity.

LINKDOWN message:

-- if a link to a neighbor is destroyed by the LINKDOWN command, we create an empty message as a LINKDOWN message. When the neighbor receive a message, it checks whether the message is empty. If so, then this message is a LINKDOWN message and thus this neighbor knows that the link from the source node of this message is destroyed. Otherwise, this message is a ROUTE UPDATE message.
-- LINKDOWN message format: <Node, Distance>, where Node = <"", 0>, Distance = <0, null>.
