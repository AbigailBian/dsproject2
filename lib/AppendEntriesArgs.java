package lib;

import java.util.List;

public class AppendEntriesArgs {
    public int term;
    public int leaderID;
    public int prevLogIndex;
    public int prevLogTerm;
    public List<LogEntry> entries;
    public int leaderCommitIndex;


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
}