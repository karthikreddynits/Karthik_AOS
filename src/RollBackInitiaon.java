import java.io.IOException;
import java.util.HashMap;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

public class RollBackInitiaon implements Runnable {

	@Override
	public void run() {
		System.out.println("In rollback thread");
		HashMap<Integer, Integer> timHashMap = new HashMap<>();
		timHashMap.put(0, 45);
		while ((MainClass.sentRollBackMessage < MainClass.totalRollBackMessage)) {
			// only node 0 will initiate the roll back request
			if (!MainClass.rollBackFlag && (MainClass.nodeId == 0)
					&& (MainClass.StableCPFlag)) {

				if (((MainClass.sentMessageCount)
						% timHashMap.get(MainClass.nodeId) == 0)) {
					timHashMap.put(MainClass.nodeId,
							timHashMap.get(MainClass.nodeId) + 40);
					MainClass.applicationMessageMutex = false;
					MainClass.cpStatusFlag = true;
					MainClass.rollBackFlag = true;
					// set the rollback decision flag
					MainClass.rollBackDecession = true;
					// MainClass.incrementLogicalClock();

					System.out.println("RollBack.java "
							+ MainClass.sentRollBackMessage + " --- "
							+ MainClass.totalRollBackMessage + " --- "
							+ MainClass.getLogicalClock() + "-----"
							+ MainClass.rollBackFlag);

					try {

						// Sending request to all the cohorts
						MainClass.InitiatorId = MainClass.nodeId;
						System.out.println("LLs VAlues :");
						for (int i : MainClass.LLS.keySet()) {
							System.out.println("\n" + i + "--->"
									+ MainClass.LLS.get(i) + "\n");
						}

						for (int j : MainClass.connectionChannelMap.keySet()) {

							// Send the CP request to only those cohorts from
							// whom I
							// received a message since my last CP

							Message rollBackMessage = new Message(
									"RollBackRequest", MainClass.nodeId, j, 0,
									null, 0, MainClass.InitiatorId);
							rollBackMessage.path.add(MainClass.nodeId);
							rollBackMessage.LLS = MainClass.LLS.get(j);

							// cpMessage.path.add(MainClass.nodeId);
							System.out
									.println("Sending RollBack request message ******************************************"
											+ rollBackMessage.toString()
											+ "\n********************\n");
							MainClass.sendMessage(
									MainClass.connectionChannelMap.get(j),
									rollBackMessage);
						}

					} catch (Exception e) {

					}
				}
			}
		}
	}
}
