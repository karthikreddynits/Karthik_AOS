import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import com.sun.nio.sctp.MessageInfo;
import com.sun.nio.sctp.SctpChannel;

public class MainClass {

	/*
	 * 1. Read from the Configuration file to fill in the Node Identity 2. Read
	 * from the Topology and set its Connection Map
	 */

	// My Node Identifier
	static int nodeId;
	static String nodedomainName = null;
	static int nodeportNumber = 0;

	static int nodeCount;

	// flag to check if received ack from all the cohorts
	// static boolean receivedAllAcks = false;
	static volatile boolean[] cpACKFlagArray;
	static ArrayList<String> domainNameList = new ArrayList<String>();
	static ArrayList<Integer> portNumerList = new ArrayList<Integer>();

	static HashMap<Integer, String> domainNameListMap = new HashMap<Integer, String>();
	static HashMap<Integer, Integer> portNumerListMap = new HashMap<Integer, Integer>();

	static ArrayList<Integer> cohertList = new ArrayList<Integer>();

	// List to maintain the connection list.
	static ArrayList<SctpChannel> connectionChannel = new ArrayList<SctpChannel>();
	static HashMap<Integer, SctpChannel> connectionChannelMap = new HashMap<Integer, SctpChannel>();

	// Message Count
	static volatile int totalMessageCount = 100;
	static volatile int sentMessageCount = 0;
	static volatile int messageId = 0;

	// Synchronization Handler Variables SHARED RESOURSES
	static volatile boolean applicationMessageMutex = true;

	// Clock Tick
	static volatile int logicalClock = 0;
	static int clockTrigger = 10;

	// CheckPoint Message Variables
	static int totalCheckPointMessage = 10;
	static int sentCheckPointMessage = 0;
	static volatile boolean cpStatusFlag = false;
	static int InitiatorId = -1;
	static int cpReqCohortCount = 0;
	static volatile int cpAckCount = 0;
	static ArrayList<Integer> cpForwardslist = new ArrayList<Integer>();
	// HashMap<String, Integer> cpInstanceMap = new HashMap<String, Integer>();

	// Data Structures to store the messages sent and received before writing it
	// into the back up file (from last check point to the current point)
	static ArrayList<Message> sentMessageBuffer = new ArrayList<Message>();
	static ArrayList<Message> receivedMessageBuffer = new ArrayList<Message>();

	// CP related
	static HashMap<Integer, Integer> LLR = new HashMap<Integer, Integer>();
	static HashMap<Integer, Integer> FLS = new HashMap<Integer, Integer>();
	static HashMap<Integer, Integer> LLS = new HashMap<Integer, Integer>();
	static HashMap<Integer, Integer> tempLLS = new HashMap<Integer, Integer>();
	static boolean FLSflag = true;
	static volatile boolean StableCPFlag = false;

	// ROll back related
	static volatile boolean rollBackFlag = false;
	static int totalRollBackMessage = 2;
	static int sentRollBackMessage = 0;
	static boolean rollBackDecession = false;
	static volatile int rollBackAckCount = 0;
	static volatile int rollBackParent = 0;

	public static void main(String[] args) throws NumberFormatException,
			IOException, InterruptedException {
		nodeId = Integer.parseInt(args[0]);

		// List the Coherts
		readTopologyFile();

		// Count the Total number of nodes for each process
		nodeCount = cohertList.size();

		// Read the config file and set the Lists
		readConfigurationFile();

		// Start Server Thread
		new Thread(new ServerConnections()).start();

		Thread.currentThread();
		Thread.sleep(9000 - (nodeId * 200));

		// Starts the Client Thread
		new Thread(new ClientConnections()).start();

		Thread.sleep(7000 - (nodeId * 200));

		// Thread that starts Request message
		MessageSender messagesender = new MessageSender();
		new Thread(messagesender).start();

		Thread.sleep(500);
		CheckPointInitiaon checkPointInitiaon = new CheckPointInitiaon();
		new Thread(checkPointInitiaon).start();

		RollBackInitiaon rollBackInitiation = new RollBackInitiaon();
		new Thread(rollBackInitiation).start();

	}

	private static void readTopologyFile() throws IOException {
		String fileName = "./topology.txt";
		String currentLIne = null;

		BufferedReader br = new BufferedReader(new FileReader(fileName));
		int count = 0;
		String[] tokens;

		while ((currentLIne = br.readLine()) != null) {
			tokens = currentLIne.split(" ");
			if (Integer.parseInt(tokens[0]) == nodeId) {
				cohertList.add(Integer.parseInt(tokens[1]));
				// initialize LLR and FLS to 0
				LLR.put(Integer.parseInt(tokens[1]), 0);
				FLS.put(Integer.parseInt(tokens[1]), 0);
			}
		}
		// initialize the cpAckFlag array
		cpACKFlagArray = new boolean[cohertList.size()];
		// close the buffer
		br.close();

	}

	private static void readConfigurationFile() throws NumberFormatException,
			IOException {
		String fileName = "./configuration.txt";
		String currentLIne = null;

		BufferedReader br = new BufferedReader(new FileReader(fileName));
		int count = 0;
		String[] tokens;

		while ((currentLIne = br.readLine()) != null) {
			tokens = currentLIne.split(" ");

			if (Integer.parseInt(tokens[0]) == nodeId) {
				nodedomainName = tokens[1];
				nodeportNumber = Integer.parseInt(tokens[2]);
			}

			if (cohertList.contains(Integer.parseInt(tokens[0]))) {
				domainNameList.add(tokens[1]);
				portNumerList.add(Integer.parseInt(tokens[2]));

				domainNameListMap.put(Integer.parseInt(tokens[0]), tokens[1]);
				portNumerListMap.put(Integer.parseInt(tokens[0]),
						Integer.parseInt(tokens[2]));
			}

		}

		for (int i = 0; i < portNumerList.size(); i++) {
			System.out.println(portNumerList.get(i) + ""
					+ domainNameList.get(i));
		}

		System.out.println("MY Host name :" + nodedomainName);
		System.out.println("MY Port num" + nodeportNumber);
		System.out.println("Total Nodes" + nodeCount);
		br.close();
	}

	public static synchronized int getLogicalClock() {
		return logicalClock;
	}

	public static synchronized void incrementLogicalClock() {
		logicalClock++;
	}

	// Synchronized block to send the Message via channels
	public synchronized static void sendMessage(SctpChannel clientSock,
			Message Message) throws CharacterCodingException {
		ByteBuffer sendBuffer = ByteBuffer.allocate(512);
		sendBuffer.clear();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oos = null;
		try {
			oos = new ObjectOutputStream(baos);
			oos.writeObject(Message);
		} catch (IOException e) {
			e.printStackTrace();
		}

		byte[] bytes = baos.toByteArray();

		sendBuffer.put(bytes);
		sendBuffer.flip();

		try {
			// Send a message in the channel
			MessageInfo messageInfo = MessageInfo.createOutgoing(null, 0);
			clientSock.send(sendBuffer, messageInfo);
		} catch (IOException ex) {
			Logger.getLogger(MainClass.class.getName()).log(Level.SEVERE, null,
					ex);
		}

	}

	public synchronized static void processCheckpointRequest(Message message) {

		if (message.tag.contains("CheckPointRequest")) {
			System.out.println("LLR VAlues :");
			for (int i : MainClass.LLR.keySet()) {
				System.out.println("\n" + MainClass.LLR.get(i) + "\n");
			}
			// Logic
			// check if already involved in a CP process
			// accept cp request
			// if not involved in any CP already
			// OR
			// if involved in a CP, but for the same iniator and the same
			// instance
			// of the CP
			if (!cpStatusFlag) {
				System.out.println("received a new CP REQ from "
						+ message.senderId + " for initiator "
						+ message.initiator);
				cpStatusFlag = true;
				// System.out.println(cpStatusFlag + ",,,,,,,, Flag");
				// set the CP static initiator variable
				InitiatorId = message.initiator;
				// Freeze the applcation messages by setting this flag false
				MainClass.applicationMessageMutex = false;
				// Take a tentative checkpoint.
				try {
					tentativeCheckPoint();
					// Send a req to all my coherts who have not already
					// received a request for this instance
					for (int j : MainClass.connectionChannelMap.keySet()) {

						// Send the CP request to only those cohorts from whom I
						// received a message since my last CP
						if ((MainClass.LLR.get(j) > 0)
								&& (!message.path.contains(j))) {

							Message cpMessage = new Message(
									"CheckPointRequest", MainClass.nodeId, j,
									0, null, 0, MainClass.InitiatorId);
							// Copy the elements of the message.path to
							// cpMessage.path
							System.out.println(cpMessage.path.size()
									+ " <-----> " + message.path.size());
							// Collections.copy(cpMessage.path, message.path);
							for (Integer i : message.path) {
								cpMessage.path.add(i);
							}
							cpMessage.path.add(MainClass.nodeId);
							System.out
									.println("ForwARDING CP message ******************************************"
											+ cpMessage.toString()
											+ "\n********************\n");
							MainClass.sendMessage(
									MainClass.connectionChannelMap.get(j),
									cpMessage);
							// increment the cohort count who received CP
							MainClass.cpReqCohortCount++;
							// add the node to the CP forwards list
							cpForwardslist.add(j);
						}

					}
					if (MainClass.cpReqCohortCount == 0) {
						System.out.println(cpStatusFlag + ",,,,,,,, Flag");
						// No CP Request forwarded from me
						// Send ACK true to the parent
						Message ackMessage = new Message("CheckPointAck",
								MainClass.nodeId, message.path.get(message.path
										.size() - 1), 0, null, 0,
								MainClass.InitiatorId);
						ackMessage.flag = true;
						// Collections.copy(ackMessage.path, message.path);
						for (Integer i : message.path) {
							ackMessage.path.add(i);
						}
						System.out.println("ACK CP MESSAGE........."
								+ ackMessage.toString() + "..............\n");
						MainClass.sendMessage(
								MainClass.connectionChannelMap.get(message.path
										.get(message.path.size() - 1)),
								ackMessage);

					}

				} catch (Exception e) {
					e.printStackTrace();
				}

			} else if (cpStatusFlag && (InitiatorId == message.initiator)) {
				// System.out.println(cpStatusFlag + ",,,,,,,, Flag");
				System.out.println("Received a DUP CP Req from "
						+ message.senderId + " for initiator "
						+ message.initiator);
				// This is a duplicate request for the CP instance, but coming
				// from a different sender.
				// Blindly send an ACK true to this guy
				// If I fail, I will anyways send false to the original sender
				// who actually triggered my CP process
				/*
				 * node0 -> (node1) ---- node0 will initiate a CP and send REQ
				 * to node1 node1 -> (node0, node2, node3) ---- node1 will
				 * forward REQ to node2, node3 node2 -> (node1, node3) ----
				 * node2 will forward REQ to node3 node3 -> (node1, node3) ----
				 * node3 will forward to none. node3 first received a REQ from
				 * node1 and is taking a CP now. But it received a duplicate REQ
				 * from node2 so node3 will simply send ACK true to node2. If it
				 * fails in the CP process, it will inform ACK false to node1.
				 * which inturn sends an ACK false to node0 which will abort the
				 * CP instance
				 */
				// No CP Request forwarded from me
				// Send ACK true to the parent
				Message ackMessage = new Message("CheckPointAck",
						MainClass.nodeId,
						message.path.get(message.path.size() - 1), 0, null, 0,
						MainClass.InitiatorId);
				ackMessage.flag = true;
				// Collections.copy(ackMessage.path, message.path);
				for (Integer i : message.path) {
					ackMessage.path.add(i);
				}

				System.out.println("Sending ACK to :"
						+ message.path.get(message.path.size() - 1));
				try {
					MainClass.sendMessage(MainClass.connectionChannelMap
							.get(message.path.get(message.path.size() - 1)),
							ackMessage);
				} catch (CharacterCodingException e) {
					e.printStackTrace();
				}

			} else {

				// I am already in a CP process. I can only process one CP
				// instance at a time
				// send ACK false to the message sender
				Message ackMessage = new Message("CheckPointAck",
						MainClass.nodeId,
						message.path.get(message.path.size() - 1), 0, null, 0,
						message.initiator);
				ackMessage.flag = false;
				// Collections.copy(ackMessage.path, message.path);
				for (Integer i : message.path) {
					ackMessage.path.add(i);
				}
				System.out
						.println("Received REQ for a second CP instance........."
								+ ackMessage.toString() + "..............\n");
				try {
					MainClass.sendMessage(MainClass.connectionChannelMap
							.get(message.path.get(message.path.size() - 1)),
							ackMessage);
				} catch (CharacterCodingException e) {
					e.printStackTrace();
				}
			}

		}

		// if the message is a CP ack
		else if (message.tag.contains("CheckPointAck")) {
			// save all the acks
			MainClass.cpACKFlagArray[MainClass.cpAckCount] = message.getFlag();
			// increment the ack count
			MainClass.cpAckCount++;
			// if received all the acks from the cohorts, set
			// the receivedAllAcks flag
			if (MainClass.cpAckCount == MainClass.cpReqCohortCount) {
				// Get count of all true flags
				int trueCount = 0;
				for (int i = 0; i < MainClass.cpACKFlagArray.length; i++) {
					if (MainClass.cpACKFlagArray[i]) {
						trueCount++;
					}
				}
				try {
					if (trueCount == MainClass.cpReqCohortCount) {
						// Inform parent OK
						System.out.println("Entered OK BLock \n");
						int index = 0;
						for (int i = 0; i < message.path.size(); i++) {
							if (message.path.get(i) == nodeId) {
								index = i - 1;
								break;
							}
						}
						if (index == -1) {
							System.out
									.println("I am the intiator and making it stable......................");

							// increment the CP count
							MainClass.sentCheckPointMessage++;

							sendPermanentCheckPoint();
						} else {
							System.out.println("Index://////////////////////"
									+ index + "///////////// Index in path "
									+ message.path.get(index));
							Message ackMessage = new Message("CheckPointAck",
									MainClass.nodeId, message.path.get(index),
									0, null, 0, MainClass.InitiatorId);
							// Collections.copy(ackMessage.path, message.path);
							for (Integer i : message.path) {
								ackMessage.path.add(i);
							}
							ackMessage.flag = true;
							System.out.println("ACK CP MESSAGE........."
									+ ackMessage.toString()
									+ "..............\n");
							MainClass.sendMessage(
									MainClass.connectionChannelMap.get(index),
									ackMessage);
						}
					} else {
						// Inform parent NO

						int index = 0;
						for (int i = 0; i < message.path.size(); i++) {
							if (message.path.get(i) == nodeId) {
								index = i - 1;
								break;
							}
						}
						if (index == -1) {
							System.out
									.println("Received at leat one ACK with 'false' and");
							System.out
									.println("I am the intiator and discarding......................");
							sendDiscardCheckPoint();
						} else {
							System.out
									.println("Received at least one ACK with false from my cohorts");
							System.out
									.println("Cannot participate in CP. Send NO to parent");
							Message ackMessage = new Message("CheckPointAck",
									MainClass.nodeId, message.path.get(index),
									0, null, 0, MainClass.InitiatorId);
							// Collections.copy(ackMessage.path, message.path);
							for (Integer i : message.path) {
								ackMessage.path.add(i);
							}
							ackMessage.flag = false;
							System.out.println("message "
									+ ackMessage.toString());
							MainClass.sendMessage(
									MainClass.connectionChannelMap.get(index),
									ackMessage);
						}
					}
				} catch (CharacterCodingException e) {
					e.printStackTrace();
				}

			}
		}

		// if the message is a CP permanent
		else if (message.tag.contains("CheckPointPermanent")) {
			System.out.println("Received Permanent " + message.toString());
			StableCPFlag = true;
			writeIntoStable();
			// make the tentative CP permanent. Inform it to coherts
			for (int j : MainClass.connectionChannelMap.keySet()) {
				if ((MainClass.LLR.get(j) > 0) && (!message.path.contains(j))
						&& cpForwardslist.contains(j)) {

					Message permCPMessage = new Message("CheckPointPermanent",
							MainClass.nodeId, j, 0, null, 0,
							MainClass.InitiatorId);
					// Collections.copy(permCPMessage.path, message.path);
					for (Integer i : message.path) {
						permCPMessage.path.add(i);
					}
					permCPMessage.path.add(MainClass.nodeId);
					System.out
							.println("Sending Permanemt CP message ******************************************"
									+ permCPMessage.toString()
									+ "\n********************\n");

					try {
						MainClass.sendMessage(
								MainClass.connectionChannelMap.get(j),
								permCPMessage);
					} catch (CharacterCodingException e) {
						e.printStackTrace();
					}
				}
			}

			// reset all the variables after permanent
			resetVariablesAfterPermanent();

		}

		// if the message is a CP discarrd
		else if (message.tag.contains("CheckPointDiscard")) {

			// StableCPFlag = false;
			// discard the tentative CP. Inform it to coherts
			removeTentativeCheckPoint();
			for (int j : MainClass.connectionChannelMap.keySet()) {
				if ((MainClass.LLR.get(j) > 0) && (!message.path.contains(j))
						&& cpForwardslist.contains(j)) {

					Message discardCPMessage = new Message("CheckPointDiscard",
							MainClass.nodeId, j, 0, null, 0,
							MainClass.InitiatorId);
					// Collections.copy(discardCPMessage.path, message.path);
					for (Integer i : message.path) {
						discardCPMessage.path.add(i);
					}
					discardCPMessage.path.add(MainClass.nodeId);
					System.out
							.println("Sending Discard CP message ******************************************"
									+ discardCPMessage.toString()
									+ "\n********************\n");

					try {
						MainClass.sendMessage(
								MainClass.connectionChannelMap.get(j),
								discardCPMessage);
					} catch (CharacterCodingException e) {
						e.printStackTrace();
					}
				}
			}
			// reset all the variables Mutex nad write to Stable.file
			resetVariablesAfterDiscard();

		}

	}

	static void sendDiscardCheckPoint() {
		// remove the tentative CP
		removeTentativeCheckPoint();
		// SEND TO ALL THE Coherts which have sent YES/NO TO TENTATIVE
		try {
			for (int j : MainClass.connectionChannelMap.keySet()) {

				// Send the CP request to only those cohorts from whom I
				// received a message since my last CP
				if (MainClass.LLR.get(j) > 0 && cpForwardslist.contains(j)) {

					Message discardCPMessage = new Message("CheckPointDiscard",
							MainClass.nodeId, j, 0, null, 0,
							MainClass.InitiatorId);
					discardCPMessage.path.add(MainClass.nodeId);
					System.out
							.println("Sending Discard CP message ******************************************"
									+ discardCPMessage.toString()
									+ "\n********************\n");

					MainClass.sendMessage(
							MainClass.connectionChannelMap.get(j),
							discardCPMessage);
				}
			}

			resetVariablesAfterDiscard();

		} catch (CharacterCodingException e) {
			e.printStackTrace();
		}
		// increment the cohort count who received CP

	}

	static void resetVariablesAfterDiscard() {

		MainClass.cpAckCount = 0;
		MainClass.cpReqCohortCount = 0;
		MainClass.cpForwardslist.clear();
		MainClass.InitiatorId = -1;
		for (int i = 0; i < MainClass.cpACKFlagArray.length; i++) {
			MainClass.cpACKFlagArray[i] = false;
		}
		// start application messages
		MainClass.applicationMessageMutex = true;
		MainClass.cpStatusFlag = false;
		System.out
				.println("$$$$$$$$$$ end of resrtVariablesAfterDiscard $$$$$$$$"
						+ "--at time stamp :" + getLogicalClock());

	}

	static void resetVariablesAfterPermanent() {
		MainClass.cpAckCount = 0;
		MainClass.cpReqCohortCount = 0;

		for (Integer i : LLR.keySet()) {
			LLR.put(i, 0);
		}
		for (Integer i : FLS.keySet()) {
			FLS.put(i, 0);
		}
		StableCPFlag = true;
		MainClass.cpForwardslist.clear();

		MainClass.FLSflag = true;
		MainClass.sentMessageBuffer.clear();
		MainClass.receivedMessageBuffer.clear();
		MainClass.InitiatorId = -1;
		for (int i = 0; i < MainClass.cpACKFlagArray.length; i++) {
			MainClass.cpACKFlagArray[i] = false;
		}
		// start application messages

		// Add temp LLS to LLS
		for (Integer i : tempLLS.keySet()) {
			LLS.put(i, tempLLS.get(i));
		}

		for (Integer i : LLS.keySet()) {
			System.out.println("...........LLS VALUES.........." + i + "  --->"
					+ LLS.get(i));
		}
		System.out.println("CP FLAG =======>" + StableCPFlag);
		MainClass.applicationMessageMutex = true;
		System.out
				.println("applicationMessageMutex " + applicationMessageMutex);
		MainClass.cpStatusFlag = false;
		System.out
				.println("$$$$$$$$$$ end of resrtVariablesAfterPermanent $$$$$$$$"
						+ "--at time stamp :" + getLogicalClock());

	}

	static synchronized void writeIntoStable() {

		try {
			String fileName = "./" + nodeId + "SentMessageTentativeBackup.txt";
			BufferedReader br = new BufferedReader(new FileReader(fileName));

			// This is the actual permanent CP file for sent messages
			File file1 = new File("./" + nodeId + "SentMessagePermanent.txt");
			if (!file1.exists()) {
				file1.createNewFile();
			}
			FileWriter fw1 = new FileWriter(file1.getAbsoluteFile(), false);
			BufferedWriter bw1 = new BufferedWriter(fw1);

			// This is the backup CP file for sent messages
			File file2 = new File("./" + nodeId
					+ "SentMessagePermanentBackup.txt");
			if (!file2.exists()) {
				file2.createNewFile();
			}

			FileWriter fw2 = new FileWriter(file2.getAbsoluteFile(), true);
			BufferedWriter bw2 = new BufferedWriter(fw2);

			String curLine = null;
			while ((curLine = br.readLine()) != null) {
				bw1.write(curLine + "\n");
				bw2.write(curLine + "\n");
			}
			bw2.write("\n---------------------------------------------------------\n");
			br.close();
			bw1.close();
			bw2.close();

			String fileName1 = "./" + nodeId
					+ "ReceivedMessageTentativeBackup.txt";
			BufferedReader br1 = new BufferedReader(new FileReader(fileName1));

			// This is the actual permanent CP file for received messages. We
			// dont really need one though, we are creating one
			File file3 = new File("./" + nodeId
					+ "ReceivedMessagePermanent.txt");
			if (!file3.exists()) {
				file3.createNewFile();
			}

			FileWriter fw3 = new FileWriter(file3.getAbsoluteFile(), false);
			BufferedWriter bw3 = new BufferedWriter(fw3);

			// This is the backup CP file for received messages.
			File file4 = new File("./" + nodeId
					+ "ReceivedMessagePermanentBackup.txt");
			if (!file4.exists()) {
				file4.createNewFile();
			}

			FileWriter fw4 = new FileWriter(file4.getAbsoluteFile(), true);
			BufferedWriter bw4 = new BufferedWriter(fw4);

			String curLine1 = null;
			while ((curLine1 = br1.readLine()) != null) {
				bw3.write(curLine1 + "\n");
				bw4.write(curLine1 + "\n");
			}
			bw4.write("---------------------------------------------------------\n");
			br1.close();
			bw3.close();
			bw4.close();

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private synchronized static void sendPermanentCheckPoint() {
		// Write to stable CP
		writeIntoStable();
		// SEND TO ALL THE Coherts which have sent YES TO TENTATIVE
		try {
			for (int j : MainClass.connectionChannelMap.keySet()) {

				// Send the CP request to only those cohorts from whom I
				// received a message since my last CP
				if (MainClass.LLR.get(j) > 0 && cpForwardslist.contains(j)) {

					Message permCPMessage = new Message("CheckPointPermanent",
							MainClass.nodeId, j, 0, null, 0,
							MainClass.InitiatorId);
					permCPMessage.path.add(MainClass.nodeId);
					System.out
							.println("Sending Permanemt CP message ******************************************"
									+ permCPMessage.toString()
									+ "\n********************\n");

					MainClass.sendMessage(
							MainClass.connectionChannelMap.get(j),
							permCPMessage);
				}
			}
			// reset the variables after permanent
			resetVariablesAfterPermanent();

		} catch (CharacterCodingException e) {
			e.printStackTrace();
		}

	}

	public synchronized static void processApplicationMessage(Message message) {
		// set the LLR flag for this message
		LLR.put(message.senderId, message.messageId);
	}

	public synchronized static void tentativeCheckPoint()
			throws ParserConfigurationException, IOException,
			TransformerException {

		/*
		 * DocumentBuilderFactory documentBuilderFactory =
		 * DocumentBuilderFactory .newInstance();
		 * 
		 * DocumentBuilder documentBuilder = documentBuilderFactory
		 * .newDocumentBuilder(); Document document =
		 * documentBuilder.newDocument();
		 * 
		 * String root = "Messages"; Element rootElement =
		 * document.createElement(root); document.appendChild(rootElement); for
		 * (int i = 0; i < MainClass.sentMessageBuffer.size(); i++) {
		 * 
		 * Message message; String type, body; String messageId; String
		 * receiverId; String senderId;
		 * 
		 * // message = MainClass.sentMessageBuffer.get(i); // String[] tokens =
		 * message.split(","); //
		 * (AppMessage),(SenderID),(ReceiverId),(MonotonicalValue //
		 * MessID),(Body) type = MainClass.sentMessageBuffer.get(i).tag;
		 * senderId = MainClass.sentMessageBuffer.get(i).senderId + "";
		 * receiverId = MainClass.sentMessageBuffer.get(i).receiverId + "";
		 * messageId = MainClass.sentMessageBuffer.get(i).messageId + ""; body =
		 * MainClass.sentMessageBuffer.get(i).body;
		 * 
		 * String element = "Message"; Element em =
		 * document.createElement(element);
		 * em.appendChild(document.createTextNode(type));
		 * rootElement.appendChild(em);
		 * 
		 * Attr attr = document.createAttribute("SenderId");
		 * attr.setValue(senderId); em.setAttributeNode(attr);
		 * 
		 * Attr attr2 = document.createAttribute("ReceiverId");
		 * attr2.setNodeValue(receiverId); em.setAttributeNode(attr2);
		 * 
		 * Attr attr3 = document.createAttribute("MessageId");
		 * attr3.setNodeValue(messageId); em.setAttributeNode(attr3);
		 * 
		 * Attr attr1 = document.createAttribute("MessageBody");
		 * attr1.setValue(body); em.setAttributeNode(attr1);
		 * 
		 * }
		 * 
		 * File file = new File("./SentMessageTentativeBackup.xml"); if
		 * (!file.exists()) { file.createNewFile(); }
		 * 
		 * FileWriter fw = new FileWriter(file.getAbsoluteFile());
		 * BufferedWriter bw = new BufferedWriter(fw);
		 * 
		 * TransformerFactory transformerFactory = TransformerFactory
		 * .newInstance(); Transformer transformer =
		 * transformerFactory.newTransformer(); DOMSource source = new
		 * DOMSource(document); StreamResult result = new StreamResult(bw);
		 * transformer.transform(source, result);
		 * 
		 * // close the writer bw.close();
		 */

		BufferedWriter out = new BufferedWriter(new FileWriter("./" + nodeId
				+ "SentMessageTentativeBackup.txt", false));

		/*
		 * File file = new File("./SentMessageTentativeBackup.txt"); if
		 * (!file.exists()) { file.createNewFile(); }
		 */

		// FileWriter fw = new FileWriter(file.getAbsoluteFile(), false);
		// BufferedWriter bw = new BufferedWriter(fw);

		for (int i = 0; i < MainClass.sentMessageBuffer.size(); i++) {
			out.write(MainClass.sentMessageBuffer.get(i).toString());
			out.write("\n");
		}

		out.close();

		File file1 = new File("./" + nodeId
				+ "ReceivedMessageTentativeBackup.txt");
		if (!file1.exists()) {
			file1.createNewFile();
		}
		FileWriter fw1 = new FileWriter(file1.getAbsoluteFile());
		BufferedWriter bw1 = new BufferedWriter(fw1);

		for (int i = 0; i < MainClass.receivedMessageBuffer.size(); i++) {
			bw1.write(MainClass.receivedMessageBuffer.get(i).toString());
			bw1.write("\n");
		}

		bw1.close();
	}

	public synchronized static void removeTentativeCheckPoint() {
		// DO nothing
	}

	// ROll back processing block
	public static synchronized void processRollBackRequest(Message message) {

		if (message.tag.contains("RollBackRequest")) {
			System.out.println("Processing ROll back REQ message :..."
					+ "---->" + message.toString() + "\n");

			/*
			 * If roll Back req is for first time then process and decide yes or
			 * no if yes set the decession flag to TRUE
			 * 
			 * on receiving the req message again if already decession flag is
			 * true just reply ok the sender.
			 */

			if (!rollBackDecession) {
				// Logic to make decession
				// LLR > LLS (Message)

				try {
					if (LLR.get(message.senderId) > message.LLS) {
						rollBackDecession = true;

						// Send the RollBAck request to all my cohorts
						for (int j : MainClass.connectionChannelMap.keySet()) {

							Message rollBackRequest = new Message(
									"RollBackRequest", MainClass.nodeId, j, 0,
									null, 0, message.initiator);

							for (Integer i : message.path) {
								rollBackRequest.path.add(i);
							}
							rollBackRequest.path.add(nodeId);
							rollBackRequest.LLS = LLS.get(j);

							System.out
									.println("ForwARDING RollBAck message ******************************************"
											+ rollBackRequest.toString()
											+ "\n********************\n");
							MainClass.sendMessage(
									MainClass.connectionChannelMap.get(j),
									rollBackRequest);

						}
					} else {
						// Make Flase and send ACK
						rollBackDecession = false;

						Message rollBackRequest = new Message("RollBackACK",
								MainClass.nodeId, message.senderId, 0, null, 0,
								message.initiator);
						try {
							MainClass.sendMessage(
									MainClass.connectionChannelMap
											.get(message.path.get(message.path
													.size() - 1)),
									rollBackRequest);
						} catch (CharacterCodingException e) {
							e.printStackTrace();
						}
					}

				} catch (Exception e) {
					e.printStackTrace();
				}

			} else if (rollBackDecession) {
				// Received Duplicate Request When Flag was already set i have
				// already told all my Coherts

				System.out
						.println("Received Duplicate Request When Flag was already set");
				// send Ok to the sender
				Message rollBackRequest = new Message("RollBackACK",
						MainClass.nodeId, message.senderId, 0, null, 0,
						message.initiator);
				for (Integer i : message.path) {
					rollBackRequest.path.add(i);
				}
				System.out
						.println("Sending Back duplicate RollBAck message ******************************************"
								+ rollBackRequest.toString()
								+ "\n********************\n");
				try {
					MainClass.sendMessage(MainClass.connectionChannelMap
							.get(message.path.get(message.path.size() - 1)),
							rollBackRequest);
				} catch (CharacterCodingException e) {
					e.printStackTrace();
				}
			}
		} else if (message.tag.contains("RollBackACK")) {
			// Now wait till u get ACK from all your COherts .... Only then you
			// can go back to ur parent with an ACK

			rollBackAckCount++;

			// On receiving all ACK's
			if (rollBackAckCount == cohertList.size()) {

				System.out.println("Entered RollBack ACK Block \n");
				int index = 0;
				for (int i = 0; i < message.path.size(); i++) {
					if (message.path.get(i) == nodeId) {
						index = i - 1;
						break;
					}
				}
				if (index == -1) {
					System.out
							.println("I am the intiator and Sending Roll BACK finalize to all......................");

					// increment the CP count
					MainClass.sentRollBackMessage++;
					sendFinalizeRollBack();
				} else {
					// I am not the Initiator ACK to my Parent
					System.out.println("Index://////////////////////" + index
							+ "///////////// Index in path "
							+ message.path.get(index));
					Message rollBackAckMessage = new Message("RollBackACK",
							MainClass.nodeId, message.path.get(index), 0, null,
							0, message.initiator);
					for (Integer i : message.path) {
						rollBackAckMessage.path.add(i);
					}
					System.out
							.println("ACK RollBck to my parent MESSAGE........."
									+ rollBackAckMessage.toString()
									+ "..............\n");
					try {
						MainClass.sendMessage(
								MainClass.connectionChannelMap.get(index),
								rollBackAckMessage);
					} catch (CharacterCodingException e) {
						e.printStackTrace();
					}
				}

			}

		} else if ((message.tag.contains("RollBackFinal"))) {
			MainClass.rollBackFlag = false;
			// I am client that received a RollBackFinal message.
			System.out
					.println("%%%%%%%%%%%%%%%%%%%%%%%%  ROLIING BACK    %%%%%%%%%%%%%%%%%%%%%%%%%%%%  \n"
							+ "But my flag is set to -->" + rollBackFlag);
			for (int j : MainClass.connectionChannelMap.keySet()) {
				// Forward the RollBackFinal message to the cohorts who has not
				// already received it
				if (j != message.initiator && (!message.path.contains(j))) {
					Message finalRollBack = new Message("RollBackFinal",
							MainClass.nodeId, j, 0, null, 0, message.initiator);
					finalRollBack.path.add(MainClass.nodeId);
					for (Integer i : message.path) {
						finalRollBack.path.add(i);
					}
					System.out
							.println("Forwarding final ROll BAck message ******************************************"
									+ finalRollBack.toString()
									+ "\n********************\n");

					try {
						MainClass.sendMessage(
								MainClass.connectionChannelMap.get(j),
								finalRollBack);
					} catch (CharacterCodingException e) {
						e.printStackTrace();
					}
				}
			}
			// reset all my variables
			writeRollBackTraceFile();
			resetVariablesAfterRollBackFinish();
		}

	}

	private static void writeRollBackTraceFile() {
		try {
			File file1 = new File("./" + nodeId + "RollBackTracefile.txt");
			if (!file1.exists()) {

				file1.createNewFile();

			}
			FileWriter fw1 = new FileWriter(file1.getAbsoluteFile(), true);
			BufferedWriter bw1 = new BufferedWriter(fw1);

			for (int i = 0; i < MainClass.sentMessageBuffer.size(); i++) {
				bw1.write(MainClass.sentMessageBuffer.get(i).toString());
				bw1.write("\n");
			}
			bw1.write("---------------------------------------------------\n");
			for (int i = 0; i < MainClass.receivedMessageBuffer.size(); i++) {
				bw1.write(MainClass.receivedMessageBuffer.get(i).toString());
				bw1.write("\n");
			}
			bw1.write("===========================================================================\n");
			bw1.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void sendFinalizeRollBack() {
		for (int j : MainClass.connectionChannelMap.keySet()) {

			// I was the initiator, forawrd the RollBackFinal message to all my
			// cohorts

			Message finalRollBack = new Message("RollBackFinal",
					MainClass.nodeId, j, 0, null, 0, nodeId);
			finalRollBack.path.add(MainClass.nodeId);
			System.out
					.println("Sending Final RollBack message ******************************************"
							+ finalRollBack.toString()
							+ "\n********************\n");

			try {
				MainClass.sendMessage(MainClass.connectionChannelMap.get(j),
						finalRollBack);
			} catch (CharacterCodingException e) {
				e.printStackTrace();
			}
		}
		// write status to file
		writeRollBackTraceFile();
		// reset the variables after permanent
		resetVariablesAfterRollBackFinish();

	}

	private static void resetVariablesAfterRollBackFinish() {

		// read the sent permanent CP file to find the messageId
		try {
			String fileName = "./" + nodeId + "SentMessagePermanent.txt";
			BufferedReader br = new BufferedReader(new FileReader(fileName));
			String str = "";
			// set the messageId to 0 by default. then keep updating it
			MainClass.messageId = 0;
			while ((str = br.readLine()) != null) {
				// split the string based on ','
				String[] tokens = str.split(",");
				// tokens are key value pairs. split the tokens based on '='
				for (String s : tokens) {
					String[] subTokens = s.split("=");
					// we only need to get the messageId token
					if (subTokens[0].trim().equals("messageId")) {
						MainClass.messageId = Integer.parseInt(subTokens[1]
								.trim());
					}
				}
			}
			if (MainClass.messageId != 0) {
				MainClass.messageId++;
			}

			// reset all the variables
			MainClass.sentMessageCount = messageId;
			MainClass.cpAckCount = 0;
			MainClass.cpReqCohortCount = 0;

			for (Integer i : LLR.keySet()) {
				LLR.put(i, 0);
			}
			for (Integer i : FLS.keySet()) {
				FLS.put(i, 0);
			}
			StableCPFlag = true;
			MainClass.cpForwardslist.clear();

			MainClass.FLSflag = true;
			MainClass.sentMessageBuffer.clear();
			MainClass.receivedMessageBuffer.clear();
			MainClass.InitiatorId = -1;
			for (int i = 0; i < MainClass.cpACKFlagArray.length; i++) {
				MainClass.cpACKFlagArray[i] = false;
			}
			// start application messages
			// set rollback flag false
			MainClass.rollBackFlag = false;
			// set message mutex flag true
			MainClass.applicationMessageMutex = true;
			// set cpStatusFlag false
			MainClass.cpStatusFlag = false;
			System.out
					.println("&&&&&&& end of resrtVariablesAfterRollBackFinal &&&&&&&&"
							+ "--at time stamp :" + getLogicalClock());

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
