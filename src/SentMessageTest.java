import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class SentMessageTest {
	static int nodeId;
	static ArrayList<Integer> MessageId = new ArrayList<>();

	public static void main(String[] args) {
		nodeId = Integer.parseInt(args[0]);
		performTest();
		validateResult();
	}

	private static void validateResult() {
		boolean flag = false;
		System.out.println("Message ID's of Sent Messages ==>");
		for (int i = 0; i < MessageId.size() - 1; i++) {
			System.out.println(MessageId.get(i));
			if ((MessageId.get(i + 1) - MessageId.get(i)) != 1) {
				System.out.println("Didn't recover correctly !!!!");
				flag = true;
				break;
			}
		}
		if (!flag) {
			System.out.println("Recovered perfectly");
		}
	}

	private static void performTest() {
		try {

			String fileName = "./" + nodeId + "SentMessagePermanentBackup.txt";
			BufferedReader br = new BufferedReader(new FileReader(fileName));

			String readLine = null;

			while ((readLine = br.readLine()) != null) {
				if (readLine.startsWith("Message")) {
					String[] token1 = readLine.split(",");

					String[] token2 = token1[4].split("=");
					MessageId.add(Integer.parseInt(token2[1]));
				}
			}

		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
