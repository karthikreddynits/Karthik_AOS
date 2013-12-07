import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import sun.tools.jar.Main;

import com.sun.nio.sctp.MessageInfo;
import com.sun.nio.sctp.SctpChannel;

public class ClientConnections implements Runnable {
	boolean flag = true;

	@Override
	public void run() {
		// TODO Auto-generated method stub

		for (int i : MainClass.domainNameListMap.keySet()) {
			String host = MainClass.domainNameListMap.get(i);
			int port = MainClass.portNumerListMap.get(i);

			ByteBuffer byteBuffer;
			byteBuffer = ByteBuffer.allocate(512);

			System.out.println("Sending connection to :" + "Sever port" + port
					+ "server host" + host);

			try {
				SctpChannel ClientSock;
				InetSocketAddress serverAddr = new InetSocketAddress(host, port);

				do {
					ClientSock = SctpChannel.open();
					ClientSock.connect(serverAddr, 0, 0);
					MainClass.connectionChannel.add(ClientSock);
					MainClass.connectionChannelMap.put(i, ClientSock);

				} while (!ClientSock.finishConnect());

				System.out.println("Created Connection Successfully");
				/*
				 * MessageInfo messageInfo = ClientSock.receive(byteBuffer,
				 * null, null); String message =
				 * MainClass.byteToString(byteBuffer);
				 * System.out.println("Received Message from Server:" + i);
				 * System.out.println(message);
				 * 
				 * MainClass.sendMessage(ClientSock,
				 * "connection.........establishbed with client" +
				 * MainClass.nodeId);
				 */

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {
				System.out.println("ALL connections established");
			}

		}

	}

}
