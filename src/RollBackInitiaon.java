import java.io.IOException;
import java.util.HashMap;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

public class RollBackInitiaon implements Runnable {

	@Override
	public void run() {
		// TODO Auto-generated method stub
System.out.println("In rollback thread");
		HashMap<Integer, Integer> timHashMap = new HashMap<>();
		timHashMap.put(0, 13);
		/*
		 * Initiating ROllback 0 ->1 ,2 2-> 3,4
		 * 
		 * 0 sends rollreq to 1 and 2, 1,2 decides on that and sets their local
		 * flag. 2 sends to 3 , 4 once 3,4 replies it send ok to 0 and even 1
		 * has already sent ok to 0 now 0 sends rollback confirm to its coherts
		 */

		while ((MainClass.sentRollBackMessage < MainClass.totalRollBackMessage)) {

			if (!MainClass.rollBackFlag && (MainClass.nodeId == 0)
					&& (MainClass.StableCPFlag)) {
				//System.out.println("Inside ROll Back first case");
				
				if (((MainClass.getLogicalClock())
						% 13 == 0)) {

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
						if (MainClass.cpReqCohortCount == 0) {
							MainClass.applicationMessageMutex = true;
							MainClass.cpStatusFlag = false;
						}

					} catch (Exception e) {

					}
				}
			}
		}
	}
}
