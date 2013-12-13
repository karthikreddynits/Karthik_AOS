import java.io.*;
import java.util.HashMap;

import javax.xml.parsers.*;
import javax.xml.transform.*;

public class CheckPointInitiaon implements Runnable {

	public CheckPointInitiaon() {

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 * 
	 * Trigger The CheckPoint for every 10th tick of clock Freeze the sending of
	 * messages
	 * 
	 * Phase1: Take a Tentative Check Point Send a Check point request to all
	 * the cohert processes
	 */
	@Override
	public void run() {
		
		HashMap<Integer, Integer> timHashMap = new HashMap<>();
		timHashMap.put(0, 10);
//		timHashMap.put(1, 8);
//		timHashMap.put(2, 12);

		while ((MainClass.sentCheckPointMessage < MainClass.totalCheckPointMessage)) {
			// only node 0 will initiate the CP request
			if (!MainClass.cpStatusFlag && (MainClass.nodeId == 0)) {

				if (((MainClass.sentMessageCount)
						% (timHashMap.get(MainClass.nodeId)) == 0)) {
					System.out.println("CheckPointInitiation.java "
							+ MainClass.sentCheckPointMessage + " --- "
							+ MainClass.totalCheckPointMessage + " --- "
							+ MainClass.getLogicalClock() + "-----"
							+ MainClass.cpStatusFlag);
					MainClass.applicationMessageMutex = false;
					MainClass.cpStatusFlag = true;
					MainClass.incrementLogicalClock();
					try {

						// Taking tentative Check point
						MainClass.tentativeCheckPoint();

						// Sending request to all the cohorts
						MainClass.InitiatorId = MainClass.nodeId;
						System.out.println("LLR VAlues :");
						for (int i : MainClass.LLR.keySet()) {
							System.out.println("\n" + i + "--->"
									+ MainClass.LLR.get(i) + "\n");
						}
						for (int j : MainClass.connectionChannelMap.keySet()) {

							// Send the CP request to only those cohorts from
							// whom I
							// received a message since my last CP
							if (MainClass.LLR.get(j) > 0) {

								Message cpMessage = new Message(
										"CheckPointRequest", MainClass.nodeId,
										j, 0, null, 0, MainClass.InitiatorId);
								cpMessage.path.add(MainClass.nodeId);
								System.out
										.println("Sending CP message ******************************************"
												+ cpMessage.toString()
												+ "\n********************\n");
								MainClass.sendMessage(
										MainClass.connectionChannelMap.get(j),
										cpMessage);
								// increment the cohort count who received CP
								MainClass.cpReqCohortCount++;
								MainClass.cpForwardslist.add(j);
							}

						}
						if (MainClass.cpReqCohortCount == 0) {
							MainClass.applicationMessageMutex = true;
							MainClass.cpStatusFlag = false;
						}

					} catch (ParserConfigurationException e) {
						e.printStackTrace();
					} catch (IOException | TransformerException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

}
