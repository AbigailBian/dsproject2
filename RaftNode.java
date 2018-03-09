import lib.*;

import java.io.*;
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
    private int electionTimeout;

    /* The paper's section 5.2 mentions election timeouts in the range of 150 to 300 milliseconds.
     * Such a range only makes sense if the leader sends heartbeats considerably more often than
     * once per 150 milliseconds. So the min and max timeout values are doubled here.
     */
    private static final int minTimeoutVal = 300;
    private static final int maxTimeoutVal = 600;

    // Interval of sending the heartbeat
    // The tester limits you to 10 heartbeats per second
    private static final int heartbeatInterval = 100;
    private LinkedBlockingDeque<HeartBeat> heartBeatQueue;

    // private class for mimicking the heartbeat
    private class HeartBeat {
        int heartbeatID;
    }


    // Count the number of votes;
    private int voteCount;

    // State
    private String state;


    private Object lock;

    public RaftNode(int port, int id, int num_peers) {
        this.id = id;
        this.num_peers = num_peers;
        lib = new TransportLib(port, id, this);

        this.currentTerm = 0;
        this.votedFor = NOT_VOTED;
        this.entries = new ArrayList<LogEntry>();
        this.entries.add(new LogEntry(0, 0, null));
        this.commitIndex = 0;
        this.lastApplied = 0;

        this.nextIndices = new int[num_peers];
        this.matchIndices = new int[num_peers];
        Arrays.fill(nextIndices, 1);

        Random random = new Random();
        this.electionTimeout = maxTimeoutVal - random.nextInt() % minTimeoutVal;
        this.heartBeatQueue = new LinkedBlockingDeque<>();

        this.voteCount = 0;
        this.state = "follower";


        this.lock = new Object();

        new Thread(new Runnable() {
            @Override
            public void run() {
                perform();
            }
        }).start();
    }

    private void perform() {
        boolean live = true;
        while (live) {

            boolean isLeader = this.state.equals("leader");
            int timeout = isLeader ? heartbeatInterval : this.electionTimeout;
            HeartBeat heartbeat = null;

            try {
                heartbeat = heartBeatQueue.poll(timeout, TimeUnit.MILLISECONDS);

            } catch (InterruptedException e) {
                live = false;
                System.out.println(e.getMessage());
            }
            // if there's heartbeat from the leader
            if (heartbeat != null) {
                if (this.state.equals("candidate")) {
                    this.state = "follower";
                }
            } else { // if the participant cannot hear the heartbeat
                if (isLeader){ // if the node is the leader, then send out heartbeat AppendEntries RPCs

                    for (int i = 0; i < num_peers; i++) {
                        if (i != id) {

                            // for the checkpoint, we only need to send out an empty or invalid args
                            // for sending heartbeat or electing a new leader.
                            AppendEntriesArgs args = new AppendEntriesArgs(currentTerm, id, -1, -1,
                                                            new ArrayList<>(), -1);
                            AppendEntryThread aThread = new AppendEntryThread(args, i);
                            aThread.start();

                        }
                    }


                } else { // if the current node is a follower or candidate, then start election!
                    // increment the term number and vote for itself
                    // request votes from all other peers
                    state = "candidate";
                    currentTerm++;
                    votedFor = this.id;
                    voteCount++;

                    for (int i = 0; i < num_peers; i++) {
                        if (i != id) {
                            int lastLogTerm = entries.get(entries.size() - 1).getTerm();
                            int lastLogIndex = entries.get(entries.size() - 1).getTerm();
                            RequestVoteArgs args = new RequestVoteArgs(currentTerm, id, lastLogIndex, lastLogTerm);
                            RequestVoteThread rThread = new RequestVoteThread(args, i);
                            rThread.start();
                        }
                    }
                }
            }
        }
    }

    private class RequestVoteThread extends Thread {
        RequestVoteArgs args;
        int destID;
        public RequestVoteThread(RequestVoteArgs args, int destID) {
            this.args = args;
            this.destID = destID;
        }

        @Override
        public synchronized void run() {
            Message msg = serialize(MessageType.RequestVoteArgs, id, destID, args);
            Message reply = sendMessageViaLib(msg);
            if (reply == null) {
                return;
            }
            synchronized (lock) {
                RequestVoteReply replyObject = (RequestVoteReply) deserialize(reply);
                int replyTerm = replyObject.getTerm();
                boolean replyVoteGranted = replyObject.isVoteGranted();

                // if the current term on this node is smaller than the replied term,
                // meaning that there's a more up-to-date node is requesting votes and election.
                // this node should step down immediately and become a follower.
                if (currentTerm < replyTerm || state.equals("follower")) {
                    // step down and set the term to the most up-to-date one
                    state = "follower";
                    currentTerm = replyTerm;
                    votedFor = NOT_VOTED;
                } else if (replyVoteGranted) {
                    voteCount++;
                    if (voteCount > (num_peers / 2)) {
                        state = "leader";
                        for (int i = 0; i < num_peers; i++) {
                            // set the nextIndex of all peers to be the next index of the current leader
                            // in order to do the checking and cleanup during the normal operation
                            nextIndices[i] = entries.get(entries.size() - 1).getIndex() + 1;
                            matchIndices[i] = 0;
                        }
                    }
                    heartBeatQueue.add(new HeartBeat());
                }
            }
        }
    }


    private class AppendEntryThread extends Thread {
        AppendEntriesArgs args;
        int destID;

        public AppendEntryThread(AppendEntriesArgs args, int destID) {
            this.args = args;
            this.destID = destID;
        }

        @Override
        public synchronized void run() {
            if (!state.equals("leader") || currentTerm != args.getTerm()) {
                return;
            }

            Message msg = serialize(MessageType.AppendEntriesArgs, id, destID, args);
            Message reply = sendMessageViaLib(msg);
            if (reply == null) {
                return;
            }

            synchronized (lock) {
                AppendEntriesReply replyObject = (AppendEntriesReply) deserialize(reply);
                int term = replyObject.getTerm();
                boolean success = replyObject.isSuccess();
                int nextIndex = replyObject.getNextIndex();

                // if the term of the sender (leader) has smaller (older) term than the receiver, the leader steps
                // down to be a follower and set the term to the current max term it learned from the "new leader"
                if (currentTerm < term) {
                    state = "follower";
                    currentTerm = term;
                    votedFor = NOT_VOTED;
                }
            }
        }
    }


    private Message sendMessageViaLib(Message msg) {
        Message reply = null;
        try {
            reply = lib.sendMessage(msg);
            if (reply == null) {
                return null;
            }
        } catch (RemoteException e) {
            return null;
        }
        return reply;
    }


    /****************************** RPC calls of Sending Messages ************************/

    // execute corresponding action when a candidate asking for a vote
    private synchronized RequestVoteReply requestVote(RequestVoteArgs args) {

        int requestTerm = args.getTerm();
        int requestLastLogTerm = args.getLastLogTerm();
        int requestLastLogIndex = args.getLastLogIndex();
        int requestCandidateID = args.getCandidateId();

        // if the node's term is larger than the candidate, then it won't grant its vote.
        // so set the default voteGranted to false;
        boolean voteGranted = false;
//
//        if (currentTerm > requestTerm) {
//            voteGranted = false;
//        }

        // if the candidate's term is larger than the voter, set the voter as Follower
        // and set the voted_for as -1 because we still don't know if there's an most up-to-date log in the candidate or not
        // need to decide if it is going to give out the vote based on the comparison between logs of candidate and voter
        if (currentTerm <= requestTerm) {
            if (currentTerm < requestTerm) {
                currentTerm = requestTerm;
                state = "follower";
                votedFor = NOT_VOTED;
            }
            LogEntry lastEntry = entries.get(entries.size() - 1);
            int lastLogTerm = lastEntry.getTerm();
            int lastLogIndex = lastEntry.getIndex();
            // Raft User Study p. 17 -> voting server will denies vote if its log is "more complete
            // otherwise if the node hasn't voted yet, it'll grant the vote to the requesting candidate
            // otherwise, if it hasn't voted yet, grant the vote to the candidate who is requesting.
            if ((votedFor == NOT_VOTED) &&
                    (requestLastLogTerm > lastLogTerm ||
                            (requestLastLogTerm == lastLogTerm && requestLastLogIndex >= lastLogIndex))) {
                votedFor = requestCandidateID;
                heartBeatQueue.add(new HeartBeat());
                voteGranted = true;

            }
        }
        return new RequestVoteReply(requestTerm, voteGranted);
    }

    // execute corresponding action when the leader wants to append things to the node
    private synchronized AppendEntriesReply appendEntries(AppendEntriesArgs args) {
        int requestTerm = args.getTerm();

        boolean success = false;
        int newestTerm = -1;
        // if the node has newer term than the requesting leader, then reply no to the appendEntries RPC
        if (currentTerm > requestTerm) {
            success = false;
            newestTerm = currentTerm;
        } else if (currentTerm < requestTerm) {
            success = true;
            currentTerm = requestTerm;
            state = "follower";
            votedFor = NOT_VOTED;
            newestTerm = requestTerm;
        }
        heartBeatQueue.add(new HeartBeat());
        return new AppendEntriesReply(newestTerm, success, -1);

    }

    /*****************************Implementation of MessageHandling **********************/
    @Override
    public StartReply start(int command) {
        return null;
    }


    @Override
    public synchronized GetStateReply getState() {
            boolean isLeader = this.state.equals("leader");
            int term = this.currentTerm;
            return new GetStateReply(term, isLeader);
    }

    @Override
    public Message deliverMessage(Message message) {
        Message replyMsg = null;
        MessageType msgType = message.getType();
        int srcID = message.getSrc();
        int destID = message.getDest();

        MessageType replyType = msgType == MessageType.RequestVoteArgs ?
                MessageType.RequestVoteReply : MessageType.AppendEntriesReply;

        byte[] reply_stream = null;
        Serializable reply = null;
        Object args = deserialize(message);

        if (msgType == MessageType.RequestVoteArgs) {
            reply = requestVote((RequestVoteArgs)args);

        } else if (msgType == MessageType.AppendEntriesArgs) {
            reply = appendEntries((AppendEntriesArgs)args);
        }
        replyMsg = serialize(replyType, destID, srcID, reply);
        return replyMsg;
    }

    /******************************* Serialize and Deserialize *************************************/

    private Message serialize(MessageType type, int srcID, int destID, Object object) {
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

        return new Message(type, srcID, destID, byteArray);
    }

    private Object deserialize(Message message) {
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


    //main function
    public static void main(String args[]) throws Exception {
        if (args.length != 3) throw new Exception("Need 3 args: <port> <id> <num_peers>");
        //new usernode
        RaftNode UN = new RaftNode(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]));
    }
}
