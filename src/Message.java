import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Message implements Serializable {
	int senderId;
	int receiverId;
	String tag;
	// int initiator;
	String body;
	int messageId;
	int Messcount;
	int initiator;
	boolean flag;
	List<Integer> path = new ArrayList<>();

	// "[(AppMessage),(SenderID),(ReceiverId),(MonotonicalValue MessID),(Body),(MCount)]"

	Message(String tag, int s, int r, int Mid, String body, int mCount,
			int Initiator) {
		senderId = s;
		receiverId = r;
		this.tag = tag;
		this.body = body;
		messageId = Mid;
		Messcount = mCount;
		initiator = Initiator;
	}

	public void setFlag(boolean f) {
		flag = f;
	}

	public boolean getFlag() {
		return flag;
	}

	@Override
	public String toString() {
		return "Message [senderId=" + senderId + ", receiverId=" + receiverId
				+ ", tag=" + tag + ", body=" + body + ", messageId="
				+ messageId + ", Messcount=" + Messcount + ", initiator="
				+ initiator + ", flag=" + flag + ", path=" + path + "]";
	}

	

}
