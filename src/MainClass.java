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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

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

	static int cpReqCohortCount = 0;
	static int cpAckCount = 0;
	// flag to check if received ack from all the cohorts
	static boolean receivedAllAcks = false;
	static boolean[] cpACKFlagArray;
	static ArrayList<String> domainNameList = new ArrayList<String>();
	static ArrayList<Integer> portNumerList = new ArrayList<Integer>();

	static HashMap<Integer, String> domainNameListMap = new HashMap<Integer, String>();
	static HashMap<Integer, Integer> portNumerListMap = new HashMap<Integer, Integer>();

	static ArrayList<Integer> cohertList = new ArrayList<Integer>();

	// List to maintain the connection list.
	static ArrayList<SctpChannel> connectionChannel = new ArrayList<SctpChannel>();
	static HashMap<Integer, SctpChannel> connectionChannelMap = new HashMap<Integer, SctpChannel>();

	// Message Count
	static int totalMessageCount = 20;
	static int sentMessageCount = 0;
	static int messageId = 0;

	// Synchronization Handler Variables SHARED RESOURSES
	static boolean applicationMessageMutex = true;

	// Clock Tick
	static int logicalClock = 0;
	static int clockTrigger = 10;

	// CheckPoint Message Variables
	static int totalCheckPointMessage = 5;
	static int sentCheckPointMessage = 0;
	static boolean cpStatusFlag = false;
	static int InitiatorId = -1;

	// Data Structures to store the messages sent and received before writing it
	// into the back up file (from last check point to the current point)
	static ArrayList<Message> sentMessageBuffer = new ArrayList<Message>();
	static ArrayList<Message> receivedMessageBuffer = new ArrayList<Message>();

	// CP related
	static HashMap<Integer, Integer> LLR = new HashMap<Integer, Integer>();
	static HashMap<Integer, Integer> FLS = new HashMap<Integer, Integer>();
	static boolean FLSflag = true;
	static boolean StableCPFlag = false;

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
				// set the CP static initiator variable
				InitiatorId = message.initiator;
				// Freeze the applcation messages by setting this flag false
				MainClass.applicationMessageMutex = false;
				// Take a tentative checkpoint.
				try {
					// TODO: Take tentative
					tentativeCheckPoint();
					// Send a req to all my coherts except for the parent

					for (int j : MainClass.connectionChannelMap.keySet()) {

						// Send the CP request to only those cohorts from whom I
						// received a message since my last CP
						if ((MainClass.LLR.get(j) > 0)
								&& (message.initiator != j)
								&& (message.senderId != j)) {

							Message cpMessage = new Message(
									"CheckPointRequest", MainClass.nodeId, j,
									0, null, 0, MainClass.InitiatorId);
							cpMessage.path = message.path;
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
						}

					}
					if (MainClass.cpReqCohortCount == 0) {
						// No CP Request forwarded from me
						// Send ACK true to the parent
						Message ackMessage = new Message("CheckPointAck",
								MainClass.nodeId, message.path.get(message.path
										.size() - 1), 0, null, 0,
								MainClass.InitiatorId);
						ackMessage.flag = true;
						ackMessage.path = message.path;
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

			} else if (cpStatusFlag && InitiatorId == message.initiator) {
				// This is a duplicate request for the CP instance, but coming
				// from a different sender.
				// Blindly send an ACK true to this guy
				// If I fail, I will anyways send false to the original sender
				// who actually triggered my CP process
				/*
				 * node0 -> (node1) ---- node0 will initiate a CP and send REQ
				 * to node1 
				 * node1 -> (node0, node2, node3) ---- node1 will
				 * forward REQ to node2, node3 
				 * node2 -> (node1, node3) ---- node2 will forward REQ to node3
				 * node3 -> (node1, node3) ---- node3 will forward to none. node3 first received a REQ from
				 * node1 and is taking a CP now. But it received a duplicate REQ
				 * from node2 so node3 will simply send ACK true to node2. If it
				 * fails in the CP process, it will inform ACK false to node1.
				 * which inturn sends an ACK false to node0 which will abort the
				 * CP instance
				 */
				// No CP Request forwarded from me
				// Send ACK true to the parent
				Message ackMessage = new Message("CheckPointAck",
						MainClass.nodeId, message.path.get(message.path
								.size() - 1), 0, null, 0,
						MainClass.InitiatorId);
				ackMessage.flag = true;
				ackMessage.path = message.path;
				System.out.println("ACK CP MESSAGE FOR DUPLICATE REQ........."
						+ ackMessage.toString() + "..............\n");
				try {
					MainClass.sendMessage(
							MainClass.connectionChannelMap.get(message.path
									.get(message.path.size() - 1)),
							ackMessage);
				} catch (CharacterCodingException e) {
					e.printStackTrace();
				}

			} else {
				// I am already in a CP process. I can only process one CP instance at a time
				// send ACK  false to the message sender
				Message ackMessage = new Message("CheckPointAck",
						MainClass.nodeId, message.path.get(message.path
								.size() - 1), 0, null, 0,
						MainClass.InitiatorId);
				ackMessage.flag = false;
				ackMessage.path = message.path;
				System.out.println("ACK CP MESSAGE FOR DUPLICATE REQ........."
						+ ackMessage.toString() + "..............\n");
				try {
					MainClass.sendMessage(
							MainClass.connectionChannelMap.get(message.path
									.get(message.path.size() - 1)),
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

							sendPermentCheckPoint();

							// TODO: Now send Permeanet to all my choerts.......
						} else {
							System.out.println("Index://////////////////////"
									+ index + "///////////// Index in path "
									+ message.path.get(index));
							Message ackMessage = new Message("CheckPointAck",
									MainClass.nodeId, message.path.get(index),
									0, null, 0, MainClass.InitiatorId);
							ackMessage.path = message.path;
							ackMessage.flag = true;
							System.out.println("ACK CP MESSAGE........."
									+ ackMessage.toString()
									+ "..............\n");
							MainClass.sendMessage(
									MainClass.connectionChannelMap.get(index),
									ackMessage);
						}
						// MainClass.receivedAllAcks = true;
					} else {
						// Inform Parent No
						Message ackMessage = new Message("CheckPointAck",
								MainClass.nodeId, message.path.get(message.path
										.size() - 1), 0, null, 0,
								MainClass.InitiatorId);
						ackMessage.path = message.path;
						ackMessage.flag = false;
						System.out.println("ACK CP MESSAGE........."
								+ ackMessage.toString() + "..............\n");

						int index = 0;
						for (int i = 0; i < message.path.size(); i++) {
							if (message.path.get(i) == nodeId) {
								index = i - 1;
							}
						}

						MainClass.sendMessage(
								MainClass.connectionChannelMap.get(index),
								ackMessage);
					}
				} catch (CharacterCodingException e) {
					e.printStackTrace();
				}

			}
		}

		// if the message is a CP permanent
		else if (message.tag.contains("CheckPointPermanent")) {

			StableCPFlag = true;
			writeIntoStable();
			// make the tentative CP permanent. Inform it to coherts
			for (int j : MainClass.connectionChannelMap.keySet()) {
				if ((MainClass.LLR.get(j) > 0) && (message.initiator != j)
						&& (message.senderId != j)) {

					Message permCPMessage = new Message("CheckPointPermanent",
							MainClass.nodeId, j, 0, null, 0,
							MainClass.InitiatorId);
					permCPMessage.path = message.path;
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
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}

			// reset all the variables Mutex nad write to Stable.file

			resetVariables();

		}

		// if the message is a CP discarrd
		else if (message.tag.contains("CheckPointDiscard")) {
			// TODO: delete the tentative checkpoint.
			MainClass.removeTentativeCheckPoint();
			// reset all the flags and variables
			MainClass.cpStatusFlag = false;
			MainClass.applicationMessageMutex = true;
			// Inform Coherts to discard
			MainClass.InitiatorId = -1;
		}

	}

	private static void resetVariables() {
		// TODO Auto-generated method stub
		MainClass.cpStatusFlag = false;
		MainClass.cpAckCount = 0;
		MainClass.cpReqCohortCount = 0;
		MainClass.FLS.clear();
		MainClass.FLSflag = true;
		MainClass.receivedAllAcks = false;
		MainClass.sentMessageBuffer.clear();
		MainClass.receivedMessageBuffer.clear();
		MainClass.applicationMessageMutex = true;
		MainClass.InitiatorId = -1;
		for (int i = 0; i < MainClass.cpACKFlagArray.length; i++) {
			MainClass.cpACKFlagArray[i] = false;
		}

	}

	private static void writeIntoStable() {
		// TODO Auto-generated method stub

		try {
			String fileName = "./SentMessageTentativeBackup" + nodeId + ".txt";
			BufferedReader br = new BufferedReader(new FileReader(fileName));

			File file1 = new File("./SentMessagePermanentBackup" + nodeId
					+ ".txt");
			if (!file1.exists()) {
				file1.createNewFile();
			}

			FileWriter fw1 = new FileWriter(file1.getAbsoluteFile(), true);
			BufferedWriter bw1 = new BufferedWriter(fw1);

			String curLine = null;
			while ((curLine = br.readLine()) != null) {
				bw1.write(curLine + "\n");
			}
			bw1.write("\n---------------------------------------------------------\n");
			br.close();
			bw1.close();

			String fileName1 = "./ReceivedMessageTentativeBackup" + nodeId
					+ ".txt";
			BufferedReader br1 = new BufferedReader(new FileReader(fileName1));

			File file = new File("./ReceivedMessagePermanentBackup" + nodeId
					+ ".txt");
			if (!file.exists()) {
				file.createNewFile();
			}

			FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
			BufferedWriter bw = new BufferedWriter(fw);

			String curLine1 = null;
			while ((curLine1 = br1.readLine()) != null) {
				bw.write(curLine1 + "\n");
			}
			bw.write("---------------------------------------------------------");
			br1.close();
			bw.close();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private synchronized static void sendPermentCheckPoint() {
		// SEND TO ALL THE Coherts which have sent YES TO TENTATIVE
		try {
			for (int j : MainClass.connectionChannelMap.keySet()) {

				// Send the CP request to only those cohorts from whom I
				// received a message since my last CP
				if (MainClass.LLR.get(j) > 0) {

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
			writeIntoStable();
			resetVariables();

		} catch (CharacterCodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// increment the cohort count who received CP

	}

	public synchronized static void processApplicationMessage(Message message) {
		// set the LLR flag for this message
		LLR.put(message.senderId, message.messageId);
		// TODO: do something with the message; may be write to a file
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

		BufferedWriter out = new BufferedWriter(new FileWriter(
				"./SentMessageTentativeBackup" + nodeId + ".txt", false));

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

		File file1 = new File("./ReceivedMessageTentativeBackup" + nodeId
				+ ".txt");
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
}
