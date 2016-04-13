/*
 * This application realizes a simple TCP-like transport layer protocol. It provides
 * reliable, in order delivery of a stream of bytes. It can recover from in-network
 * packet loss, packet corruption, packet duplication and packet reordering and is
 * able to cope with dynamic network delays.
 */
import java.io.*;
import java.nio.*;
import java.net.*;
import java.util.Date;

/**
 * This class implements the receiver which receives TCP-like packets from the sender
 * and then sends correspondent ACK to the sender. If it receives a corrupted packet,
 * it drops the packet.
 */
public class Receiver {
	/*
     * The name of the received file.
     */
	private String fileName;
	
	/*
     * The name of the log file.
     */
	private String logFileName;
	
	/*
     * Source IP address of the sender.
     */
	private String srcIp;
	
	/*
     * Source port number of the sender.
     */
	private short srcPort;
	
	/*
     * ACK port number of the sender.
     */
	private short ackPort;
	
	/*
     * Destination IP address of the receiver.
     */
	private String destIp;
	
	/*
     * Destination port number of the receiver.
     */
	private short destPort;
	
	/*
     * The size of the receiver window.
     */
	private short windowSize;
	
	/*
     * The sequence number of a packet.
     */
	private int seqNum;
	
	/*
     * The expected sequence number of a packet.
     */
	private int expectedSeqNum;
	
	/*
     * The ACK number of a packet.
     */
	private int ackNum;
	
	/*
     * The flag field of a packet.
     */
	private byte flags;
	
	/*
     * The checksum of a packet, which is computed over the header and data.
     */
	private short checksum;
	
	/*
     * The maximum segment size. Here we use the default value 576.
     */
    private final int MSS = 576;
    
    /*
     * The TCP socket which is used to send the ACK packet to the sender.
     */
    private Socket sendSocket;
       
    /*
     * The UDP socket which is used to listen to calls from the sender (proxy).
     */
    private DatagramSocket listeningSocket;
    
    /*
     * The ACK packet received from the receiver.
     */
    private String ack;

    public static void main(String[] args) {
    	new Receiver(args);
    }

    public Receiver(String args[]) {
        try {
        	this.fileName = args[0];
        	this.destPort = Short.parseShort(args[1]);
        	this.srcIp = args[2];
        	this.ackPort = Short.parseShort(args[3]);
        	this.logFileName = args[4];
            this.listeningSocket = new DatagramSocket(this.destPort);
            this.destIp = this.listeningSocket.getLocalAddress().toString();

            File logFile = new File(this.logFileName);
            PrintWriter logWriter = null;

            if (this.logFileName.equals("stdout")) {
            	logWriter = new PrintWriter(System.out, true);
            }
            else {
            	logWriter = new PrintWriter(new BufferedWriter(new FileWriter(logFile, true)), true);
            }

            while (true) {
            	//Receive a packet from the sender.
                byte[] pkt = new byte[20 + MSS];
                DatagramPacket datagramPkt = new DatagramPacket(pkt, 20 + MSS);
                this.listeningSocket.receive(datagramPkt);
                pkt = datagramPkt.getData();
                getHeaderInfo(pkt);
                toLogFile(logWriter);

                //If the packet is the expected one and is not corrupted.
                if (this.seqNum == this.expectedSeqNum && getChecksum(pkt) == this.checksum) {
                    //Get the file data from the packet.
                	this.expectedSeqNum++;
                	byte[] pktData = new byte[MSS];
                	System.arraycopy(pkt, 20, pktData, 0, MSS);
                    FileOutputStream fileOutputStream = new FileOutputStream(this.fileName, true);
                    fileOutputStream.write(pktData);
                    fileOutputStream.close();

                    //Send ACK to the sender.
                    this.sendSocket = new Socket(this.srcIp, this.ackPort);
                    DataOutputStream ackWriter = new DataOutputStream(sendSocket.getOutputStream());
                    toLogFile(logWriter, 1);
                    ackWriter.writeBytes(this.ack + '\n');

                    //If FIN = 17, terminate the transmission.
                    if (this.flags == 17) {
                        break;
                    }
                }
            }
            
            System.out.println("Delivery completed successfully");
            this.listeningSocket.close();
        }
        catch (Exception e) {
        	//System.out.println("Error! Please try again.");
        	System.err.print(e);
    		System.exit(1);
        }
    }

    /**
     * This method obtains information from the header.
     */
    private void getHeaderInfo(byte[] pkt) {
    	//Get the source port number of the packet.
    	byte[] srcPort = new byte[2];
        System.arraycopy(pkt, 0, srcPort, 0, 2);
        this.srcPort = toShort(srcPort);
        
        //Get the destination port number of the packet.
    	byte[] destPort = new byte[2];
        System.arraycopy(pkt, 2, destPort, 0, 2);
        this.destPort = toShort(destPort);
        
        //Get the sequence number of the packet.
    	byte[] seqNum = new byte[4];
        System.arraycopy(pkt, 4, seqNum, 0, 4);
        this.seqNum = toInt(seqNum);
    	
    	//Get the ACK number of the packet.
        byte[] ackNum = new byte[4];
        System.arraycopy(pkt, 8, ackNum, 0, 4);
        this.ackNum = toInt(ackNum);
        
        //Get the flag field of the packet.
        this.flags = pkt[13];
        
        //Get the window size of the packet.
    	byte[] windowSize = new byte[2];
        System.arraycopy(pkt, 14, windowSize, 0, 2);
        this.windowSize = toShort(windowSize);
        
        //Get the checksum of the packet.
    	byte[] checksum = new byte[2];
        System.arraycopy(pkt, 16, checksum, 0, 2);
        this.checksum = toShort(checksum);
    }

    /**
     * This method computes the checksum of a packet over the header and data.
     */
    private short getChecksum(byte[] pkt) {
    	//compute the checksum of the header.
    	this.checksum = (short)(this.srcPort + this.destPort + this.windowSize);
    	
        //compute the checksum of the data.
        for (int i = 0; i < pkt.length; i += 2) {
        	if (i != pkt.length - 1) {
        		byte[] byteArray = new byte[2];
        		System.arraycopy(pkt, i, byteArray, 0, 2);
        		this.checksum += toShort(byteArray);
        	}
        	else {
        		break;
        	}
        }
    	
    	this.checksum = (short)(~this.checksum);
    	return this.checksum;
    }

    /**
     * This method logs the packets transmission to the log file.
     */
    private void toLogFile(PrintWriter logWriter) {
    	String srcAddr = this.srcIp + ":" + this.srcPort;
        String destAddr = this.destIp + ":" + this.destPort;
        String flags = "";
        
        if (this.flags == 16) {
        	flags = ", ACK 1, FIN 0";
        }
        else {
        	flags = ", ACK 1, FIN 1";
        }
        
        this.ack = "Sequence Number " + this.seqNum + ", ACK Number " + this.ackNum + flags;
        logWriter.println(new Date() + ": Source " + srcAddr + ", Destination " + destAddr +
        		", " + this.ack);
    }
    
    /**
     * This method logs the sent ACK to the log file.
     */
    private void toLogFile(PrintWriter logWriter, int i) {
        String srcAddr = this.destIp + ":" + this.sendSocket.getLocalPort();
        String destAddr = this.srcIp + ":" + this.ackPort;
        
        logWriter.println(new Date() + ": Source " + srcAddr + ", Destination " + destAddr +
        		", " + this.ack);
    }
    
    /**
     * This method converts a byte array to a short number.
     */
    private short toShort(byte[] byteArray) {
        ByteBuffer buff = ByteBuffer.wrap(byteArray);
        buff.order(ByteOrder.BIG_ENDIAN);
        return buff.getShort();
    }
    
    /**
     * This method converts a byte array to an integer.
     */
    private int toInt(byte[] byteArray) {
        ByteBuffer buff = ByteBuffer.wrap(byteArray);
        buff.order(ByteOrder.BIG_ENDIAN);
        return buff.getInt();
    }
}
