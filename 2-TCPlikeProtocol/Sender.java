/*
 * This application realizes a simple TCP-like transport layer protocol. It provides
 * reliable, in order delivery of a stream of bytes. It can recover from in-network
 * packet loss, packet corruption, packet duplication and packet reordering and is
 * able to cope with dynamic network delays.
 */
import java.io.*;
import java.nio.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Date;

/**
 * This class implements the sender which sends TCP-like packets to the receiver
 * and then waits for the correspondent ACK. If a timeout does occur, it retransmit
 * the packet.
 */
public class Sender {
	/*
     * The name of the given file.
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
     * Total number of bytes that have been sent to the receiver.
     */
    private int totalBytesSent;
    
    /*
     * Total number of packets that have been sent to the receiver.
     */
    private int totalPktsSent;
    
    /*
     * Total number of packets that have been retransmitted.
     */
    private int totalPktsRetransmitted;
    
    /*
     * This variable records whether a packet has been retransmitted.
     */
    private boolean isRetransmitted;
      
    /*
     * The timeout after which the sender will retransmit the packet.
     */
    private int timeout;
    
    /*
     * The start time of a transmission.
     */
    private long startTime;
    
    /*
     * The end time of a transmission.
     */
    private long endTime;
    
    /*
     * The sample RTT. Here we use the default value 1000 milliseconds.
     */
    private int sampleRTT;
    
    /*
     * The estimated RTT which is computed from sample RTT and current estimated RTT.
     */
    private int estimatedRTT;
    
    /*
     * The RTT deviation.
     */
    private int devRTT;
    
    /*
     * The UDP socket which is used to send packets to the receiver (proxy).
     */
    private DatagramSocket sendSocket;
    
    /*
     * The TCP socket which is used to listen to calls from the receiver.
     */
    private ServerSocket listeningSocket;
    
    /*
     * The TCP socket which is used to receive the ACK packet from the receiver.
     */
    private Socket receiveSocket;
    
    /*
     * The ACK packet received from the receiver.
     */
    private String ack;
    
    /*
     * The byte array of the given file.
     */
    private byte[] fileByteArray;
    
    /*
     * A sequence of packets generated from the byte array.
     */
    private ArrayList<byte[]> pkts = new ArrayList<byte[]>();
	
    public static void main(String[] args) {
    	new Sender(args);
    }

    public Sender(String[] args) {
    	try {
        	this.fileName = args[0];
        	this.destIp = args[1];
        	this.destPort = Short.parseShort(args[2]);
        	this.ackPort = Short.parseShort(args[3]);
        	this.logFileName = args[4];
        	this.windowSize = (args.length == 6) ? Short.parseShort(args[5]) : 1;
        	this.sendSocket = new DatagramSocket();
        	this.srcIp = this.sendSocket.getLocalAddress().toString();
        	this.srcPort = (short)this.sendSocket.getLocalPort();
            this.listeningSocket = new ServerSocket(ackPort);
            this.timeout = 1000;
            this.sampleRTT = 1000;
            this.estimatedRTT = 1000;

            File logFile = new File(this.logFileName);
            PrintWriter logWriter = null;

            if (this.logFileName.equals("stdout")) {
            	logWriter = new PrintWriter(System.out, true);
            }
            else {
            	logWriter = new PrintWriter(new BufferedWriter(new FileWriter(logFile, true)), true);
            }
            
            //Generates a sequence of TCP-like packets from a given file. It first
            //generates a byte array of the given file, then converts it to a sequence of
            //packets with size (20 + MSS), and finally set the header fields.
            toFileByteArray();
            toPkts();
            setHeader();

            for (int i = 0; i < this.pkts.size(); i++) {
            	InetAddress address = InetAddress.getByName(this.destIp);
                DatagramPacket pkt = new DatagramPacket(this.pkts.get(i), 20 + MSS, address, this.destPort);
                getHeaderInfo(this.pkts.get(i));
                
                //Send a packet to the receiver.
                this.sendSocket.send(pkt);
                this.startTime = System.currentTimeMillis();
                totalBytesSent += 20 + MSS;
                totalPktsSent++;
                toLogFile(logWriter);

                this.listeningSocket.setSoTimeout(timeout);
                
                try {
                	//Wait for ACK from the receiver.
                	this.receiveSocket = this.listeningSocket.accept();
                	//The socket will only be blocked for the receiver for a given timeout.
                	this.receiveSocket.setSoTimeout(timeout);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(receiveSocket.getInputStream()));
                    this.ack = reader.readLine().trim();
                } catch (Exception e) {
                	//If a timeout dose occur, retransmit the packet.
                    i--;
                    this.totalPktsRetransmitted++;
                    this.isRetransmitted = true;
                    continue;
                }                
                
                if (this.ack.contains("ACK Number " + this.ackNum)) {
                    //If the packet has not been retransmitted, calculate the timeout.
                    if (!this.isRetransmitted) {
                    	this.endTime = System.currentTimeMillis();
                        getTimeout();
                    }

                    this.isRetransmitted = false;
                    toLogFile(logWriter, 1);
                    System.out.println("Packet " + i + " has been delivered successfully");
                }
                else {
                	i--;
                    this.totalPktsRetransmitted++;
                    this.isRetransmitted = true;
                }
                
                this.receiveSocket.close();
            }
            
            this.listeningSocket.close();
            
            //Print the transmission information.
            System.out.println("Delivery completed cuccessfully");
            System.out.println("Total bytes sent = " + this.totalBytesSent);
            System.out.println("Total packets sent = " + this.totalPktsSent);
            System.out.println("Total packets retransmitted = " + this.totalPktsRetransmitted);
        }
    	catch (Exception e) {
    		System.out.println("Error! Please try again.");
    		System.exit(1);
        }
    }

    /**
     * This method converts the given file to a byte array.
     */
    private void toFileByteArray() throws IOException {
        File file = new File(this.fileName);
        long fileLength = file.length();
        
        //If the file is too large, throw an exception.
        if (fileLength > Integer.MAX_VALUE) {
        	throw new IOException("Error: the file " + file.getName() + " is too large.");
        }
        
        this.fileByteArray = new byte[(int)fileLength];
        
        InputStream fileReadStream = new FileInputStream(file);
        fileReadStream.read(this.fileByteArray);
        fileReadStream.close();
    }
    
    /**
     * This method converts the byte array to a sequence of packets with size
     * (20 + MSS), where 20 is the space remained for the header.
     */
    private void toPkts() {
    	//The first 20 bytes of the packet are remained for the header.
    	byte[] pkt = new byte[20 + MSS];
        
        for (int i = 0; i < this.fileByteArray.length; i++) {
        	if (i != 0 && i % MSS == 0) {
            	this.pkts.add(pkt);
            	pkt = new byte[20 + MSS];
            }
            
        	pkt[20 + i % MSS] = this.fileByteArray[i];
        }
        this.pkts.add(pkt);
    }

    /**
     * This method sets the header for each packet.
     */
    private void setHeader() {
        for (int i = 0; i < this.pkts.size(); i++, this.seqNum++, this.ackNum++) {
        	//Set the 16-bit source port number field of the header.
            byte[] byteSrcPort = toByteArray(this.srcPort);
            System.arraycopy(byteSrcPort, 0, this.pkts.get(i), 0, 2);
            
            //Set the 16-bit destination port field of the header.
            byte[] byteDestPort = toByteArray(this.destPort);
            System.arraycopy(byteDestPort, 0, this.pkts.get(i), 2, 2);
            
            //Set the 32-bit sequence number field of the header.
            byte[] byteSeqNum = toByteArray(this.seqNum);
            System.arraycopy(byteSeqNum, 0, this.pkts.get(i), 4, 4);
            
            //Set the 32-bit ACK number field of the header.
            byte[] byteAckNum = toByteArray(this.ackNum);
            System.arraycopy(byteAckNum, 0, this.pkts.get(i), 8, 4);
            
            //Set the 4-bit header length field of the header.
            //The header length is 20 bytes (= 5 * 32 bits), so the first four bits
            //of this byte (the 4-bit header length field) is 0101 (= 5), while other
            //unused bits are 0. Therefore this byte is 01010000 (= 80).
            this.pkts.get(i)[12] = 80;
            
            //Set the 6-bit flag field of the header.
            //We only used ACK and FIN in this assignment.
            if (i == this.pkts.size() - 1) {
            	//FIN = 1.
            	this.pkts.get(i)[13] = 17;
            }
            else {
            	//FIN = 0.
            	this.pkts.get(i)[13] = 16;
            }
            
            //Set the 16-bit window size field of the header.
            byte[] byteWindowSize = toByteArray(this.windowSize);
            System.arraycopy(byteWindowSize, 0, this.pkts.get(i), 14, 2);
            
            //Set the 16-bit checksum field of the header.
            getChecksum(this.pkts.get(i));
            byte[] byteChecksum = toByteArray(this.checksum);
            System.arraycopy(byteChecksum, 0, this.pkts.get(i), 16, 2);
            
            //Since the 16-bit urgent data pointer field is not used in this
            //assignment, we don't need to set it, and use it default value 0.
        }
    }

    /**
     * This method computes the checksum of a packet over the header and data.
     */
    private void getChecksum(byte[] pkt) {
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
    }

    /**
     * This method obtains information from the header.
     */
    private void getHeaderInfo(byte[] pkt) {
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
    }

    /**
     * This method computes the timeout using sample RTT, estimated RTT, deviate RTT.
     */
    private void getTimeout() {
        this.sampleRTT = (int)(this.endTime - this.startTime);
        this.estimatedRTT = (int)(0.875 * this.estimatedRTT + 0.125 * sampleRTT);
        this.devRTT = (int)(0.75 * this.devRTT + 0.25 * Math.abs(sampleRTT - estimatedRTT));
        this.timeout = (int)(this.estimatedRTT + 4 * this.devRTT);
    }

    /**
     * This method logs the packets transmission to the log file.
     */
    private void toLogFile(PrintWriter logWriter) {
    	String srcAddr = this.srcIp + ":" + this.srcPort;
        String destAddr = this.destIp + ":" + this.destPort;
        String flags = "";
        
        if (this.flags == 16) {
        	flags = ", ACK 1, FIN 0, ";
        }
        else {
        	flags = ", ACK 1, FIN 1, ";
        }
        
        logWriter.println(new Date() + ": Source " + srcAddr + ", Destination " + destAddr +
        		", Sequence Number " + this.seqNum + ", ACK Number " + this.ackNum +
        		flags + "Estimated RTT " + this.estimatedRTT + " ms");
    }

    /**
     * This method logs the received ACK to the log file.
     */
    private void toLogFile(PrintWriter logWriter, int i) {
    	String srcPort = "" + this.receiveSocket.getPort();
    	String srcAddr = this.destIp + ":" + srcPort;
        String destAddr = this.srcIp + ":" + this.ackPort;
        
        logWriter.println(new Date() + ": Source " + srcAddr + ", Destination " + destAddr +
        		", " + this.ack + ", Estimated RTT " + this.estimatedRTT + " ms");
    }
    
    /**
     * This method converts a short number to a byte array.
     */
    private byte[] toByteArray(short value) {
    	ByteBuffer buff = ByteBuffer.allocate(2);
        buff.order(ByteOrder.BIG_ENDIAN);
        return buff.putShort(value).array();
    }

    /**
     * This method converts an integer to a byte array.
     */
    private byte[] toByteArray(int value) {
    	ByteBuffer buff = ByteBuffer.allocate(4);
        buff.order(ByteOrder.BIG_ENDIAN);
        return buff.putInt(value).array();
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
