import java.io.*;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;

import org.w3c.dom.*;

import com.sun.nio.sctp.SctpChannel;

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

		while (MainClass.sentCheckPointMessage < MainClass.totalCheckPointMessage) {
			if (((MainClass.getLogicalClock()) % (10) == 0)
					&& (MainClass.nodeId == 0)) {
				MainClass.applicationMessageMutex = false;
				MainClass.incrementLogicalClock();
				try {

					// Taking tentative Check point
					MainClass.tentativeCheckPoint();

					// Sending request to all the cohorts
					MainClass.InitiatorId = MainClass.nodeId;
					/*
					 * for (int j = 0; j < MainClass.connectionChannel.size();
					 * j++) {
					 * 
					 * Message cpMessage = new Message("CheckPoint",
					 * MainClass.nodeId, MainClass.cohertList.get(j), 0, null,
					 * 0, MainClass.InitiatorId);
					 * 
					 * //Send the CP request to only those cohorts from whom I
					 * received a message since my last CP
					 * if(MainClass.LLR.get(j) != 0){ MainClass.sendMessage(
					 * MainClass.connectionChannel.get(j), cpMessage);
					 * //increment the cohort count who received CP
					 * MainClass.cpReqCohortCount++; }
					 * 
					 * }
					 */

					for (int j : MainClass.connectionChannelMap.keySet()) {

						// Send the CP request to only those cohorts from whom I
						// received a message since my last CP
						if (MainClass.LLR.get(j) > 0) {

							Message cpMessage = new Message(
									"CheckPointRequest", MainClass.nodeId, j,
									0, null, 0, MainClass.InitiatorId);
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
						}

					}


					// increment CP count
					//FIXME: shouldn't his be incremented only if the CP was succesful?
					//MainClass.sentCheckPointMessage++;

				} catch (ParserConfigurationException e) {
					e.printStackTrace();
				} catch (IOException | TransformerException e) {
					e.printStackTrace();
				}
			}
		}
	}

}
