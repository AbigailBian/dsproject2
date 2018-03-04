import lib.*;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class RaftNode implements MessageHandling {
    private int id;
    private static TransportLib lib;
    private int num_peers;

    // Persistent state on all servers
    private int currentTerm;
    private int votedFor;
    private static final int NOT_VOTED = -1;
    private List<LogEntry> entries;

    // Volatile state on all servers
    private int commitIndex;
    private int lastApplied;

    // Volatile state on leaders
    private int[] nextIndices;
    private int[] matchIndices;

    // Randomized the timeout value of each node
    private Random random;

    /* The paper's section 5.2 mentions election timeouts in the range of 150 to 300 milliseconds.
     * Such a range only makes sense if the leader sends heartbeats considerably more often than
     * once per 150 milliseconds. So the min and max timeout values are doubled here.
     */
    private static final int minTimeoutVal = 300;
    private static final int maxTimeoutVal = 600;

    // Interval of sending the heartbeat
    // The tester limits you to 10 heartbeats per second
    private static final int heartbeatInterval = 100;


    // Count the number of votes;
    private int voteCount;

    // Type
    private String type;

    public RaftNode(int port, int id, int num_peers) {
        this.id = id;
        this.num_peers = num_peers;
        lib = new TransportLib(port, id, this);

        this.currentTerm = 0;
        this.votedFor = NOT_VOTED;
        this.entries = new ArrayList<LogEntry>();
        this.commitIndex = 0;
        this.lastApplied = 0;

        this.nextIndices = new int[num_peers];
        this.matchIndices = new int[num_peers];

        Arrays.fill(nextIndices, 1);

        this.voteCount = 0;
        new Thread () {
            public void perform() {
                if ()
            }
        };
    }

    private class AppendEntryThread extends Thread {

        AppendEntryThread() {

        }

        @Override
        public void run() {

        }
    }

    private class RequestVoteThread extends Thread {

        RequestVoteThread() {

        }

        @Override
        public void run() {

        }
    }


    /************** Serialize and Deserialize ****************/

    public Message serialize(MessageType type, int src_addr, int dest_addr, Object object) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream ob = null;
        byte[] byteArray = null;

        try {
            ob = new ObjectOutputStream(bos);
            ob.writeObject(object);
            ob.flush();
            byteArray = bos.toByteArray();

        } catch (IOException e) {
            System.out.println("");
        } finally {
            try {
                ob.close();
            } catch (IOException e1) {
                System.out.println("");
            }
        }

        return new Message(type, src_addr, dest_addr, byteArray);
    }

    public Object deserialize(Message message) {
        byte[] byteArray = message.getBody();

        ByteArrayInputStream bis = new ByteArrayInputStream(byteArray);
        ObjectInput oi = null;
        Object in = null;

        try {
            oi = new ObjectInputStream(bis);
            in = oi.readObject();
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("");
        } finally {
            try {
                oi.close();
            } catch (IOException e1) {
                System.out.println("");
            }
        }

        return in;
    }

    /*
     *call back.
     */
    @Override
    public StartReply start(int command) {
        return null;
    }

    @Override
    public GetStateReply getState() {
        return null;
    }

    @Override
    public Message deliverMessage(Message message) {
        return null;
    }

    //main function
    public static void main(String args[]) throws Exception {
        if (args.length != 3) throw new Exception("Need 3 args: <port> <id> <num_peers>");
        //new usernode
        RaftNode UN = new RaftNode(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]));
    }
}
