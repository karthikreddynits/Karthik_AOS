import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;

import com.sun.nio.sctp.MessageInfo;
import com.sun.nio.sctp.SctpChannel;

public class MessageReceiver implements Runnable {
	SctpChannel cliSctpChannel;

	public MessageReceiver(SctpChannel channel) {
		cliSctpChannel = channel;
	}

	@Override
	public void run() {
		while (true) {

			ByteBuffer byteBuffer = null;
			Message msg = null;
			MessageInfo messageInfo = null;

			byteBuffer = ByteBuffer.allocate(512);
			byteBuffer.clear();

			try {
				messageInfo = cliSctpChannel.receive(byteBuffer, null, null);
				ByteArrayInputStream bais = new ByteArrayInputStream(
						byteBuffer.array());
				ObjectInputStream ois;
				ois = new ObjectInputStream(bais);
				try {
					msg = (Message) ois.readObject();
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				}
				if (messageInfo != null) {
					synchronized (this) {
						
						

						// if the message is a CP request
						if (msg.tag.contains("CheckPoint")) {
//							System.out.println("Receivd CP .....Message:"+ msg.toString() +"\n");
							MainClass.processCheckpointRequest(msg);
						}

						// if the message is an App message
						else if (msg.tag.contains("AppMessage")) {
							System.out.println("Receivd APP Message:"+ msg.toString() +"\n timestamp "+ MainClass.logicalClock);
							// increment the logical clock
							MainClass.incrementLogicalClock();
							// process the applciation message
							MainClass.receivedMessageBuffer.add(msg);
							MainClass.processApplicationMessage(msg);
							
						}

					}

				}

			} catch (CharacterCodingException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}

			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

	}

	/*
	 * private static String byteToString(ByteBuffer byteBuffer) {
	 * byteBuffer.position(0); byteBuffer.limit(512); byte[] bufArr = new
	 * byte[byteBuffer.remaining()]; byteBuffer.get(bufArr); return new
	 * String(bufArr); }
	 */

}
