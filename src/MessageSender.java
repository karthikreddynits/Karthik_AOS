import java.nio.charset.CharacterCodingException;

public class MessageSender implements Runnable {

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run() Sending Messages to the Cohert
	 * Applications...
	 * 
	 * Message Format:
	 * "[(AppMessage),(SenderID),(ReceiverId),(MonotonicalValue MessID),(Body),(MCount)]"
	 */

	@Override
	public void run() {

		try {
			while ((MainClass.sentMessageCount <= MainClass.totalMessageCount)
					&& (MainClass.applicationMessageMutex == true)) {
				// increment the logical clock before sending a message
				MainClass.incrementLogicalClock();
				// send the message to all the neighbors
				for (int j = 0; j < MainClass.connectionChannel.size(); j++) {
					String str = "Hey this is test message";
					Message appMsg = new Message("AppMessage",
							MainClass.nodeId, MainClass.cohertList.get(j),
							MainClass.messageId, str,
							MainClass.sentMessageCount, 0);
					// send the message
					System.out.println("SENT MESSAGE :" + appMsg.toString()
							+ "\n");
					MainClass.sendMessage(MainClass.connectionChannel.get(j),
							appMsg);
					// add the message to the message buffer
					MainClass.sentMessageBuffer.add(appMsg);
					// increment the messageId. messageId must be monotonically
					// increasing
					MainClass.messageId++;
					Thread.sleep(2000);
				}

				// To store the FLS since the last check point
				// Everytime a new checkpoint is taken, this flag becomes true
				if (MainClass.FLSflag == true) {
					for (int i = 0; i < MainClass.sentMessageBuffer.size(); i++) {
						// stores 'cohort - message label' in FLS arraylist
						MainClass.FLS.put(
								MainClass.sentMessageBuffer.get(i).receiverId,
								MainClass.sentMessageBuffer.get(i).messageId);
					}
					// set the flag false. this flag becomes true whenever a new
					// CP is taken
					MainClass.FLSflag = false;

				}
				// increment the sent message count
				MainClass.sentMessageCount++;
			}
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

		} catch (CharacterCodingException | InterruptedException e) {
			e.printStackTrace();
		}

	}
}
