package lib;

import java.io.Serializable;
import java.util.List;

public class AppendEntriesArgs implements Serializable{
    private int term;
    private int leaderID;
    private int prevLogIndex;
    private int prevLogTerm;
    private List<LogEntry> entries;
    private int leaderCommitIndex;


    // there might be multiple entries that will be asked to commit at the same time
    // in order to follow the new commitment rules describe in Raft User Study p. 20
    // 1. Must be stored on a majority of servers
    //
    // 2. At least one new entry from leader’s term must also be
    //    stored on majority of servers

    public AppendEntriesArgs(int term, int leaderID, int prevLogIndex, int prevLogTerm, List<LogEntry> entries, int leaderCommitIndex) {
        this.term = term;
        this.leaderID = leaderID;
        this.prevLogIndex = prevLogIndex;
        this.prevLogTerm = prevLogTerm;
        this.entries = entries;
        this.leaderCommitIndex = leaderCommitIndex;
    }

    public int getTerm() {
        return term;
    }

    public void setTerm(int term) {
        this.term = term;
    }

    public int getLeaderID() {
        return leaderID;
    }

    public void setLeaderID(int leaderID) {
        this.leaderID = leaderID;
    }

    public int getPrevLogIndex() {
        return prevLogIndex;
    }

    public void setPrevLogIndex(int prevLogIndex) {
        this.prevLogIndex = prevLogIndex;
    }

    public int getPrevLogTerm() {
        return prevLogTerm;
    }

    public void setPrevLogTerm(int prevLogTerm) {
        this.prevLogTerm = prevLogTerm;
    }

    public List<LogEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<LogEntry> entries) {
        this.entries = entries;
    }

    public int getLeaderCommitIndex() {
        return leaderCommitIndex;
    }

    public void setLeaderCommitIndex(int leaderCommitIndex) {
        this.leaderCommitIndex = leaderCommitIndex;
    }
}