import lib.*;

import java.io.*;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

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
    private int timeOut;

    /* The paper's section 5.2 mentions election timeouts in the range of 150 to 300 milliseconds.
     * Such a range only makes sense if the leader sends heartbeats considerably more often than
     * once per 150 milliseconds. So the min and max timeout values are doubled here.
     */
    private static final int minTimeoutVal = 300;
    private static final int maxTimeoutVal = 600;

    // Interval of sending the heartbeat
    // The tester limits you to 10 heartbeats per second
    private static final int heartbeatInterval = 100;
    private LinkedBlockingDeque<Boolean> heartBeatQueue;


    // Count the number of votes;
    private int voteCount;

    // State
    private String state;

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

        Random random = new Random();
        this.timeOut = maxTimeoutVal - random.nextInt() % minTimeoutVal;
        this.heartBeatQueue = new LinkedBlockingDeque<>();

        this.voteCount = 0;
        this.state = "follower";

        new Thread(new Runnable() {
            @Override
            public void run() {
                perform();
            }
        }).start();
    }

    public void perform() {
        if (this.state.equals("follower") || this.state.equals("candidate")) {
            try {
                Boolean ans = heartBeatQueue.poll(this.timeOut, TimeUnit.MILLISECONDS);
                if (ans == null) {
                    // If timeout, change its state to candidate and starts election.
                    this.state = "candidate";
                    this.currentTerm++;
                    this.voteCount++;
                    this.votedFor = this.id;

                    for (int i = 0; i < num_peers; i++) {
                        if (i == this.id) {
                            continue;
                        }
                        RequestVoteArgs requestArgs = new RequestVoteArgs(this.currentTerm,
                                                                          this.id,
                                                                          this.entries.get(entries.size() - 1).getIndex(),
                                                                          this.entries.get(entries.size() - 1).getTerm());
                        RequestVoteThread requestThread = new RequestVoteThread(this.id, i, requestArgs);
                        requestThread.run();
                    }
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        } else {
            leaderBroadCast();
        }
    }

    private class AppendEntryThread extends Thread {
        private AppendEntriesArgs appendArgs;
        private int destID;

        AppendEntryThread(int destID, AppendEntriesArgs args) {
            this.appendArgs = args;
            this.destID = destID;
        }

        @Override
        public void run() {
            Message request = serialize(MessageType.AppendEntriesArgs, id, this.destID, this.appendArgs);
            Message reply;

            try {
                reply = lib.sendMessage(request);
                // failure
                if (reply == null) {
                    return;
                }
                AppendEntriesReply appendReply = (AppendEntriesReply) deserialize(reply);


            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private class RequestVoteThread extends Thread {
        private int srcID;
        private int destID;
        private RequestVoteArgs requestArgs;
        RequestVoteThread(int srcID, int destID, RequestVoteArgs args) {
            this.srcID = srcID;
            this.destID = destID;
            this.requestArgs = args;
        }

        @Override
        public void run() {
            // pack up the Message and send those msgs to the peers using TransportLib
            // then unpack and take actions
            Message request = serialize(MessageType.RequestVoteArgs, this.srcID, this.destID, requestArgs);
            Message reply;
            try {
                reply = lib.sendMessage(request);
                // failure
                if (reply == null) {
                    return;
                }

                RequestVoteReply deserializedReply = (RequestVoteReply) deserialize(reply);
                boolean voteGranted = deserializedReply.voteGranted;
                int replierTerm = deserializedReply.term;


                if (replierTerm < currentTerm) {
                    return;
                } else if (replierTerm > currentTerm) { // replier is more up-to-date, step down
                    state = "follower";
                    voteCount = 0;
                    currentTerm = replierTerm;
                    votedFor = -1;
                } else {
                    if (voteGranted) {
                        voteCount++;
                        if (voteCount > num_peers / 2) {
                            state = "leader";
                            heartBeatQueue.offer(true);
                            for (int i = 0; i < num_peers; i++) {
                                nextIndices[i] = entries.get(entries.size() - 1).getIndex() + 1;
                                matchIndices[i] = 0;
                            }
                            heartBeatQueue.offer(true);
                        }
                    }
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private void leaderBroadCast() {
        for (int i = 0; i < num_peers; i++) {
            if (i == id) {
                continue;
            }


            int prevLogIndex = this.nextIndices[i] - 1;
            int prevLogTerm = entries.get(prevLogIndex).getTerm();

            List<LogEntry> toBeAppendEntries = entries.subList(prevLogIndex + 1, entries.size());
            AppendEntriesArgs appendArgs = new AppendEntriesArgs(this.currentTerm, this.id, prevLogIndex, prevLogTerm,
                                                                 toBeAppendEntries, commitIndex);
            AppendEntryThread appendThread = new AppendEntryThread(i, appendArgs);
            appendThread.start();
        }
    }


    /******************************* Serialize and Deserialize *************************************/

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

    /***********************************************************************************/

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
