package android.os;

public class Message {
    final int id;
    final Object params;

    public Message(int id, Object params) {
        this.id = id;
        this.params = params;
    }
}
