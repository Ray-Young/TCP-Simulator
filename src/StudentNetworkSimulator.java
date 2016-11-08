import java.util.*;

import com.sun.prism.sw.SWMaskTexture;

import java.awt.Window;
import java.io.*;

public class StudentNetworkSimulator extends NetworkSimulator
{
    /*
     * Predefined Constants (static member variables):
     *
     *   int MAXDATASIZE : the maximum size of the Message data and
     *                     Packet payload
     *
     *   int A           : a predefined integer that represents entity A
     *   int B           : a predefined integer that represents entity B 
     *
     * Predefined Member Methods:
     *
     *  void stopTimer(int entity): 
     *       Stops the timer running at "entity" [A or B]
     *  void startTimer(int entity, double increment): 
     *       Starts a timer running at "entity" [A or B], which will expire in
     *       "increment" time units, causing the interrupt handler to be
     *       called.  You should only call this with A.
     *  void toLayer3(int callingEntity, Packet p)
     *       Puts the packet "p" into the network from "callingEntity" [A or B]
     *  void toLayer5(String dataSent)
     *       Passes "dataSent" up to layer 5
     *  double getTime()
     *       Returns the current time in the simulator.  Might be useful for
     *       debugging.
     *  int getTraceLevel()
     *       Returns TraceLevel
     *  void printEventList()
     *       Prints the current event list to stdout.  Might be useful for
     *       debugging, but probably not.
     *
     *
     *  Predefined Classes:
     *
     *  Message: Used to encapsulate a message coming from layer 5
     *    Constructor:
     *      Message(String inputData): 
     *          creates a new Message containing "inputData"
     *    Methods:
     *      boolean setData(String inputData):
     *          sets an existing Message's data to "inputData"
     *          returns true on success, false otherwise
     *      String getData():
     *          returns the data contained in the message
     *  Packet: Used to encapsulate a packet
     *    Constructors:
     *      Packet (Packet p):
     *          creates a new Packet that is a copy of "p"
     *      Packet (int seq, int ack, int check, String newPayload)
     *          creates a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and a
     *          payload of "newPayload"
     *      Packet (int seq, int ack, int check)
     *          chreate a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and
     *          an empty payload
     *    Methods:
     *      boolean setSeqnum(int n)
     *          sets the Packet's sequence field to "n"
     *          returns true on success, false otherwise
     *      boolean setAcknum(int n)
     *          sets the Packet's ack field to "n"
     *          returns true on success, false otherwise
     *      boolean setChecksum(int n)
     *          sets the Packet's checksum to "n"
     *          returns true on success, false otherwise
     *      boolean setPayload(String newPayload)
     *          sets the Packet's payload to "newPayload"
     *          returns true on success, false otherwise
     *      int getSeqnum()
     *          returns the contents of the Packet's sequence field
     *      int getAcknum()
     *          returns the contents of the Packet's ack field
     *      int getChecksum()
     *          returns the checksum of the Packet
     *      int getPayload()
     *          returns the Packet's payload
     *
     */

    /*   Please use the following variables in your routines.
     *   int WindowSize  : the window size
     *   double RxmtInterval   : the retransmission timeout
     *   int LimitSeqNo  : when sequence number reaches this value, it wraps around
     */

    public static final int FirstSeqNo = 0;
    private int WindowSize;
    private double RxmtInterval;
    private int LimitSeqNo;
    private Packet[] sW;	//sender window
    private Packet[] rW;	//receiver window
    private List<Packet> pList = new ArrayList<Packet>();
    private int seqNum;		//Keep track of sequence number
    private boolean T;		//To make sure it is allowed to be transmitted or not.
    private int lastACK;
    private int lastRCV;
    private int count;
    
    // Add any necessary class variables here.  Remember, you cannot use
    // these variables to send messages error free!  They can only hold
    // state information for A or B.
    // Also add any necessary methods (e.g. checksum of a String)

    // This is the constructor.  Don't touch!
    public StudentNetworkSimulator(int numMessages,
                                   double loss,
                                   double corrupt,
                                   double avgDelay,
                                   int trace,
                                   int seed,
                                   int winsize,
                                   double delay)
    {
        super(numMessages, loss, corrupt, avgDelay, trace, seed);
	WindowSize = winsize;
	LimitSeqNo = winsize+1;
	RxmtInterval = delay;
    }

    
    // This routine will be called whenever the upper layer at the sender [A]
    // has a message to send.  It is the job of your protocol to insure that
    // the data in such a message is delivered in-order, and correctly, to
    // the receiving upper layer.
    protected void aOutput(Message message)
    {
    	String data = message.getData();
    	int check = calCheckSum(data,seqNum,0);
    	Packet p = new Packet(seqNum, 0, check, data);
    	//System.out.println("Upper layer gives message "+seqNum);
    	pList.add(p);
    	if(T){
    		transmit();
    	}
    	//set the next sequence number.
    	if(WindowSize==1){
    		if(seqNum==0){
    			seqNum = 1;
    		}else{
    			seqNum = 0;
    		}
    		T = false;
    	}else{
    		if(seqNum<WindowSize-1){
    			seqNum+=1;
    		}else{
    			T = false;
    			seqNum = 0;
    		}
    	}
    }
    
    // This routine will be called whenever a packet sent from the B-side 
    // (i.e. as a result of a toLayer3() being done by a B-side procedure)
    // arrives at the A-side.  "packet" is the (possibly corrupted) packet
    // sent from the B-side.
    protected void aInput(Packet packet)
    {
    	int ack = packet.getAcknum();
    	int check = packet.getChecksum();
    	String data = packet.getPayload();
		if (check == calCheckSum(data,packet.getSeqnum(),ack)) {
			System.out.println("RECEIVED ACK: " + ack);
			if (WindowSize == 1) {
				toLayer5(rW[0].getPayload());
				clearWindow(sW);
				clearWindow(rW);
				if (ack == 1) {
					lastRCV = -1;
				}
				stopTimer(0);
				T = true;
			}
			if (WindowSize > 1) {
				if (ack == WindowSize - 1) {
					T = true;
					for(Packet pkt:rW){
						String m = pkt.getPayload();
						toLayer5(m);
					}
					System.out.println("--------ACK OF WINDOW " + count + " IS FINISHED------------------------");
					count++;
					stopTimer(0);
					clearWindow(sW);
					clearWindow(rW);
					lastRCV = -1;
				}
			}
		}else{
			System.out.println("ACK GETS CORRUPTED");
		}
    }
    
    // This routine will be called when A's timer expires (thus generating a 
    // timer interrupt). You'll probably want to use this routine to control 
    // the retransmission of packets. See startTimer() and stopTimer(), above,
    // for how the timer is started and stopped. 
    protected void aTimerInterrupt()
    {
		System.out.println("Time Out!!!");
		if (WindowSize == 1) {
			int x = sW[0].getSeqnum();
			System.out.println("Retransmitting packet " + x);
			Packet resentP = sW[0];
			toLayer3(0, resentP);
		} else {
			if(lastRCV+1==WindowSize){			//special condition, where the last ack lost.
				lastRCV=lastRCV-1;
			}
			System.out.println("Retransmitting packet " + (lastRCV + 1));
			Packet resentP = sW[lastRCV + 1];
			toLayer3(0, resentP);
		}
		startTimer(0, RxmtInterval);
    }
    
    // This routine will be called once, before any of your other A-side 
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity A).
    protected void aInit()
    {
    	seqNum = 0;
    	sW = new Packet[WindowSize];
    	T = true;
    	lastACK = -1;
    	count = 1;
    }
    
    // This routine will be called whenever a packet sent from the B-side 
    // (i.e. as a result of a toLayer3() being done by an A-side procedure)
    // arrives at the B-side.  "packet" is the (possibly corrupted) packet
    // sent from the A-side.
    protected void bInput(Packet packet)
    {
    	int seq = packet.getSeqnum();
    	int check = packet.getChecksum();
    	String data = packet.getPayload();
    	if(check==calCheckSum(data,seq,packet.getAcknum())){				//Means the packet is not corrupted
			if (WindowSize == 1) {
				rW[0] = packet;
			} else {
				rW[seq] = packet; 					// add packet to receiver window
			}
			if(WindowSize == 1){
				lastRCV = seq;
				String back = "RECEIVED TILL PACKET: "+seq;
				System.out.println(back);
				data = "ACK";
				check = calCheckSum(data,0,seq);
				Packet p = new Packet(0, seq, check, data);
				toLayer3(1, p); 
			}
    		if(WindowSize>1){
    			if(seq == lastRCV+1){				//receive packet in correct order
    				lastRCV=checkReceived(rW);		//check received till which packet.
    				String back = "RECEIVED TILL PACKET: "+lastRCV;
    				System.out.println(back);
    				data = "ACK";
    				check = calCheckSum(data,0,lastRCV);
    				Packet p = new Packet(0, lastRCV, check, data);
    				toLayer3(1, p);
    			}else{								//means packet loss
    				System.out.println("DETECT PACKET LOSS");
    				String back = "RECEIVED TILL PACKET: "+lastRCV;
    				System.out.println(back);
    				data = "ACK";
    				check = calCheckSum(data,0,lastRCV);
    				Packet p = new Packet(0, lastRCV, check, data);
    				toLayer3(1,p);
    			}
    		}
    		
    	}else{										//Means the packet is corrupted
    		System.out.println("DETECT PACKET CORRUPTED");
    		//Packet p = new Packet(0, lastRCV, 0);
			//toLayer3(1,p);
    	}
    }
    
    // This routine will be called once, before any of your other B-side 
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity B).
    protected void bInit()
    {
    	rW = new Packet[WindowSize];
    	lastRCV = -1;
    }

    // Use to print final statistics
    protected void Simulation_done()
    {
    	System.out.println("DONE!!!");
    }
    
    private int calCheckSum(String data,int seq,int ack){
    	int check = 0;
    	for(int i=0;i<data.length();i++){
    		check+=(int)data.charAt(i);
    	}
    	check= check+seq+ack;
    	return check;
    }
    
    private void transmit(){
    	List<Packet> rm = new ArrayList<Packet>();
    	for(Packet p:pList){
    		toLayer3(0, p);
    		System.out.println("SENDING PACKET "+p.getSeqnum());
    		System.out.println("--------------------");
    		if(WindowSize==1){
    			startTimer(0, RxmtInterval);
    			sW[0]=p;
    			rm.add(p);
    			break;
    		}else{
    			sW[p.getSeqnum()]=p;
    			if(p.getSeqnum()==WindowSize-1){
    				startTimer(0, RxmtInterval);
    				rm.add(p);
    				break;
    			}
    			rm.add(p);
    		}
    		
    	}
    	for(Packet p:rm){
    		pList.remove(p);
    	}
    }
    
    private int checkReceived(Packet[] window){
    	int i=0;
    	for(i=0;i<window.length;i++){
    		if(window[i]==null){
    			break;
    		}
    	}
    	return i-1;
    }
    
    private void clearWindow(Packet[] window){
    	for(int i=0;i<WindowSize;i++){
    		window[i]=null;
    	}
    }

}
