import java.io.IOException;
import java.net.InetSocketAddress;

import com.sun.nio.sctp.SctpChannel;
import com.sun.nio.sctp.SctpServerChannel;

public class ServerConnections implements Runnable {

	@Override
	public void run() {
		// TODO Auto-generated method stub
		SctpServerChannel serverSocket;

		try {
			serverSocket = SctpServerChannel.open();
			InetSocketAddress serverAddr = new InetSocketAddress(
					MainClass.nodedomainName, MainClass.nodeportNumber);
			serverSocket.bind(serverAddr);
			int count = 0; // (MainClass.nodeCount - MainClass.nodeId);

			while (true) {
				System.out.println("Server is ready to accept");
				// Receive a connection from client and accept it
				SctpChannel clientSocket = serverSocket.accept();

				// Add the opened socket to the list
				// MainClass.connectionChannel.add(clientSocket);

				System.out.println("Received Connection from Node :" + (count));
				// Now server will communication to this Client via clientSock

				/*
				 * String Message = "WELCOME MESSAGE FROM SERVER" +
				 * MainClass.nodeId; MainClass.sendMessage(clientSocket,
				 * Message);
				 */

				MessageReceiver messagereceiver = new MessageReceiver(
						clientSocket);
				new Thread(messagereceiver).start();

				count++;

			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
