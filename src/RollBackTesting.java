import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class RollBackTesting {
	static int nodeId;
	static ArrayList<Integer> MessageId = new ArrayList<>();

	public static void main(String[] args) {
		nodeId = Integer.parseInt(args[0]);

		PrintResult();
	}

	private static void PrintResult() {
		try {

			String fileName = "./" + nodeId + "RollBackTracefile.txt";
			BufferedReader br = new BufferedReader(new FileReader(fileName));
			int rollbackInstance = 1;
			String readLine = null;
			System.out.println(" **** ROLLBACK INSTANCE **** "
					+ rollbackInstance);
			System.out.println("Sent messages");
			rollbackInstance++;
			while ((readLine = br.readLine()) != null) {
				/**
				 * if line stasrts with == "Sent message from" if line starts
				 * with --- "received message from else if sender = nodeId,
				 * print if receiver = nodeId print
				 */

				if (readLine.startsWith("===")) {
					System.out.println(" **** ROLLBACK INSTANCE **** "
							+ rollbackInstance);
					System.out.println("Sent messages");
					rollbackInstance++;
					continue;
				}
				if (readLine.startsWith("---")) {
					System.out.println("Received messages");
					continue;
				}

				if (readLine.startsWith("Message")) {
					String[] token = readLine.split(",");
					// System.out.println("token size " + token.length);
					System.out.println(token[0] + " -- " + token[1] + " -- "
							+ token[3] + " -- " + token[4]);

				}

			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
