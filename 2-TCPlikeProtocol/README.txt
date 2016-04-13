TCP-like Protocol

==========================================================================

1. Description

This application realizes a simple TCP-like transport layer protocol. It provides reliable, in order delivery of a stream of bytes. It can recover from in-network packet loss, packet corruption, packet duplication and packet reordering. Also, it is able to cope with dynamic network delays.

This application contains two programs: a sender program (Sender.java), a receiver program (Receiver.java). The sender program is more complicated, since it needs to implement most of the reliable transmission mechanisms. Both the sender and the receiver maintains a log file.

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

Use command "javac Sender.java" to compile the sender program;
Use command "javac Receiver.java" to compile the receiver program;

Step 3:

Use command "java Receiver <file_name> <listening_port> <sender_IP> <sender_port> <logfile_name>" to invoke the sender program. e.g. java Receiver receiverfile.txt 800 127.0.0.1 801 receiverlogfile.txt

Step 4:

Use command "java Sender <file_name> <remote_IP> <remote_port> <ACK_port> <logfile_name> <window_size>" to invoke the sender program. e.g. java Sender senderfile.txt 127.0.0.1 800 801 senderlogfile.txt 1152

==========================================================================

4. TCP-like Segment Structure

Packet Size: (20 + MSS) bytes 
Header Size: 20 bytes
Payload Size: MSS bytes (MSS default value = 576)

byte[0] - byte[1]: 16-bit source port number field

byte[2] - byte[3]: 16-bit destination port number field

byte[4] - byte[7]: 32-bit sequence number field

byte[8] - byte[11]: 32-bit ACK number field

byte[12]: 4-bit header length field + 4-bit unused space

byte[13]: 2-bit unused space + 6-bit flag field

byte[14] - byte[15]: 16-bit window size field

byte[16] - byte[17]: 16-bit checksum field

byte[18] - byte[19]: 16-bit urgent data pointer field

byte[20] - byte[20 + MSS - 1]: MSS bytes payload

Note:

-- All fields above are implemented in this application, and only the urgent data pointer field is not used during the transmission.
-- The header length is 20 bytes (= 5 * 32 bits), so the first four bits of byte[12] is 0101 (= 5), while other unused bits are 0. Therefore byte[12] is 01010000 (= 80).
-- Only ACK and FIN are used here. If ACK = 1, FIN = 0, then byte[13] = 16; if ACK = 1, FIN = 1, then byte[13] = 17.
-- The default value of the window size field is 1.
-- The checksum is computed over the header and the data.
-- The default value of the urgent data pointer field is 0.

==========================================================================

5. Realiable Transmission Mechanism

This application can recover from packet loss, packet corruption, packet duplication and packet reordering.

Step 1:

The sender convert the file to a byte array, then convert the byte array to a sequence of packets. For each packet, the sender attaches a 20-byte header to it, and sets the value of all fields (e.g. calculates the checksum over the whole packet). 

Step 2:

The sender sends the first packet (sequence number = 0) to the reiceiver.

Step 3:

The receiver will acts as one of the following ways:

-- If packet loss occurs, the receiver does nothing.
-- If the receiver receives a packet, then it extracts its sequence number and calculates its checksum. If the sequence number is not equal to the expected sequence number (packet duplication or packet reordering), or the checksum is not equal to the checksum field (packet corruption), then the receiver drops this packet.
-- If the receiver receives a packet and finds its sequence number is the expected one and its checksum is also correct, then the receiver will send an ACK (with the ACK number and flags) to the sender, writes the data to the file, and increments the expected sequence number.
-- If the receiver receives a correct packet and finds its FIN value is 1, then it sends an ACK with FIN = 1 and terminates the transmission.

Step 4:

The sender will acts as one of the following ways:

-- If the sender receives am ACK form the receiver, and finds its ACK number is equal to the sequence number of the packet it recently sent, then it calculate the estimated RTT and timeout, and transmits the next packet.
-- If the sender receives am ACK form the receiver, and finds its ACK number is not equal to the sequence number of the packet it recently sent, then it retransmits the packet.
-- If a timeout does occur, the sender retransmits the packet.

Note:

-- When the transmission is terminated, the transmission information will be printed on the sender side.
-- When the sender or the receiver sends or receives a packet, it will log the header of this packet to its log file.
-- The initial value of timeout, sample RTT, and estimated RTT is 1000 ms. The initial value of deviate RTT is 0.
-- The estimated RTT is calculated in this way: estimated RTT = 0.875 * estimated RTT + 0.125 * sample RTT.
-- The deviate RTT is calculated in this way: 0.75 * deviate RTT + 0.25 * |sample RTT - estimated RTT|
-- The timeout is calculated in this way: timeout = estimated RTT + 4 * deviate RTT

==========================================================================

6. Variables

MSS

-- The maximum segment size. Its default value is 576 bytes.
